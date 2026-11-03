package com.gongpu.stats;

import com.gongpu.common.Num;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 排行榜服务（Redis ZSET）。
 * <p>按生命上限排序。<b>排序权重用 log10(maxHP)</b>：BigDecimal 可达任意量级，
 * double 权重在 1e308 后变 Infinity 会让整榜报废，而 log10 对任何正数都有限且保序。
 * 精确展示值以字符串形式编码在 member 里（uid|名字|世代|maxHP 明文）。
 * <p>同用户取历史最好成绩（HASH 记录当前最佳，避免全表扫描找旧条目）。
 * Redis 不可用时静默降级——榜单是锦上添花，绝不能拖垮游戏主流程。</p>
 */
@Service
public class LeaderboardService {

    private static final String HASH_MEMBER = "member";
    private static final String HASH_LOG = "log";

    /** 榜单类型：生命上限 / 金币 / 世代。 */
    public static final String BOARD_HP = "maxhp";
    public static final String BOARD_GOLD = "gold";
    public static final String BOARD_GEN = "gen";

    private final StringRedisTemplate redis;
    private final String hpKey;
    private final String goldKey;
    private final String genKey;

    public LeaderboardService(StringRedisTemplate redis,
                              @Value("${gongpu.game.leaderboard-key}") String key) {
        this.redis = redis;
        this.hpKey = key;
        this.goldKey = key.replace("maxhp", "gold");
        this.genKey = key.replace("maxhp", "gen");
    }

    private String keyOf(String board) {
        return switch (board) {
            case BOARD_GOLD -> goldKey;
            case BOARD_GEN -> genKey;
            default -> hpKey;
        };
    }

    /**
     * 统一上报三个榜单(生命上限/金币/世代),同用户各榜取历史最好成绩。
     * 共享 60 秒节流,避免 tick 风暴。任何失败静默——榜单不影响游戏。
     */
    public void submit(long userId, String username, BigDecimal maxHP, int gen, BigDecimal gold) {
        if (userId <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = throttle.get(userId);
        if (last != null && now - last < 60_000L) {
            return;
        }
        throttle.put(userId, now);
        // 节流表只增不删会随历史玩家数单调膨胀:超阈值时顺手清掉已过窗口的条目
        if (throttle.size() > 4096) {
            throttle.entrySet().removeIf(e -> now - e.getValue() >= 60_000L);
        }
        if (maxHP != null && maxHP.signum() > 0) {
            submitBoard(hpKey, userId, username, gen, Num.plain(maxHP), log10Safe(maxHP), false);
        }
        if (gold != null && gold.signum() > 0) {
            submitBoard(goldKey, userId, username, gen, Num.plain(gold), log10Safe(gold), false);
        }
        if (gen > 0) {
            submitBoard(genKey, userId, username, gen, String.valueOf(gen), gen, true);
        }
    }

    /**
     * 单榜写入的原子脚本:「读旧分→比大小→换 member→写新分」必须在 Redis 内一次做完,
     * 否则并发上报会互相覆盖(旧实现是四步往返,残留旧 member 即出自于此)。
     * KEYS[1]=榜 ZSET,KEYS[2]=best HASH;ARGV=分数字符串,log 字段,member 字段,新 member。
     * 返回 1=已写入,0=旧分更高跳过。
     */
    private static final org.springframework.data.redis.core.script.RedisScript<Long> SUBMIT_SCRIPT =
            org.springframework.data.redis.core.script.RedisScript.of("""
                    local old = redis.call('HGET', KEYS[2], ARGV[2])
                    if old and tonumber(old) >= tonumber(ARGV[1]) then
                      return 0
                    end
                    local oldm = redis.call('HGET', KEYS[2], ARGV[3])
                    if oldm and oldm ~= ARGV[4] then
                      redis.call('ZREM', KEYS[1], oldm)
                    end
                    redis.call('ZADD', KEYS[1], ARGV[1], ARGV[4])
                    redis.call('HSET', KEYS[2], ARGV[2], ARGV[1], ARGV[3], ARGV[4])
                    return 1
                    """, Long.class);

    /** 单榜写入:member=uid|名|世代|值明文,分数为 log10(防 1e308 溢出)或世代整数。 */
    private void submitBoard(String boardKey, long userId, String username, int gen,
                             String valuePlain, double score, boolean genBoard) {
        try {
            String uidStr = String.valueOf(userId);
            String hashKey = boardKey + ":best";
            // gen 榜 member 无第 4 段(值=世代);其余榜带值明文供 top() 精确展示
            String member = genBoard
                    ? userId + "|" + (username == null ? "?" : username)
                    : userId + "|" + (username == null ? "?" : username) + "|" + gen + "|" + valuePlain;
            redis.execute(SUBMIT_SCRIPT,
                    java.util.List.of(boardKey, hashKey),
                    String.valueOf(score), HASH_LOG + ":" + uidStr,
                    HASH_MEMBER + ":" + uidStr, member);
        } catch (RuntimeException ignored) {
            // 榜单失败不影响游戏
        }
    }

    private final Map<Long, Long> throttle = new java.util.concurrent.ConcurrentHashMap<>();

    /** log10(v),v>0。BigDecimal 无 log 方法,手写:v = d.ddd×10^s (d∈[1,10)) → s + log10(d.ddd),全程有限值。 */
    private static double log10Safe(BigDecimal v) {
        int s = v.precision() - v.scale() - 1;
        double mantissa = v.movePointLeft(s).doubleValue();
        return s + Math.log10(mantissa);
    }

    /** 榜单查询:board=maxhp(默认)/gold/gen。行结构随榜单不同(maxHP/maxHPText 或 gold/goldText 或 gen)。 */
    public List<Map<String, Object>> top(String board, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            Set<ZSetOperations.TypedTuple<String>> set =
                    redis.opsForZSet().reverseRangeWithScores(keyOf(board), 0, limit - 1);
            int rank = 1;
            if (set != null) {
                Set<String> seenUser = new java.util.HashSet<>();
                for (ZSetOperations.TypedTuple<String> t : set) {
                    String v = t.getValue();
                    if (v == null) {
                        continue;
                    }
                    String[] parts = v.split("\\|");
                    String uid = parts.length > 0 ? parts[0] : "";
                    // 同一玩家只保留最好的一条
                    if (!seenUser.add(uid)) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("rank", rank++);
                    row.put("username", parts.length > 1 ? parts[1] : "?");
                    double score = t.getScore() == null ? 0 : t.getScore();
                    switch (board == null ? BOARD_HP : board) {
                        case BOARD_GEN -> row.put("gen", (int) score);
                        case BOARD_GOLD -> {
                            BigDecimal gold = valueOfMember(parts, score);
                            row.put("gold", Num.plain(gold));
                            row.put("goldText", Num.cn(gold));
                        }
                        default -> {
                            // gen 段:新版 4 段在第 3 段;旧版 3 段条目无 gen,回退 0
                            row.put("gen", parts.length > 2 ? parts[2] : "0");
                            BigDecimal hp = valueOfMember(parts, score);
                            row.put("maxHP", Num.plain(hp));
                            row.put("maxHPText", Num.cn(hp));
                        }
                    }
                    rows.add(row);
                }
            }
        } catch (RuntimeException ignored) {
            // 返回空榜
        }
        return rows;
    }

    /** member 第 4 段是值明文;旧版 3 段条目(无明文)用 10^score 估算展示。 */
    private static BigDecimal valueOfMember(String[] parts, double score) {
        return parts.length > 3 && !parts[3].isBlank()
                ? new BigDecimal(parts[3])
                : BigDecimal.TEN.pow((int) Math.min(1000, score));
    }
}
