package com.gongpu.state;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gongpu.common.ApiException;
import com.gongpu.game.GameConst;
import com.gongpu.game.GameEngine;

/**
 * 存档序列化：PlayerState ⇄ JSON 字符串。
 * <p>所有 BigDecimal 通过全局配置序列化为<b>普通十进制字符串</b>（防双精度截断），
 * 反序列化时按字符串精确还原，1e44 量级不丢精度。</p>
 */
public final class StateCodec {

    private static final ObjectMapper M = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true));

    private StateCodec() {
    }

    public static String encode(PlayerState s) {
        try {
            return M.writeValueAsString(s);
        } catch (Exception e) {
            throw new IllegalStateException("存档序列化失败", e);
        }
    }

    public static PlayerState decode(String json) {
        try {
            PlayerState s = M.readValue(json, PlayerState.class);
            normalize(s);
            return s;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("存档解析失败", e);
        }
    }

    /** 兜底修复：兼容旧档缺失字段、越界值。 */
    private static void normalize(PlayerState s) {
        if (s.life == null) {
            s.life = Life.fresh();
        }
        if (s.fixed == null) {
            s.fixed = Fixed.fresh();
        }
        if (s.blessings == null) {
            s.blessings = new java.util.LinkedHashMap<>();
        }
        if (s.blessings.isEmpty()) {
            s.blessings.put("HP", new PlayerState.Bless(0));
        }
        if (s.equipped == null) {
            s.equipped = new java.util.LinkedHashMap<>();
        }
        if (s.equipBag == null) {
            s.equipBag = new java.util.ArrayList<>();
        }
        if (s.passives == null) {
            s.passives = new java.util.ArrayList<>();
        }
        if (s.mastery == null) {
            s.mastery = new java.util.LinkedHashMap<>();
        }
        if (s.potions == null) {
            s.potions = new java.util.LinkedHashMap<>();
        }
        if (s.buffs == null) {
            s.buffs = new java.util.LinkedHashMap<>();
        }
        if (s.cds == null) {
            s.cds = new java.util.LinkedHashMap<>();
        }
        if (s.stats == null) {
            s.stats = new PlayerState.Stats();
        }
        if (s.nextUid <= 0) {
            s.nextUid = 1;
        }
        if (s.bossKills == null) {
            s.bossKills = new java.util.LinkedHashSet<>();
        }
        if (s.codex == null) {
            s.codex = new java.util.LinkedHashSet<>();
        }
        if (s.achievements == null) {
            s.achievements = new java.util.LinkedHashSet<>();
        }
        if (s.stats != null) {
            if (s.stats.lifetimeKills == null) {
                s.stats.lifetimeKills = java.math.BigDecimal.ZERO;
            }
            // 旧档种子：终身击杀字段引入前 kills 是唯一口径，以当前当世击杀为起点（此后终身单调累加）
            if (s.stats.kills != null && s.stats.lifetimeKills.compareTo(s.stats.kills) < 0) {
                s.stats.lifetimeKills = s.stats.kills;
            }
        }
        if (s.activeQuests == null) {
            s.activeQuests = new java.util.LinkedHashMap<>();
        }
        if (s.doneQuests == null) {
            s.doneQuests = new java.util.LinkedHashSet<>();
        }
        if (s.materials == null) {
            s.materials = new java.util.LinkedHashMap<>();
        }
        if (s.events == null) {
            s.events = new java.util.ArrayList<>();
        }
        if (s.potionBought == null) {
            s.potionBought = new java.util.LinkedHashMap<>();
        }
        if (s.freePoints == null) {
            s.freePoints = java.math.BigDecimal.ZERO;
        }
        if (s.statPoints == null) {
            s.statPoints = new java.util.LinkedHashMap<>();
        }
        if (s.zone == null || !GameConst.ZONES.containsKey(s.zone)) {
            s.zone = "village";
        }
        // 第八/九批集合字段兜底：JSON 显式 null 或旧档缺失时重建，防 petView/任务 ensure 空指针
        if (s.petsOwned == null) {
            s.petsOwned = new java.util.ArrayList<>();
        }
        if (s.petLevels == null) {
            s.petLevels = new java.util.LinkedHashMap<>();
        }
        if (s.petExps == null) {
            s.petExps = new java.util.LinkedHashMap<>();
        }
        if (s.petNames == null) {
            s.petNames = new java.util.LinkedHashMap<>();
        }
        if (s.monthlyTasks == null) {
            s.monthlyTasks = new java.util.ArrayList<>();
        }
        if (s.weeklyTasks == null) {
            s.weeklyTasks = new java.util.ArrayList<>();
        }
        if (s.dailyTasks == null) {
            s.dailyTasks = new java.util.ArrayList<>();
        }
        // 出战宠物合法性：未拥有/图鉴不存在则下场；等级钳制到 [1, 上限]、经验不为负
        if (s.pet != null && (!GameConst.PETS.containsKey(s.pet) || !s.petsOwned.contains(s.pet))) {
            s.pet = null;
        }
        s.petLevels.replaceAll((k, v) -> v == null ? 1
                : Math.max(1, Math.min(GameConst.PET_MAX_LEVEL, v)));
        s.petExps.replaceAll((k, v) -> v == null ? 0.0 : Math.max(0.0, v));
        convertLegacyPctAffixes(s);
        if (s.lastTickAt <= 0) {
            s.lastTickAt = System.currentTimeMillis();
        }
    }

    /**
     * v3.9 一次性转换:旧档装备上的百分比词条 → 直接数值词条(玩家要求:
     * 装备属性不要 "+14%" 而是 "+14" 直接加面板)。按旧区间→新区间线性映射,
     * 幂等(转换后 type=flat 不会再进本方法分支)。新档/新掉落无此类型。
     */
    private static void convertLegacyPctAffixes(PlayerState s) {
        if (s.equipped == null && s.equipBag == null) {
            return;
        }
        java.util.List<PlayerState.Item> all = new java.util.ArrayList<>();
        if (s.equipped != null) {
            all.addAll(s.equipped.values());
        }
        if (s.equipBag != null) {
            all.addAll(s.equipBag);
        }
        for (PlayerState.Item it : all) {
            if (it == null || it.affixes == null) {
                continue;
            }
            for (PlayerState.AffixInst a : it.affixes) {
                if (a == null || !"pct".equals(a.type) || a.val == null) {
                    continue;
                }
                double[] range = LEGACY_PCT_MAP.get(a.key);
                if (range == null) {
                    continue;
                }
                double oldMin = range[0], oldMax = range[1], newMin = range[2], newMax = range[3];
                double v = a.val.doubleValue();
                double t = Math.max(0, Math.min(1, (v - oldMin) / (oldMax - oldMin)));
                a.val = new java.math.BigDecimal(String.valueOf(
                        Math.round((newMin + t * (newMax - newMin)) * 100) / 100.0));
                a.type = "flat";
            }
        }
    }

    /**
     * 旧 pct 词条区间 → 新 flat 区间(键 → {旧min,旧max,新min,新max})。
     * 前 7 键 = v3.9 改直接数值的原乘算词条;后 7 键为防御性兜底:
     * 率型键(CRIT/CRITD/ACC/EVA/LIFESTEAL/ASPD)的 flat 值本来就是同一小数单位,
     * 同区间映射=仅翻 type 保住原值;RNG 按旧比例折进延伸(1~2 步)区间。
     * 本表必须覆盖全部词条键:PanelCalculator 对率型/射程键只读 flat 分支,
     * 迁移漏键 = 该词条静默失效(玩家属性无声蒸发)。
     */
    private static final java.util.Map<String, double[]> LEGACY_PCT_MAP = java.util.Map.ofEntries(
            java.util.Map.entry("PATK", new double[]{0.05, 0.20, 4, 16}),
            java.util.Map.entry("MATK", new double[]{0.05, 0.20, 6, 24}),
            java.util.Map.entry("REGEN", new double[]{0.05, 0.20, 1, 4}),
            java.util.Map.entry("SPD", new double[]{0.05, 0.15, 10, 30}),
            java.util.Map.entry("PDEF", new double[]{0.10, 0.25, 6, 20}),
            java.util.Map.entry("MDEF", new double[]{0.10, 0.25, 6, 18}),
            java.util.Map.entry("MREGEN", new double[]{0.10, 0.30, 1, 3}),
            java.util.Map.entry("CRIT", new double[]{0.02, 0.30, 0.02, 0.30}),
            java.util.Map.entry("CRITD", new double[]{0.10, 1.00, 0.10, 1.00}),
            java.util.Map.entry("ACC", new double[]{0.02, 0.20, 0.02, 0.20}),
            java.util.Map.entry("EVA", new double[]{0.02, 0.20, 0.02, 0.20}),
            java.util.Map.entry("LIFESTEAL", new double[]{0.005, 0.05, 0.005, 0.05}),
            java.util.Map.entry("ASPD", new double[]{0.02, 0.20, 0.02, 0.20}),
            java.util.Map.entry("RNG", new double[]{0.10, 0.50, 1, 2}),
            java.util.Map.entry("MP", new double[]{100, 500, 100, 500}),   // v3.13 新增,恒等区间防御
            java.util.Map.entry("maxHP", new double[]{50, 300, 50, 300}));   // 第四批新增,恒等区间防御

    /** 包内可见:单测校验迁移表覆盖全部词条键(漏键=旧档词条静默失效)。 */
    static boolean legacyPctMapHasKey(String key) {
        return LEGACY_PCT_MAP.containsKey(key);
    }

    public static long aliveSeconds(PlayerState s) {
        long now = System.currentTimeMillis();
        long born = s.stats.born <= 0 ? now : s.stats.born;
        return Math.max(0, (now - born) / 1000);
    }
}
