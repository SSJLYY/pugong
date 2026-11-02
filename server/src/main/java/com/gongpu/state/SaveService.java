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

    public SaveService(GameSaveRepository repo, StateCache cache) {
        this.repo = repo;
        this.cache = cache;
    }

    /** 取档（优先 Redis，未命中回源 MySQL）。 */
    public PlayerState load(long userId, int slot) {
        PlayerState cached = cache.get(userId, slot);
        if (cached != null) {
            return cached;
        }
        GameSave gs = repo.findByUserIdAndSlotNo(userId, slot)
                .orElseThrow(() -> ApiException.notFound("槽位 " + slot + " 没有存档"));
        PlayerState s = StateCodec.decode(gs.getStateJson());
        cache.put(userId, slot, s);
        return s;
    }

    public boolean exists(long userId, int slot) {
        return repo.findByUserIdAndSlotNo(userId, slot).isPresent();
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
        gs.setMaxHp(Num.plain(s.life.maxHP));
        gs.setAliveSeconds(StateCodec.aliveSeconds(s));
        gs.setStateJson(StateCodec.encode(s));
        gs.setUpdatedAt(Instant.now());
        repo.save(gs);
        // 回村自动快照(设计 §3.2"回安全区自动存主槽"):在村口且未死亡时,
        // 距上次快照 ≥5 分钟顺手刷一次回档点——从未手动存档的玩家死亡也有保命底线。
        // 快照含当前进度,会让"回档"最多回退 5 分钟,属硬核与体验的折中(注释明确,非 bug)。
        if ("village".equals(s.zone) && !s.dead) {
            Instant last = gs.getSavedAt();
            if (last == null || Instant.now().toEpochMilli() - last.toEpochMilli() >= 5 * 60_000L) {
                gs.setSnapshotJson(StateCodec.encode(s));
                gs.setSavedAt(Instant.now());
                repo.save(gs);
            }
        }
        cache.put(userId, slot, s);
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
        gs.setSnapshotJson(StateCodec.encode(s));
        gs.setSavedAt(now);
        gs.setStateJson(StateCodec.encode(s));
        gs.setUpdatedAt(now);
        gs.setMaxHp(Num.plain(s.life.maxHP));
        gs.setAliveSeconds(StateCodec.aliveSeconds(s));
        repo.save(gs);
        cache.put(userId, slot, s);
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
        if (snap != null && !snap.isBlank()) {
            restored = StateCodec.decode(snap);
        } else {
            // 无快照：按硬核规则重开一世（面板清零，传承保留）
            restored = StateCodec.decode(gs.getStateJson());
            resetForNewLife(restored);
        }
        restored.dead = false;
        restored.deathCause = null;
        restored.lastTickAt = System.currentTimeMillis();
        restored.lastSaveAt = System.currentTimeMillis();
        gs.setStateJson(StateCodec.encode(restored));
        gs.setUpdatedAt(Instant.now());
        gs.setGen(restored.gen);
        gs.setZone(restored.zone);
        gs.setMaxHp(Num.plain(restored.life.maxHP));
        repo.save(gs);
        cache.put(userId, slot, restored);
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
