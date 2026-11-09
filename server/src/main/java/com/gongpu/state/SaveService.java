package com.gongpu.state;

import com.gongpu.common.ApiException;
import com.gongpu.common.Num;
import com.gongpu.config.StateCache;
import com.gongpu.game.GameConst;
import com.gongpu.game.GameEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 存档槽服务：载入 / 存档 / 新建 / 删除 / 列表。
 * <p>并发控制：Redis 短锁 + 乐观推进；同一档的写入走事务，避免半截状态。</p>
 */
@Service
public class SaveService {

    private final GameSaveRepository repo;
    private final StateCache cache;

    /** 轮询节流落库：每个存档距上次 MySQL 写入的最小间隔（毫秒）。 */
    private static final long PERSIST_THROTTLE_MS = 5_000L;
    private final java.util.concurrent.ConcurrentHashMap<String, Long> lastDbWrite =
            new java.util.concurrent.ConcurrentHashMap<>();

    public SaveService(GameSaveRepository repo, StateCache cache) {
        this.repo = repo;
        this.cache = cache;
    }

    // ---- 冗余列边界防护（第十四批修复，2026-11-06）----
    // 事故：玩家终身击杀指数增长冲破 10^65，DECIMAL(65,0) 冗余列 Out of range，
    // 整批 update 失败 → 登录/存档 500（「服务器开小差」）。
    // state_json 才是真实状态源，冗余列仅供运营 SUM，超限时钳到上限，不丢玩家数据。

    /** 冗余统计列安全上限：DECIMAL(65,0) 极值 10^65-1，取 10^63 留两级聚合余量。 */
    private static final java.math.BigDecimal STATS_COLUMN_CAP =
            java.math.BigDecimal.ONE.scaleByPowerOfTen(63);

    /** max_hp 列宽 varchar(80)：大数 toPlainString 可能超长，需截断。 */
    private static final int MAX_HP_COLUMN_LEN = 80;

    /** 冗余统计列写入钳制：null→0、去尾零、超 ±10^63 封顶（保持单调，聚合安全）。 */
    static java.math.BigDecimal capStatsColumn(java.math.BigDecimal v) {
        if (v == null) {
            return java.math.BigDecimal.ZERO;
        }
        java.math.BigDecimal s = v.stripTrailingZeros();
        if (s.scale() != 0) {
            s = s.setScale(0, java.math.RoundingMode.DOWN);
        }
        if (s.compareTo(STATS_COLUMN_CAP) > 0) {
            return STATS_COLUMN_CAP;
        }
        if (s.compareTo(STATS_COLUMN_CAP.negate()) < 0) {
            return STATS_COLUMN_CAP.negate();
        }
        return s;
    }

    /** varchar 列写入截断。 */
    static String capVarchar(String s, int len) {
        if (s == null || s.length() <= len) {
            return s;
        }
        return s.substring(0, len);
    }

    // 存档体积观测（第八批）：落库 JSON 字节数——体积膨胀是编解码/落库成本的第一指标
    private static final java.util.concurrent.atomic.AtomicLong STATE_BYTES_LAST =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong STATE_BYTES_MAX =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong STATE_BYTES_SUM =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong STATE_PERSISTS =
            new java.util.concurrent.atomic.AtomicLong();

    static void recordStateBytes(int bytes) {
        STATE_BYTES_LAST.set(bytes);
        STATE_BYTES_MAX.accumulateAndGet(bytes, Math::max);
        STATE_BYTES_SUM.addAndGet(bytes);
        STATE_PERSISTS.incrementAndGet();
    }

    /** 存档体积快照（health 用）：最近/平均/最大字节与带备份环的存档数（环计数由 health 侧查库补）。 */
    public static java.util.Map<String, Object> stateSizeStats() {
        long n = STATE_PERSISTS.get();
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("lastBytes", STATE_BYTES_LAST.get());
        m.put("avgBytes", n == 0 ? 0 : STATE_BYTES_SUM.get() / n);
        m.put("maxBytes", STATE_BYTES_MAX.get());
        m.put("persists", n);
        return m;
    }

    /** 取档（优先 Redis，未命中回源 MySQL）。 */
    public PlayerState load(long userId, int slot) {
        PlayerState cached = cache.get(userId, slot);
        if (cached != null) {
            return cached;
        }
        GameSave gs = repo.findByUserIdAndSlotNo(userId, slot)
                .orElseThrow(() -> ApiException.notFound("槽位 " + slot + " 没有存档"));
        PlayerState s = decodeOrRecover(gs);
        cache.put(userId, slot, s);
        return s;
    }

    // ---------------- 滚动备份环（第七批）：主档历史自愈 ----------------

    /** 备份轮转间隔：距上一份备份 ≥10 分钟才收新的一份，避免环里全是同一分钟的近似状态。 */
    static final long BACKUP_ROTATE_MS = 10 * 60_000L;
    /** 环容量：保留最近 3 份。 */
    static final int BACKUP_RING_MAX = 3;

    private static final com.fasterxml.jackson.databind.ObjectMapper RING_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 轮转备份环：newest 在尾部。上一份未满轮转间隔 → 原样返回（同一引用表示未变更）；
     * 环本身损坏 → 从当前状态重建单元素环。自愈数据宁可少而新，不可多而旧。
     */
    static String rotateBackupRing(String ringJson, String stateJson, long now) {
        // 第二十批·轮3 快速判期：环未到期直接原样返回，不做全量 Jackson 解析。
        // 此前每次落库（含 5 秒节流帧）都把环内最多 3 份完整状态 JSON 解析成 List 再判期，
        // 而 ≥95% 的调用答案都是「未到期」。JSON 字符串值内引号一律转义，
        // 裸序列 "t": 只会是真实键，末元素时间戳可安全抽取；抽取失败回退全量解析。
        if (ringJson != null && !ringJson.isBlank()) {
            Long lastT = lastRingTimestamp(ringJson);
            if (lastT != null && now - lastT < BACKUP_ROTATE_MS) {
                return ringJson;
            }
        }
        List<Map<String, Object>> ring = new ArrayList<>();
        if (ringJson != null && !ringJson.isBlank()) {
            try {
                List<?> parsed = RING_MAPPER.readValue(ringJson, List.class);
                for (Object o : parsed) {
                    if (o instanceof Map<?, ?> mm) {
                        Map<String, Object> e = new LinkedHashMap<>();
                        e.put("t", mm.get("t"));
                        e.put("j", mm.get("j"));
                        ring.add(e);
                    }
                }
            } catch (Exception badRing) {
                ring.clear(); // 环坏了就重建，不连累主流程
            }
        }
        if (!ring.isEmpty()) {
            Object t = ring.get(ring.size() - 1).get("t");
            long lastT = t instanceof Number n ? n.longValue() : 0L;
            if (now - lastT < BACKUP_ROTATE_MS) {
                return ringJson;
            }
        }
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("t", now);
        e.put("j", stateJson);
        ring.add(e);
        while (ring.size() > BACKUP_RING_MAX) {
            ring.remove(0);
        }
        try {
            return RING_MAPPER.writeValueAsString(ring);
        } catch (Exception impossible) {
            return ringJson;
        }
    }

    /** 抽取备份环末元素时间戳；结构不符/解析失败返回 null（调用方回退全量解析，正确性不受影响）。 */
    private static Long lastRingTimestamp(String ringJson) {
        int i = ringJson.lastIndexOf("\"t\":");
        if (i < 0) {
            return null;
        }
        int start = i + 4;
        int end = start;
        while (end < ringJson.length()
                && (Character.isDigit(ringJson.charAt(end)) || ringJson.charAt(end) == '-')) {
            end++;
        }
        if (end == start) {
            return null;
        }
        try {
            return Long.parseLong(ringJson.substring(start, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 从备份环里按新→旧找第一份能解码的状态；全坏/无环返回 null。 */
    static PlayerState decodeFromRing(String ringJson) {
        if (ringJson == null || ringJson.isBlank()) {
            return null;
        }
        try {
            List<?> parsed = RING_MAPPER.readValue(ringJson, List.class);
            for (int i = parsed.size() - 1; i >= 0; i--) {
                if (parsed.get(i) instanceof Map<?, ?> mm && mm.get("j") instanceof String j) {
                    try {
                        return StateCodec.decode(j);
                    } catch (RuntimeException ignored) {
                        // 继续找更旧的一份
                    }
                }
            }
        } catch (Exception ignored) {
            // 环整体不可解析
        }
        return null;
    }

    /**
     * 存档解码自愈：主档 JSON 损坏（截断写/坏迁移）时依次回退——滚动备份环（新→旧）、
     * 存档点快照，都坏才报明确错误，避免玩家被永久锁在 500 外。
     */
    static PlayerState decodeOrRecover(GameSave gs) {
        try {
            return StateCodec.decode(gs.getStateJson());
        } catch (RuntimeException mainBad) {
            PlayerState fromRing = decodeFromRing(gs.getBackupRing());
            if (fromRing != null) {
                return fromRing;
            }
            String snap = gs.getSnapshotJson();
            if (snap != null && !snap.isBlank()) {
                try {
                    return StateCodec.decode(snap);
                } catch (RuntimeException ignored) {
                    // 快照也坏，走下面的统一报错
                }
            }
            throw new ApiException(500, "SAVE_CORRUPT",
                    "存档数据损坏，请联系管理员或删除该档重开");
        }
    }

    public boolean exists(long userId, int slot) {
        return repo.findByUserIdAndSlotNo(userId, slot).isPresent();
    }

    /**
     * 节流落库（供每秒轮询的状态帧使用）：缓存与成就评估每帧都做，
     * MySQL 写入每档最多每 {@link #PERSIST_THROTTLE_MS} 一次；
     * 死亡状态强制立即落库（硬核死亡制不能丢）。显式动作仍走全量 persist。
     */
    public void persistThrottled(long userId, int slot, PlayerState s) {
        String key = userId + ":" + slot;
        long now = System.currentTimeMillis();
        Long last = lastDbWrite.get(key);
        if (!s.dead && last != null && now - last < PERSIST_THROTTLE_MS) {
            s.lastSaveAt = now;
            if (s.achievements != null) {
                s.achievements.addAll(com.gongpu.game.Achievements.evaluate(s));
            }
            cache.put(userId, slot, s);
            return;
        }
        persist(userId, slot, s, null);
        // 第二十批·轮7：节流表只增不删，随历史玩家×槽位单调膨胀——超阈值清掉久未写入的条目
        // （条目过期不影响语义：下次写仍按 PERSIST_THROTTLE_MS 从头计）
        if (lastDbWrite.size() > 4096) {
            lastDbWrite.entrySet().removeIf(e -> now - e.getValue() > 10 * 60_000L);
        }
        lastDbWrite.put(key, System.currentTimeMillis());
    }

    /** 落库并刷新缓存。 */
    @Transactional
    public void persist(long userId, int slot, PlayerState s, String slotName) {
        s.lastSaveAt = System.currentTimeMillis();
        // 成就评估(服务端权威):每次落库前把新达标项写进解锁集合,随本次 JSON 一起持久化
        if (s.achievements != null) {
            s.achievements.addAll(com.gongpu.game.Achievements.evaluate(s));
        }
        GameSave gs = repo.findByUserIdAndSlotNo(userId, slot).orElseGet(() -> {
            GameSave n = new GameSave();
            n.setUserId(userId);
            n.setSlotNo(slot);
            n.setCreatedAt(Instant.now());
            return n;
        });
        if (slotName != null && !slotName.isBlank()) {
            gs.setSlotName(slotName.length() > 16 ? slotName.substring(0, 16) : slotName);
        }
        gs.setGen(s.gen);
        gs.setZone(s.zone);
        gs.setMaxHp(capVarchar(Num.plain(s.life.maxHP), MAX_HP_COLUMN_LEN));
        gs.setAliveSeconds(StateCodec.aliveSeconds(s));
        // 运营统计冗余列：与 JSON 内 stats 同步维护，聚合端一条 SUM 取全服累计
        if (s.stats != null) {
            // 冗余列存终身口径：当世 punches/kills 每世重置，聚合 SUM 必须单调（见 StatDailyAggregator）
            gs.setPunchesTotal(capStatsColumn(s.stats.lifetimePunches));
            gs.setKillsTotal(capStatsColumn(s.stats.lifetimeKills));
            gs.setDeathsTotal(s.stats.deaths);
            gs.setRebirthsTotal(s.stats.rebirths);
        }
        String stateJson = StateCodec.encode(s);
        recordStateBytes(stateJson.length());
        gs.setStateJson(stateJson);
        // 滚动备份环：落库顺手轮转（≥10 分钟一份、留 3 份），主档损坏时 decodeOrRecover 靠它自愈
        String rotated = rotateBackupRing(gs.getBackupRing(), stateJson, System.currentTimeMillis());
        if (rotated != gs.getBackupRing()) {
            gs.setBackupRing(rotated);
        }
        gs.setUpdatedAt(Instant.now());
        // 回村自动快照(设计 §3.2"回安全区自动存主槽"):在村口且未死亡时,
        // 距上次快照 ≥5 分钟顺手刷一次回档点——从未手动存档的玩家死亡也有保命底线。
        // 快照含当前进度,会让"回档"最多回退 5 分钟,属硬核与体验的折中(注释明确,非 bug)。
        // 第二十批·轮3：快照字段在首次 save 前设置并复用 stateJson（快照=当前状态同一份编码），
        // 合并原先「两次 save + 二次 encode」的写放大。
        if ("village".equals(s.zone) && !s.dead) {
            Instant last = gs.getSavedAt();
            if (last == null || Instant.now().toEpochMilli() - last.toEpochMilli() >= 5 * 60_000L) {
                gs.setSnapshotJson(stateJson);
                gs.setSavedAt(Instant.now());
            }
        }
        repo.save(gs);
        // 第二十批·轮2：复用上面已编码的 stateJson，不再让 cache.put 二次全量 encode
        cache.putJson(userId, slot, stateJson);
    }

    /**
     * 写入存档点快照（硬核死亡制的唯一保命手段）。
     * <p>只有手动存档 / 回村自动存档会调用；此后死亡回档读的就是这份快照。</p>
     */
    @Transactional
    public void createSnapshot(long userId, int slot, PlayerState s) {
        if (s.dead) {
            throw ApiException.badRequest("已经阵亡，无法存档");
        }
        GameSave gs = repo.findByUserIdAndSlotNo(userId, slot)
                .orElseThrow(() -> ApiException.notFound("槽位 " + slot + " 没有存档"));
        Instant now = Instant.now();
        s.lastSaveAt = now.toEpochMilli();
        // 第二十批·轮2：快照与主档是同一份状态，encode 一次两用（原两次全量 encode）
        String stateJson = StateCodec.encode(s);
        gs.setSnapshotJson(stateJson);
        gs.setSavedAt(now);
        gs.setStateJson(stateJson);
        gs.setUpdatedAt(now);
        gs.setMaxHp(capVarchar(Num.plain(s.life.maxHP), MAX_HP_COLUMN_LEN));
        gs.setAliveSeconds(StateCodec.aliveSeconds(s));
        repo.save(gs);
        cache.putJson(userId, slot, stateJson);
    }

    /** 是否有可回档的快照。 */
    public boolean hasSnapshot(long userId, int slot) {
        return repo.findByUserIdAndSlotNo(userId, slot)
                .map(g -> g.getSnapshotJson() != null && !g.getSnapshotJson().isBlank())
                .orElse(false);
    }

    /**
     * 死亡回档：把当前档还原为最后一次存档点。
     * <p>若从未存过档（无快照），按硬核规则"大侠请重新来过"——回到第 0 世面板，
     * 但万世传承（赋能与世代）保留。</p>
     */
    @Transactional
    public PlayerState rollback(long userId, int slot) {
        GameSave gs = repo.findByUserIdAndSlotNo(userId, slot)
                .orElseThrow(() -> ApiException.notFound("槽位 " + slot + " 没有存档"));
        PlayerState restored;
        String snap = gs.getSnapshotJson();
        PlayerState fromSnap = null;
        if (snap != null && !snap.isBlank()) {
            try {
                fromSnap = StateCodec.decode(snap);
            } catch (RuntimeException ignored) {
                // 快照损坏：按无快照处理（主档重开一世），不让回档入口也锁死
            }
        }
        if (fromSnap != null) {
            restored = fromSnap;
        } else {
            // 无快照：按硬核规则重开一世（面板清零，传承保留）
            restored = StateCodec.decode(gs.getStateJson());
            resetForNewLife(restored);
        }
        restored.dead = false;
        restored.deathCause = null;
        restored.lastTickAt = System.currentTimeMillis();
        restored.lastSaveAt = System.currentTimeMillis();
        String restoredJson = StateCodec.encode(restored);
        gs.setStateJson(restoredJson);
        gs.setUpdatedAt(Instant.now());
        gs.setGen(restored.gen);
        gs.setZone(restored.zone);
        gs.setMaxHp(capVarchar(Num.plain(restored.life.maxHP), MAX_HP_COLUMN_LEN));
        if (restored.stats != null) {
            gs.setPunchesTotal(capStatsColumn(restored.stats.lifetimePunches));
            gs.setKillsTotal(capStatsColumn(restored.stats.lifetimeKills));
            gs.setDeathsTotal(restored.stats.deaths);
            gs.setRebirthsTotal(restored.stats.rebirths);
        }
        repo.save(gs);
        cache.putJson(userId, slot, restoredJson);
        return restored;
    }

    /** 死亡后就地重开一世：面板清零，传承保留。 */
    @Transactional
    public PlayerState restart(PlayerState s, long userId, int slot) {
        resetForNewLife(s);
        s.dead = false;
        s.deathCause = null;
        s.lastTickAt = System.currentTimeMillis();
        persist(userId, slot, s, null);
        return s;
    }

    public static void resetForNewLife(PlayerState s) {   // public:单测验证 extraSlots 跨世保留(仅重置当世面板/背包,不动天赋与积累)
        // 新手保底每世重新生效(legacy 语义)
        s.newbieKills = 0;
        s.newbieWeaponsGiven = 0;
        s.ringGranted = false;
        s.life = com.gongpu.state.Life.fresh();
        s.fixed = com.gongpu.state.Fixed.fresh();
        s.gold = java.math.BigDecimal.ZERO;
        s.equipBag = new java.util.ArrayList<>();
        s.equipped = new java.util.LinkedHashMap<>();
        s.passives = new java.util.ArrayList<>();
        s.mastery = new java.util.LinkedHashMap<>();
        s.potions = new java.util.LinkedHashMap<>();
        s.potions.put("hp_s", 3);
        s.buffs = new java.util.LinkedHashMap<>();
        s.cds = new java.util.LinkedHashMap<>();
        // 护盾是 shield_pot buff 的附属量（到期即清零，见 GameEngine.tickBuff）：
        // buff 全清时盾必须同清，否则新一世白带一层无 buff 的盾（重置审计补漏）
        s.shield = java.math.BigDecimal.ZERO;
        s.activeQuests = new java.util.LinkedHashMap<>();
        s.zone = "village";
        s.stats.punches = java.math.BigDecimal.ZERO;
        s.stats.kills = java.math.BigDecimal.ZERO;
        s.stats.born = System.currentTimeMillis();
        s.lastTickAt = System.currentTimeMillis();
        s.mob = null;
        // 药价阶梯每世重置:此前全工程无清理点,买涨计数跨世累积,
        // 药价很快永久钉死在 2 倍封顶(挂机自动买药会持续推高)
        if (s.potionBought != null) {
            s.potionBought.clear();
        }
        // 无限属性点是本世机制:击杀数每世重置,自由点/已加点随之清零重赚
        s.freePoints = java.math.BigDecimal.ZERO;
        s.statPoints = new java.util.LinkedHashMap<>();
        // 尸检击杀序列只属于当世,跨世残留会让尸检报出上一世的死因线索
        if (s.recentKills == null) {
            s.recentKills = new java.util.ArrayList<>();
        } else {
            s.recentKills.clear();
        }
        com.gongpu.game.GameEngine.spawnMob(s, s.zone);
    }

    /** 新建一档（第 0 世出生）。 */
    @Transactional
    public PlayerState create(long userId, int slot, String slotName) {
        if (slot < 1 || slot > 3) {
            throw ApiException.badRequest("槽位只能是 1~3");
        }
        if (exists(userId, slot)) {
            throw ApiException.conflict("槽位 " + slot + " 已有存档");
        }
        PlayerState s = NewState.create();
        persist(userId, slot, s, slotName);
        return s;
    }

    @Transactional
    public void delete(long userId, int slot) {
        repo.findByUserIdAndSlotNo(userId, slot).ifPresent(repo::delete);
        cache.evict(userId, slot);
    }

    /** 槽位列表（不返回完整状态，只给摘要）。 */
    public List<Map<String, Object>> list(long userId) {
        List<GameSave> all = repo.findByUserIdOrderBySlotNoAsc(userId);
        Map<Integer, GameSave> bySlot = new LinkedHashMap<>();
        for (GameSave g : all) {
            bySlot.put(g.getSlotNo(), g);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            GameSave g = bySlot.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("slot", i);
            if (g == null) {
                m.put("empty", true);
            } else {
                m.put("empty", false);
                m.put("slotName", g.getSlotName());
                m.put("gen", g.getGen());
                m.put("zone", g.getZone());
                m.put("maxHp", g.getMaxHp());
                m.put("aliveSeconds", g.getAliveSeconds());
                m.put("updatedAt", g.getUpdatedAt());
            }
            out.add(m);
        }
        return out;
    }

    /**
     * 推进一个档到当前时刻，返回结算摘要。
     * <p>唯一入口：所有会改变时间轴的动作（读状态、动作、离线结算）都调这里，
     * 保证「任何时刻只推进一次」。</p>
     */
    public GameEngine.AdvanceResult advanceNow(long userId, int slot, PlayerState s) {
        long now = System.currentTimeMillis();
        boolean offline = s.lastTickAt > 0
                && (now - s.lastTickAt) / 1000.0 > GameConst.BATCH_THRESHOLD_SEC * 30;
        double eff = offline ? GameConst.OFFLINE_EFF : 1.0;
        GameEngine.AdvanceResult r = GameEngine.advance(s, now, eff, offline);
        return r;
    }

    public Map<String, Object> summary(PlayerState s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gen", s.gen);
        m.put("zone", s.zone);
        m.put("maxHP", Num.plain(s.life.maxHP));
        m.put("gold", Num.plain(s.gold));
        m.put("dead", s.dead);
        return m;
    }
}
