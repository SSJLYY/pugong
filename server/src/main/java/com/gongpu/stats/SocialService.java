package com.gongpu.stats;

import com.gongpu.account.User;
import com.gongpu.account.UserRepository;
import com.gongpu.common.ApiException;
import com.gongpu.common.Num;
import com.gongpu.game.PanelCalculator;
import com.gongpu.state.GameSaveRepository;
import com.gongpu.state.Grave;
import com.gongpu.state.GraveRepository;
import com.gongpu.state.PlayerState;
import com.gongpu.state.StateCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 网游社交玩法：香火祭奠 / 切磋 PvP / 世界墓园。
 * <p>设计原则：交互全部围绕「墓碑」与「面板快照」，不直接读取对方在线状态，
 * 天然异步化，无需长连接。</p>
 */
@Service
public class SocialService {

    /** 香火价格：祭奠一次花 200 金币，墓主得 1 炷香火。 */
    public static final int TRIBUTE_COST = 200;
    /** 香火复活：1 炷香火原地满血复活。 */
    public static final int REVIVE_INCENSE_COST = 1;
    /** 切磋冷却（秒）：防止刷战绩。 */
    public static final long DUEL_COOLDOWN_SEC = 300;
    /** 世界墓园拉取条数。 */
    public static final int WORLD_GRAVE_LIMIT = 50;

    private final UserRepository users;
    private final GraveRepository graves;
    private final GameSaveRepository saves;
    private final com.gongpu.state.SaveService saveService;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;

    /** 世界公告 Redis key（史诗掉落播报）。 */
    public static final String BULLETIN_KEY = "gongpu:bulletin";
    private static final int BULLETIN_MAX = 20;

    public SocialService(UserRepository users, GraveRepository graves,
                         GameSaveRepository saves, com.gongpu.state.SaveService saveService,
                         org.springframework.data.redis.core.StringRedisTemplate redis) {
        this.users = users;
        this.graves = graves;
        this.saves = saves;
        this.saveService = saveService;
        this.redis = redis;
        HOLDER = this;
    }

    // ==================== 世界墓园 ====================

    /** 全服墓园（按祭奠热度 + 时间排序）。viewerId 用于标记"自己的碑"（不可祭奠自己）。 */
    public Map<String, Object> worldGraveyard(Long viewerId) {
        // SQL 端取前 50,再批量查一次用户名(避免全表加载 + 每碑一次 N+1)
        List<Grave> top = graves.findTop50ByOrderByTributesDescCreatedAtDesc();
        Map<Long, String> names = new HashMap<>();
        for (Grave g : top) {
            if (!names.containsKey(g.getUserId())) {
                names.put(g.getUserId(), "无名氏");
            }
        }
        for (User u : users.findAllById(names.keySet())) {
            names.put(u.getId(), u.getUsername());
        }
        // 圆满金碑：飞升金碑集齐 12 个不同世代的玩家，其金碑置顶并标注圆满
        java.util.Set<Long> perfect = perfectUsers(graves.findTop500ByKindOrderByCreatedAtDesc("ascend"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Grave g : top) {
            Map<String, Object> view = graveView(g, names.getOrDefault(g.getUserId(), "无名氏"));
            view.put("own", viewerId != null && viewerId.equals(g.getUserId()));
            if ("ascend".equals(g.getKind()) && perfect.contains(g.getUserId())) {
                view.put("perfect", true);
            }
            rows.add(view);
        }
        // 置顶圆满金碑（稳定排序：组内仍按祭奠热度原序）
        rows.sort((a, b) -> Boolean.compare(Boolean.TRUE.equals(b.get("perfect")),
                Boolean.TRUE.equals(a.get("perfect"))));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("rows", rows);
        return out;
    }

    /** 圆满金碑门槛：飞升世代种类数（集齐 12 世）。 */
    public static final int PERFECT_GENS = 12;

    /** 从金碑列表聚合「圆满」玩家集合：kind=ascend 且不同 gen 数 ≥ 12。包内可见供单测。 */
    static java.util.Set<Long> perfectUsers(List<Grave> ascendGraves) {
        Map<Long, java.util.Set<Integer>> gensByUser = new HashMap<>();
        for (Grave g : ascendGraves) {
            if (!"ascend".equals(g.getKind()) || g.getGen() == null) {
                continue;
            }
            gensByUser.computeIfAbsent(g.getUserId(), k -> new java.util.HashSet<>()).add(g.getGen());
        }
        java.util.Set<Long> out = new java.util.HashSet<>();
        gensByUser.forEach((uid, gens) -> {
            if (gens.size() >= PERFECT_GENS) {
                out.add(uid);
            }
        });
        return out;
    }

    private Map<String, Object> graveView(Grave g, String owner) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("owner", owner);
        m.put("kind", g.getKind());
        m.put("gen", g.getGen());
        m.put("killer", g.getKiller());
        m.put("finalHpText", Num.cn(Num.parse(g.getFinalHp(), Num.ZERO)));
        m.put("livedSec", g.getLivedSec());
        m.put("epitaph", g.getEpitaph());
        m.put("tributes", g.getTributes() == null ? 0 : g.getTributes());
        m.put("createdAt", g.getCreatedAt());
        return m;
    }

    // ==================== 香火祭奠 ====================

    /**
     * 祭奠别人的墓碑：花金币，墓主得香火（复活货币）。
     * <p>不能祭奠自己（防止自己给自己攒复活资源刷死亡成本归零）。</p>
     */
    @Transactional
    public Map<String, Object> tribute(long actorId, long graveId, int slot) {
        if (slot < 1 || slot > 3) {
            throw ApiException.badRequest("槽位不合法");
        }
        Grave g = graves.findById(graveId)
                .orElseThrow(() -> ApiException.notFound("墓碑不存在"));
        if (g.getUserId() == actorId) {
            throw ApiException.badRequest("不能祭奠自己的墓碑");
        }
        // 墓主先行解析：账号已注销时在扣费/占免费名额之前失败，不吞玩家的每日首祭
        User owner = users.findById(g.getUserId())
                .orElseThrow(() -> ApiException.notFound("墓主账号已注销"));
        // 从行动者存档扣金币：统一走 SaveService.load+persist（缓存优先读、
        // 成就评估与冗余列维护与主流程同口径；旧实现直读 DB 会拿到节流落库前的旧金币）
        PlayerState actor = saveService.load(actorId, slot);
        // 每日首祭免费（第七批）：每档每天第一次祭奠免单，鼓励日常社交；免费也要先占名额防并发双免
        String today = java.time.LocalDate.now(com.gongpu.game.GameConst.DAILY_ZONE).toString();
        boolean firstFree = !today.equals(actor.tributeDay);
        if (firstFree) {
            actor.tributeDay = today;
        } else {
            if (Num.lt(actor.gold, Num.of(TRIBUTE_COST))) {
                throw ApiException.badRequest("金币不足，祭奠需要 " + TRIBUTE_COST + " 金币");
            }
            actor.gold = Num.sub(actor.gold, Num.of(TRIBUTE_COST));
        }
        saveService.persist(actorId, slot, actor, null);
        // 墓主 +1 香火(原子,并发祭奠不丢)
        users.addIncense(owner.getId(), 1);
        graves.addTribute(g.getId());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("owner", owner.getUsername());
        out.put("incenseGiven", 1);
        out.put("firstFree", firstFree);
        out.put("goldLeft", Num.plain(actor.gold));
        return out;
    }

    // ==================== 香火复活 ====================

    /** 香火余额。 */
    public int incense(long userId) {
        return users.findById(userId).map(u -> u.getIncense() == null ? 0 : u.getIncense()).orElse(0);
    }

    /**
     * 香火复活：花 1 炷香火，原地满血满蓝复活，保留全部面板装备。
     * <p>这是网游化后对「硬核死亡」的缓冲：单人可重开，但朋友可以捞你。</p>
     */
    @Transactional
    public Map<String, Object> reviveByIncense(long userId, int slot) {
        User u = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("账号不存在"));
        int have = u.getIncense() == null ? 0 : u.getIncense();
        if (have < REVIVE_INCENSE_COST) {
            throw ApiException.badRequest("香火不足（需要 " + REVIVE_INCENSE_COST + " 炷，现有 "
                    + have + " 炷）。去帮助别的玩家，或者等别人来祭奠你。");
        }
        PlayerState s = saveService.load(userId, slot);
        if (!s.dead) {
            throw ApiException.badRequest("当前并未死亡");
        }
        // 原子扣香火:同账号两槽并发复活时,只有一槽能扣成功(读-改-写会双双成功刷复活)
        if (users.spendIncense(userId, REVIVE_INCENSE_COST) == 0) {
            throw ApiException.badRequest("香火不足（需要 " + REVIVE_INCENSE_COST + " 炷，现有 "
                    + have + " 炷）。去帮助别的玩家，或者等别人来祭奠你。");
        }

        // 满血满蓝原地复活：面板装备全部保留
        s.dead = false;
        s.deathCause = null;
        PanelCalculator.Panel p = PanelCalculator.of(s);
        s.life.curHP = p.maxHP();
        s.life.curMP = p.MP();
        s.buffs.remove("poison");
        s.buffs.remove("burn_dot");
        s.buffs.remove("silence");
        s.lastTickAt = System.currentTimeMillis();
        // 走 persist 刷新缓存（旧实现手写 encode+save+evict：缓存被清后下轮询回源
        // DB 才恢复，且冗余列 gen/zone/maxHp 与成就评估都被跳过）
        saveService.persist(userId, slot, s, null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("incenseLeft", have - REVIVE_INCENSE_COST);
        out.put("curHP", Num.plain(s.life.curHP));
        return out;
    }

    // ==================== 切磋 PvP ====================

    /**
     * 切磋：用双方「当前面板」跑一场 30 回合模拟战斗。
     * <p>不伤任何一方状态，只结算胜负战绩。攻防公式与 PvE 一致，
     * 先手由 RNG 决定，命中闪避暴击全部参与。</p>
     */
    @Transactional
    public Map<String, Object> duel(long challengerId, int slot, String targetName) {
        User challenger = users.findById(challengerId)
                .orElseThrow(() -> ApiException.unauthorized("账号不存在"));
        User target = users.findByUsername(targetName == null ? "" : targetName.trim())
                .orElseThrow(() -> ApiException.notFound("对手不存在：" + targetName));
        if (target.getId() == challengerId) {
            throw ApiException.badRequest("不能和自己切磋");
        }

        // CD 检查（Redis 由调用方做也行，这里用内存简易限频）
        String cdKey = challengerId + ":" + target.getId();
        Long last = duelCd.get(cdKey);
        long now = System.currentTimeMillis();
        if (last != null && now - last < DUEL_COOLDOWN_SEC * 1000) {
            long left = (DUEL_COOLDOWN_SEC * 1000 - (now - last)) / 1000;
            throw ApiException.badRequest("切磋冷却中，还剩 " + left + " 秒");
        }
        if (duelCd.size() > 10_000) {
            // 惰性过期:防 key 永不清理无限膨胀
            duelCd.entrySet().removeIf(e -> now - e.getValue() > DUEL_COOLDOWN_SEC * 1000);
        }
        duelCd.put(cdKey, now);

        // 双方面板：从各自最新存档槽取（取生命上限最高的那一槽）
        PanelCalculator.Panel a = bestPanel(challengerId, challenger.getUsername());
        PanelCalculator.Panel b = bestPanel(target.getId(), target.getUsername());

        // 30 回合模拟
        List<String> log = new ArrayList<>();
        BigDecimal hpA = a.maxHP();
        BigDecimal hpB = b.maxHP();
        boolean aFirst = a.RNG().doubleValue() >= b.RNG().doubleValue();
        if (aFirst) {
            log.add("🏹 你的射程不短于对手，抢先出手");
        } else {
            log.add("🏹 对手射程更长，先发制人");
        }
        int round = 1;
        for (; round <= 30 && Num.gt(hpA, Num.ZERO) && Num.gt(hpB, Num.ZERO); round++) {
            // 每回合 = 1 秒战斗
            BigDecimal dmgA = perSecondDamage(a, b);
            BigDecimal dmgB = perSecondDamage(b, a);
            if (aFirst) {
                hpB = Num.sub(hpB, dmgA);
                if (Num.lte(hpB, Num.ZERO)) {
                    log.add("第" + round + "回合：你打出 " + Num.cn(dmgA) + "，对手倒下");
                    break;
                }
                hpA = Num.sub(hpA, dmgB);
                if (Num.lte(hpA, Num.ZERO)) {
                    log.add("第" + round + "回合：对手反打 " + Num.cn(dmgB) + "，你倒下了");
                    break;
                }
            } else {
                hpA = Num.sub(hpA, dmgB);
                if (Num.lte(hpA, Num.ZERO)) {
                    log.add("第" + round + "回合：对手打出 " + Num.cn(dmgB) + "，你倒下了");
                    break;
                }
                hpB = Num.sub(hpB, dmgA);
                if (Num.lte(hpB, Num.ZERO)) {
                    log.add("第" + round + "回合：你反打 " + Num.cn(dmgA) + "，对手倒下");
                    break;
                }
            }
            if (round % 10 == 0) {
                log.add("第" + round + "回合：你 " + Num.cn(Num.max(hpA, Num.ZERO))
                        + " vs 对手 " + Num.cn(Num.max(hpB, Num.ZERO)));
            }
        }

        boolean win;
        if (Num.lte(hpB, Num.ZERO)) {
            win = true;
        } else if (Num.lte(hpA, Num.ZERO)) {
            win = false;
        } else {
            // 30 回合未分胜负：剩余血量百分比高者胜
            // BigDecimal 交叉相乘比较(hpA/maxA vs hpB/maxB),double 相除在 1e308 后是 NaN 恒 false
            win = Num.gte(Num.mul(hpA, b.maxHP()), Num.mul(hpB, a.maxHP()));
            log.add("三十回合战罢，以剩余血量论英雄：" + (win ? "你略胜一筹" : "对手略胜一筹"));
        }

        // 战绩落库(原子,同账号并发切磋不丢战绩)
        if (win) {
            users.addPvpWin(challengerId);
            users.addPvpLoss(target.getId());
        } else {
            users.addPvpLoss(challengerId);
            users.addPvpWin(target.getId());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("win", win);
        out.put("target", target.getUsername());
        out.put("log", log);
        int wins = challenger.getPvpWins() == null ? 0 : challenger.getPvpWins();
        int losses = challenger.getPvpLosses() == null ? 0 : challenger.getPvpLosses();
        out.put("record", (wins + (win ? 1 : 0)) + "胜" + (losses + (win ? 0 : 1)) + "负");
        return out;
    }

    /** 切磋限频表（并发安全;读取时惰性过期,防无限膨胀）。 */
    private final Map<String, Long> duelCd = new java.util.concurrent.ConcurrentHashMap<>();

    /** 取一个玩家「生命上限最高」的存档槽面板（挂机玩家的实力代表）。 */
    private PanelCalculator.Panel bestPanel(long userId, String username) {
        PlayerState best = null;
        BigDecimal bestHp = Num.ZERO;
        for (var gs : saves.findByUserIdOrderBySlotNoAsc(userId)) {
            try {
                PlayerState s = StateCodec.decode(gs.getStateJson());
                if (Num.gt(s.life.maxHP, bestHp)) {
                    bestHp = s.life.maxHP;
                    best = s;
                }
            } catch (RuntimeException ignored) {
                // 坏档跳过
            }
        }
        if (best == null) {
            throw ApiException.badRequest(username + " 还没有任何存档，无法切磋");
        }
        return PanelCalculator.of(best);
    }

    /** 每秒期望伤害（物+魔双轨，含暴击期望、命中、双防减免）。 */
    private BigDecimal perSecondDamage(PanelCalculator.Panel atk, PanelCalculator.Panel def) {
        BigDecimal critMul = Num.add(Num.of(1), Num.mul(atk.CRIT(), Num.sub(atk.CRITD(), Num.of(1))));
        double hitRate = Math.max(0.05, atk.ACC().doubleValue() - def.EVA().doubleValue());
        BigDecimal pd = Num.max(Num.of(0), Num.sub(Num.mul(atk.PATK(), critMul),
                Num.mul(def.PDEF(), Num.of(0.8))));
        BigDecimal md = Num.gt(atk.MATK(), Num.ZERO)
                ? Num.max(Num.of(0), Num.sub(Num.mul(atk.MATK(), critMul),
                        Num.mul(def.MDEF(), Num.of(0.8))))
                : Num.ZERO;
        return Num.mul(Num.mul(Num.add(pd, md), atk.ASPD()), Num.of(hitRate));
    }

    /** 战绩卡（切磋界面用）。 */
    public Map<String, Object> pvpCard(long userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("账号不存在"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", u.getUsername());
        m.put("wins", u.getPvpWins() == null ? 0 : u.getPvpWins());
        m.put("losses", u.getPvpLosses() == null ? 0 : u.getPvpLosses());
        m.put("incense", u.getIncense() == null ? 0 : u.getIncense());
        return m;
    }

    // ==================== 世界公告 ====================

    /** 发布世界公告（史诗掉落播报）。静默失败：公告不影响战斗。 */
    public static void publishBulletin(String playerName, com.gongpu.state.PlayerState.Item it, String zoneName) {
        try {
            org.springframework.data.redis.core.StringRedisTemplate rt = HOLDER.redis;
            if (rt == null || it == null) {
                return;
            }
            String line = System.currentTimeMillis() + "|" + playerName + "|" + it.name + "|" + (it.quality + 1) + "|" + zoneName;
            rt.opsForList().leftPush(BULLETIN_KEY, line);
            rt.opsForList().trim(BULLETIN_KEY, 0, BULLETIN_MAX - 1);
            rt.expire(BULLETIN_KEY, java.time.Duration.ofHours(1));
        } catch (RuntimeException ignored) {
            // 公告失败不影响掉落
        }
    }

    /** 注入静态引用（构造器调用）。 */
    private static volatile SocialService HOLDER;

    /** 最近的世界公告（公开，前端轮询）。 */
    public java.util.List<java.util.Map<String, Object>> bulletin() {
        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        try {
            java.util.List<String> lines = redis.opsForList().range(BULLETIN_KEY, 0, BULLETIN_MAX - 1);
            if (lines != null) {
                for (String line : lines) {
                    String[] p = line.split("\\|");
                    if (p.length < 5) {
                        continue;
                    }
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("ts", Long.parseLong(p[0]));
                    m.put("player", p[1]);
                    m.put("item", p[2]);
                    m.put("stars", p[3]);
                    m.put("zone", p[4]);
                    rows.add(m);
                }
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用返回空列表
        }
        return rows;
    }

    /** 随机闲话（祭奠 / 切磋的文案点缀）。 */
    public static String flavor() {
        String[] LINES = {
                "江湖路远，拳头说话。", "一拳一点命，万世不回头。",
                "拳头再硬，命只有一条。", "存档是纪律，出拳是修行。",
                "墓碑不会说谎，碑上的数字都是拳头换来的。"
        };
        return LINES[ThreadLocalRandom.current().nextInt(LINES.length)];
    }
}
