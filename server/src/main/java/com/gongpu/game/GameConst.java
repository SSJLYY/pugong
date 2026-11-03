package com.gongpu.game;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 游戏常量表（服务端权威）。
 * <p>数值与 DESIGN.md v0.4 对齐；前端不再持有任何可被篡改的数值。</p>
 */
public final class GameConst {

    private GameConst() {
    }

    // ==================== 赋能体系 ====================

    /** 赋能顺序：HP 先天自带，其余 12 条需逐世解锁。 */
    public static final List<String> BLESS_ORDER = List.of(
            "HP", "PATK", "MATK", "PDEF", "MDEF", "ASPD",
            "RNG", "SPD", "CRIT", "CRITD", "ACC", "EVA", "REGEN");

    /** 每次普攻的基础赋能步长。 */
    public static final Map<String, BigDecimal> BLESS_STEP = Map.ofEntries(
            Map.entry("HP", new BigDecimal("1")),
            Map.entry("PATK", new BigDecimal("1")),
            Map.entry("MATK", new BigDecimal("1")),
            Map.entry("PDEF", new BigDecimal("1")),
            Map.entry("MDEF", new BigDecimal("1")),
            Map.entry("ASPD", new BigDecimal("0.01")),
            Map.entry("RNG", new BigDecimal("0.001")),
            Map.entry("SPD", new BigDecimal("1")),
            Map.entry("CRIT", new BigDecimal("0.0001")),
            Map.entry("CRITD", new BigDecimal("0.001")),
            Map.entry("ACC", new BigDecimal("0.0001")),
            Map.entry("EVA", new BigDecimal("0.0001")),
            Map.entry("REGEN", new BigDecimal("1")));

    /** 百分比类赋能的上限（到顶后不可再强化）。 */
    /** 「每日」边界的唯一时区:签到/每日无相位栏都用它,避免双口径(此前一个用系统默认一个用上海)。 */
    public static final java.time.ZoneId DAILY_ZONE = java.time.ZoneId.of("Asia/Shanghai");

    public static final Map<String, BigDecimal> BLESS_CAP = Map.of(
            "CRIT", new BigDecimal("1"),
            "ACC", new BigDecimal("1"),
            "EVA", new BigDecimal("0.95"));

    public static final Map<String, String> BLESS_NAME = Map.ofEntries(
            Map.entry("HP", "生命上限"), Map.entry("PATK", "物攻"), Map.entry("MATK", "魔攻"), Map.entry("ATK", "攻击（物攻/魔攻取高）"),
            Map.entry("PDEF", "物防"), Map.entry("MDEF", "魔防"), Map.entry("ASPD", "攻速"),
            Map.entry("RNG", "射程"), Map.entry("SPD", "移速"), Map.entry("CRIT", "暴击率"),
            Map.entry("CRITD", "暴伤"), Map.entry("ACC", "命中"), Map.entry("EVA", "闪避"),
            Map.entry("REGEN", "回血"));

    public static final Map<String, String> BLESS_EMOJI = Map.ofEntries(
            Map.entry("HP", "❤️"), Map.entry("PATK", "🗡️"), Map.entry("MATK", "✨"),
            Map.entry("PDEF", "🛡️"), Map.entry("MDEF", "🔮"), Map.entry("ASPD", "⚡"),
            Map.entry("RNG", "🏹"), Map.entry("SPD", "👢"), Map.entry("CRIT", "💥"),
            Map.entry("CRITD", "💢"), Map.entry("ACC", "🎯"), Map.entry("EVA", "🍃"),
            Map.entry("REGEN", "💚"));

    /** 通关线：攻速 1 载次/秒 = 1e44。 */
    public static final BigDecimal CLEAR_ASPD = new BigDecimal("1e44");

    // ==================== 战斗 ====================

    public static final double DMG_VARIANCE = 0.10;
    public static final double DMG_DEF_FACTOR = 0.8;

    /** 村口木桩区的成长加成（新手友好）。 */
    public static final BigDecimal VILLAGE_GROWTH_MUL = new BigDecimal("2");

    public static final double FLEE_BASE = 0.5;
    public static final double FLEE_SPD_FACTOR = 200;
    public static final double FLEE_CAP = 0.95;
    public static final double FLEE_FAIL_COST = 0.30;

    /** 一次结算最多推进的秒数（防止单次请求被超大时间窗拖垮）。 */
    public static final long MAX_ADVANCE_SECONDS = 12L * 3600;

    /** 单个 tick 的时间粒度：超过该值走批量数学结算。 */
    public static final double BATCH_THRESHOLD_SEC = 2.0;

    // ==================== 怪物与区域 ====================

    public record MobDef(
            String id, String name, String emoji,
            String maxHP, String PATK, String MATK, String PDEF, String MDEF,
            double ASPD, double RNG, double SPD, long gold, String desc, List<String> tags) {
    }

    public record ZoneDef(
            String id, String name, String emoji, boolean safe, String desc,
            Map<String, String> req, List<MobDef> mobs, MobDef boss) {
    }

    private static MobDef mob(String id, String name, String emoji, String hp, String patk, String matk,
                              String pdef, String mdef, double aspd, double rng, double spd, long gold,
                              String desc, List<String> tags) {
        return new MobDef(id, name, emoji, hp, patk, matk, pdef, mdef, aspd, rng, spd, gold, desc, tags);
    }

    public static final Map<String, ZoneDef> ZONES = buildZones();

    /**
     * 图鉴可收集总数：各区域可击杀怪种（无限血木桩不可杀、不入图鉴）+ 各区域 Boss 条目（boss_区域）。
     * 与 GameEngine 击杀时 codex 的写入规则同口径，供「全图鉴」成就判定。
     */
    public static int codexTotal() {
        int n = 0;
        for (ZoneDef z : ZONES.values()) {
            for (MobDef d : z.mobs()) {
                if (d.maxHP() != null) {
                    n++;
                }
            }
            if (z.boss() != null) {
                n++;
            }
        }
        return n;
    }

    // ==================== 怪物词条（v3.15） ====================
    // 野怪个体词条:出生时按区域深度掷骰,给同一张脸的怪一点脾气——残血狂暴、
    // 破甲无视防御、缴械压普攻。Boss 不带词条(已有沉默机制),村口木桩永远温顺。

    public record TraitDef(String id, String name, String emoji, String desc) {
    }

    public static final Map<String, TraitDef> MOB_TRAITS = Map.of(
            "frenzy", new TraitDef("frenzy", "狂暴", "💢", "生命低于 30% 时攻速 +50%，小心它临死反扑"),
            "sunder", new TraitDef("sunder", "破甲", "🪓", "攻击无视你 30% 的物防与魔防"),
            "disarm", new TraitDef("disarm", "缴械", "🔗", "压制你的武器，你的普攻伤害 -15%"),
            "leech", new TraitDef("leech", "嗜血", "🩸", "命中你时吸回所造成伤害 30% 的生命，拖得越久它越难杀"),
            "thorns", new TraitDef("thorns", "荆棘", "🌵", "你打它时被反扎：所造成伤害的 10% 反弹给你，出手越重扎得越狠"),
            "rich", new TraitDef("rich", "富饶", "💰", "行走的钱袋子：击杀金币 ×3，它不伤人，只散财"),
            "swift", new TraitDef("swift", "疾影", "⚡", "出手如风：攻速 +50%，别被它连绵的攻势磨死"));

    /** 狂暴触发血线 / 攻速倍率。 */
    public static final double TRAIT_FRENZY_HP_LINE = 0.30;
    public static final double TRAIT_FRENZY_HASTE = 1.5;
    /** 破甲无视的防御比例（结算时防御乘 1-该值）。 */
    public static final double TRAIT_SUNDER_IGNORE = 0.30;
    /** 缴械的普攻伤害乘区。 */
    public static final double TRAIT_DISARM_MUL = 0.85;
    /** 嗜血的吸血比例（对玩家实际掉血量）。 */
    public static final double TRAIT_LEECH_MUL = 0.30;
    /** 荆棘的反弹比例（对玩家本帧所造伤害量）。 */
    public static final double TRAIT_THORNS_MUL = 0.10;
    /** 富饶的金币倍率（击杀结算乘区）。 */
    public static final double TRAIT_RICH_GOLD_MUL = 3.0;
    /** 疾影的攻速倍率（常驻乘区，与狂暴残血加速同口径叠加）。 */
    public static final double TRAIT_SWIFT_HASTE = 1.5;

    /** 区域由浅到深的顺序（与 buildZones 的插入顺序一致）。 */
    private static final List<String> ZONE_ORDER =
            List.of("village", "plain", "forest", "mine", "swamp", "snow", "volcano");

    /**
     * 出生掷词条：村口不出；平原 10% 起、每深一层 +3%。
     * 词条池随深度解锁：平原只有狂暴，森林起加破甲，矿洞起加缴械，沼泽起加嗜血，雪原起五种全开（含荆棘）。
     */
    public static String rollMobTrait(String zoneId) {
        int idx = ZONE_ORDER.indexOf(zoneId);
        if (idx < 1) {
            return null;
        }
        double chance = 0.10 + 0.03 * (idx - 1);
        if (Math.random() >= chance) {
            return null;
        }
        List<String> pool = idx == 1
                ? List.of("frenzy")
                : idx == 2
                        ? List.of("frenzy", "sunder", "rich")
                        : idx == 3
                                ? List.of("frenzy", "sunder", "disarm", "rich", "swift")
                                : idx == 4
                                        ? List.of("frenzy", "sunder", "disarm", "leech", "rich", "swift")
                                        : List.of("frenzy", "sunder", "disarm", "leech", "thorns", "rich", "swift");
        return pool.get((int) (Math.random() * pool.size()));
    }

    private static Map<String, ZoneDef> buildZones() {
        Map<String, ZoneDef> m = new LinkedHashMap<>();

        m.put("village", new ZoneDef("village", "村口", "🏡", true,
                "安全区 · 木桩区 · 自动存档点 · 出拳成长 ×2", null,
                List.of(mob("dummy", "木桩", "🪵", null, "0", "0", "0", "0", 0, 0, 0, 0,
                        "无限血挨打桩，出拳就是修行", List.of())),
                null));

        m.put("plain", new ZoneDef("plain", "平原", "🌾", false, "建议生命 ≥ 500",
                Map.of("maxHP", "500"),
                List.of(
                        mob("rabbit", "野兔", "🐇", "120", "1", "0", "0", "0", 1.0, 1, 130, 3,
                                "攻速快移速快，小心被风筝", List.of()),
                        mob("boar", "野猪", "🐗", "500", "6", "0", "2", "0", 0.8, 1, 80, 12,
                                "皮糙肉厚，新手大敌", List.of())),
                mob("boss_boar", "野猪王", "🐗", "5000", "15", "0", "5", "0", 0.7, 2, 60, 100,
                        "平原之王，咆哮令你的被动失效", List.of())));

        m.put("forest", new ZoneDef("forest", "森林", "🌲", false, "建议生命 ≥ 3000 / 物防 ≥ 5",
                Map.of("maxHP", "3000", "PDEF", "5"),
                List.of(
                        mob("wolf", "野狼", "🐺", "2000", "15", "0", "3", "0", 1.5, 1, 140, 25,
                                "攻速快，移速快，逃跑考验", List.of()),
                        mob("bandit_bow", "山贼弓手", "🏹", "4000", "25", "0", "5", "0", 1.0, 6, 90, 40,
                                "手长 6 步，考验你的移速和射程", List.of()),
                        mob("bandit", "山贼", "🗡️", "5000", "30", "0", "8", "0", 1.0, 1, 100, 50,
                                "硬茬子，建议堆防御", List.of())),
                mob("boss_wolf", "狼王", "🐺", "30000", "50", "0", "12", "0", 1.2, 1, 150, 400,
                        "狼群之首，啸声穿透灵魂", List.of())));

        m.put("mine", new ZoneDef("mine", "矿洞", "⛏️", false, "建议生命 ≥ 12000 / 攻击 ≥ 60（石魔像物防 50,逼你堆攻/附魔/魔攻）",
                Map.of("maxHP", "12000", "ATK", "60"),
                List.of(
                        mob("golem", "石魔像", "🗿", "15000", "25", "0", "50", "0", 0.5, 1, 40, 80,
                                "物防 50 魔防 0，逼你搞附魔/魔攻", List.of()),
                        mob("bats", "蝙蝠群", "🦇", "3000", "12", "0", "2", "0", 2.5, 2, 160, 35,
                                "攻速极快，检验命中", List.of())),
                mob("boss_golem", "熔核魔像", "🗿", "120000", "80", "40", "60", "20", 0.5, 1, 35, 1200,
                        "岩浆核心，沉默领域常开", List.of("burn"))));

        m.put("swamp", new ZoneDef("swamp", "沼泽", "🐸", false, "建议生命 ≥ 40000 / 回血 ≥ 50 / 攻击 ≥ 150",
                Map.of("maxHP", "40000", "REGEN", "50", "ATK", "150"),
                List.of(
                        mob("toad", "毒蟾", "🐸", "30000", "40", "20", "10", "10", 0.8, 3, 70, 120,
                                "双伤混合 + 毒伤，吃你的 REGEN", List.of("poison"))),
                mob("boss_toad", "毒蟾女王", "🐸", "250000", "110", "60", "20", "20", 0.9, 3, 65, 2500,
                        "剧毒之源，沉默毒雾弥漫", List.of("poison"))));

        m.put("snow", new ZoneDef("snow", "雪山", "🏔️", false, "建议生命 ≥ 100000 / 魔防 ≥ 30 / 攻击 ≥ 400",
                Map.of("maxHP", "100000", "MDEF", "30", "ATK", "400"),
                List.of(
                        mob("snowman", "雪人", "⛄", "80000", "120", "0", "30", "20", 0.6, 2, 60, 200,
                                "物攻凶猛，掉落冰晶和稀有装备", List.of()),
                        mob("icemage", "冰法", "🧙", "60000", "20", "180", "10", "80", 0.8, 7, 80, 180,
                                "魔伤 180 射程 7 步，检验你的魔防和射程", List.of())),
                mob("boss_yeti", "冰霜巨魔", "🧊", "600000", "250", "200", "50", "60", 0.8, 3, 55, 6000,
                        "极寒之主，冻结一切增益", List.of())));

        m.put("volcano", new ZoneDef("volcano", "火山", "🌋", false,
                "建议生命 ≥ 300000 / 双防成型 / 攻击 ≥ 1200（第 0 世毕业区）",
                Map.of("maxHP", "300000", "PDEF", "80", "MDEF", "80", "ATK", "1200"),
                List.of(
                        mob("lava", "熔岩兽", "🔥", "200000", "250", "80", "50", "50", 1.0, 2, 90, 300,
                                "双伤混合，掉落火晶和顶级图纸", List.of("burn")),
                        mob("inferno", "炎魔", "😈", "500000", "300", "400", "80", "80", 1.2, 5, 110, 500,
                                "第 0 世天花板，魔伤 400 射程 5", List.of("burn"))),
                mob("boss_ifrit", "炎狱魔王", "👹", "2000000", "500", "600", "100", "100", 1.1, 4, 100, 15000,
                        "炎狱之主，沉默之焰焚尽增益", List.of("burn"))));

        return java.util.Collections.unmodifiableMap(m);
    }

    /** Boss 沉默参数：每 silenceCd 秒触发一次，持续 silenceDur 秒。 */
    public static final double BOSS_SILENCE_CD = 15;
    public static final double BOSS_SILENCE_DUR = 30;

    // ==================== 技能 ====================

    public record SkillDef(String id, String name, String emoji, int mp, int cd, int dur, String desc) {
    }

    public static final Map<String, SkillDef> ACTIVE_SKILLS = Map.of(
            "heavy", new SkillDef("heavy", "蓄力重击", "💥", 20, 30, 0, "下一击 ×5 伤害"),
            "block", new SkillDef("block", "格挡反击", "🛡️", 25, 20, 3, "3s 内受击减半并反弹 50%"),
            "roar", new SkillDef("roar", "怒吼", "😤", 15, 60, 0, "吓退当前怪物（移速判定）"),
            "rapid", new SkillDef("rapid", "急速射击", "⚡", 40, 90, 5, "5s 内攻速 ×3"),
            "burn", new SkillDef("burn", "燃烧生命", "🔥", 0, 300, 300, "烧当前血 25%，300s 内攻击 ×2"),
            "surge", new SkillDef("surge", "魔力涌动", "✨", 50, 180, 10, "10s 内技能不耗蓝"));

    public record PassiveDef(String id, String name, String emoji, String desc) {
    }

    public static final Map<String, PassiveDef> PASSIVE_SKILLS = Map.of(
            "combo", new PassiveDef("combo", "连击", "👊", "15% 概率一拳变两拳"),
            "vitality", new PassiveDef("vitality", "健体", "💪", "每 100 次普攻额外 +50 生命上限"),
            "iron", new PassiveDef("iron", "铁布衫", "🛡️", "物防 ×1.2"),
            "resist", new PassiveDef("resist", "魔抗皮肤", "🔮", "魔防 ×1.2"),
            "swift", new PassiveDef("swift", "疾风步", "💨", "+20% 移速"),
            "hawkeye", new PassiveDef("hawkeye", "鹰眼", "🦅", "+5% 命中"),
            "brute", new PassiveDef("brute", "蛮力", "💢", "物攻 ×1.15，-3% 命中"),
            "element", new PassiveDef("element", "元素亲和", "⚡", "附魔魔伤 +25%"),
            "boss_hunter", new PassiveDef("boss_hunter", "巨人杀手", "🗡️", "对 Boss 伤害 +20%"));

    public static final double COMBO_CHANCE = 0.15;
    /** 被动「巨人杀手」对 Boss 的伤害乘区。 */
    public static final double BOSS_HUNTER_MUL = 1.2;
    /** 石肤药剂的承伤乘区（-10%）。 */
    public static final double STONE_POT_MUL = 0.9;
    /** 荆棘药剂的反弹比例（对实际承受伤害）。 */
    public static final double THORN_POT_MUL = 0.20;
    /** 巨人药剂对 Boss 的伤害乘区（与巨人杀手被动乘算叠加）。 */
    public static final double GIANT_POT_MUL = 1.15;
    public static final int VITALITY_INTERVAL = 100;
    public static final int VITALITY_BONUS = 50;

    /** 燃烧生命：烧当前血 25%（不伤上限）。 */
    public static final double BURN_HP_COST = 0.25;

    // ==================== 药剂 ====================

    public record PotionDef(String id, String name, String emoji, String desc,
                            double healHP, double healMP, String buff, double buffVal, double buffDur, int price) {
    }

    public static final Map<String, PotionDef> POTIONS = Map.ofEntries(
            java.util.Map.entry("hp_s", new PotionDef("hp_s", "小血药", "🧪", "瞬回 20% 生命上限", 0.20, 0, null, 0, 0, 50)),
            java.util.Map.entry("hp_l", new PotionDef("hp_l", "大血药", "🧪", "瞬回 50% 生命上限", 0.50, 0, null, 0, 0, 200)),
            java.util.Map.entry("mp", new PotionDef("mp", "蓝药", "🔵", "瞬回 40% 蓝量", 0, 0.40, null, 0, 0, 40)),
            java.util.Map.entry("food", new PotionDef("food", "兔肉", "🍖", "10s 内回血 +20", 0, 0, "regen_food", 20, 10, 10)),
            java.util.Map.entry("cure", new PotionDef("cure", "解毒剂", "💊", "清除中毒/灼烧/沉默", 0, 0, null, 0, 0, 30)),
            java.util.Map.entry("spd", new PotionDef("spd", "速度药剂", "💨", "30s 内攻速 +20%", 0, 0, "spd_pot", 0.20, 30, 100)),
            java.util.Map.entry("growth", new PotionDef("growth", "修行药剂", "🍵", "120s 内普攻成长 ×2", 0, 0, "growth_pot", 2.0, 120, 600)),
            java.util.Map.entry("ironpot", new PotionDef("ironpot", "铁壁药剂", "🛡️", "60s 内物防魔防 ×1.5", 0, 0, "iron_pot", 1.5, 60, 400)),
            // ---- v3.14 新增 10 种(商店扩容,配套 buff 见 cfg_buff/迁移 v3.14) ----
            java.util.Map.entry("crit", new PotionDef("crit", "锐眼药剂", "👁️", "90s 内暴击率 +10%", 0, 0, "crit_pot", 0.10, 90, 500)),
            java.util.Map.entry("critd", new PotionDef("critd", "残暴药剂", "🩸", "90s 内暴击伤害 +50%", 0, 0, "critd_pot", 0.50, 90, 550)),
            java.util.Map.entry("lifesteal", new PotionDef("lifesteal", "嗜血药剂", "🦇", "90s 内吸血 +10%", 0, 0, "lifesteal_pot", 0.10, 90, 450)),
            java.util.Map.entry("atk", new PotionDef("atk", "狂暴药剂", "⚔️", "60s 内攻击 ×1.5", 0, 0, "atk_pot", 1.5, 60, 800)),
            java.util.Map.entry("def", new PotionDef("def", "磐石药剂", "🪨", "60s 内物防魔防 ×1.5", 0, 0, "def_pot", 1.5, 60, 700)),
            java.util.Map.entry("aspd", new PotionDef("aspd", "疾风药剂", "🌪️", "60s 内攻速 ×1.5", 0, 0, "aspd_pot", 1.5, 60, 900)),
            java.util.Map.entry("acc", new PotionDef("acc", "明察药剂", "🎯", "120s 内命中 +10%", 0, 0, "acc_pot", 0.10, 120, 300)),
            java.util.Map.entry("eva", new PotionDef("eva", "幻影药剂", "🌫️", "120s 内闪避 +10%", 0, 0, "eva_pot", 0.10, 120, 350)),
            java.util.Map.entry("mregen", new PotionDef("mregen", "凝神药剂", "🧿", "120s 内回蓝 +10/秒", 0, 0, "mregen_pot", 10, 120, 250)),
            java.util.Map.entry("shield", new PotionDef("shield", "护盾药剂", "🛡", "30s 内获得 30% 生命上限的护盾", 0, 0, "shield_pot", 0.30, 30, 900)),
            // ---- 第四批扩充：回复系（特大回复 + 双回） ----
            java.util.Map.entry("hp_xl", new PotionDef("hp_xl", "特大血药", "❤️", "瞬回 100% 生命上限", 1.0, 0, null, 0, 0, 600)),
            java.util.Map.entry("mp_l", new PotionDef("mp_l", "大蓝药", "💙", "瞬回 100% 蓝量", 0, 1.0, null, 0, 0, 150)),
            java.util.Map.entry("elixir", new PotionDef("elixir", "万灵药剂", "🌈", "瞬回 80% 生命与 80% 蓝量", 0.80, 0.80, null, 0, 0, 800)),
            // ---- 第四批扩充：新维度增益（持续回复/射程/移速） ----
            java.util.Map.entry("regen", new PotionDef("regen", "生生药剂", "🌿", "120s 内回血 +25/秒", 0, 0, "regen_pot", 25, 120, 400)),
            java.util.Map.entry("far", new PotionDef("far", "远眺药剂", "🔭", "120s 内射程 +1", 0, 0, "rng_pot", 1, 120, 350)),
            java.util.Map.entry("fleet", new PotionDef("fleet", "疾行药剂", "👟", "120s 内移速 +40", 0, 0, "fleet_pot", 40, 120, 300)),
            // ---- 第四批扩充：收益维度（幸运） ----
            java.util.Map.entry("luck", new PotionDef("luck", "鸿运药剂", "🍀", "300s 内装备与图纸掉落率 ×1.5", 0, 0, "luck_pot", 1.5, 300, 500)),
            // ---- 第六批扩充：上限/防御/攻击/减伤/反弹/专伤/蓝上限/强回复 ----
            java.util.Map.entry("vital", new PotionDef("vital", "生命药剂", "💗", "120s 内生命上限 ×1.3", 0, 0, "vital_pot", 1.3, 120, 600)),
            java.util.Map.entry("guard", new PotionDef("guard", "金钟药剂", "🔔", "120s 内物防魔防 +15", 0, 0, "guard_pot", 15, 120, 500)),
            java.util.Map.entry("might", new PotionDef("might", "大力药剂", "🏋️", "120s 内物攻魔攻 +20", 0, 0, "might_pot", 20, 120, 500)),
            java.util.Map.entry("stone", new PotionDef("stone", "石肤药剂", "🗿", "120s 内受到的伤害 -10%", 0, 0, "stone_pot", 0.9, 120, 550)),
            java.util.Map.entry("thorn", new PotionDef("thorn", "荆棘药剂", "🌵", "120s 内反弹所受伤害的 20%", 0, 0, "thorn_pot", 0.2, 120, 650)),
            java.util.Map.entry("giant", new PotionDef("giant", "巨人药剂", "🦍", "180s 内对 Boss 伤害 +15%", 0, 0, "giant_pot", 1.15, 180, 700)));

    /** 跨界使用蓝药的蓝量上限保护：技能 MP 消耗按当前 MP 判定。 */
    public static final double MP_REGEN_BASE = 2;

    // ==================== 装备 ====================

    public record Affix(String name, String key, String type, double min, double max,
                        String rarity, String sideKey, Double sideVal) {
    }

    // 词条全部为 flat 直接加面板(玩家要求:百分比词条前期无感,改为直接数值);
    // 旧档里已生成的 pct 词条按原 type 兼容生效(PanelCalculator 按 type 分流)
    public static final List<Affix> AFFIXES = List.of(
            new Affix("锋利", "PATK", "flat", 4, 16, "common", null, null),
            new Affix("咒能", "MATK", "flat", 6, 24, "common", null, null),
            new Affix("坚韧", "REGEN", "flat", 1, 4, "common", null, null),
            new Affix("灵巧", "SPD", "flat", 10, 30, "common", null, null),
            new Affix("重甲", "PDEF", "flat", 6, 20, "common", "SPD", -0.03),
            new Affix("魔纹", "MDEF", "flat", 6, 18, "common", null, null),
            new Affix("鹰眼", "ACC", "flat", 0.03, 0.08, "uncommon", null, null),
            new Affix("蓝泉", "MREGEN", "flat", 1, 3, "uncommon", null, null),
            new Affix("致命", "CRIT", "flat", 0.03, 0.15, "rare", null, null),
            new Affix("残忍", "CRITD", "flat", 0.10, 0.50, "rare", null, null),
            new Affix("吸血回春", "LIFESTEAL", "flat", 0.005, 0.03, "rare", null, null),
            new Affix("迅捷", "ASPD", "flat", 0.02, 0.10, "epic", null, null),
            new Affix("法力洪流", "MP", "flat", 100, 500, "rare", null, null),
            new Affix("延伸", "RNG", "flat", 1, 2, "epic", null, null),
            // 第四批新增：闪避（此前只有药剂能加）与生命上限（此前任何词条都不加）
            new Affix("轻盈", "EVA", "flat", 0.02, 0.10, "uncommon", null, null),
            new Affix("活力", "maxHP", "flat", 50, 300, "rare", null, null));

    /** 掉落概率（按区域）。 */
    public static final Map<String, Double> DROP_RATE = Map.of(
            "village", 0.0, "plain", 0.30, "forest", 0.35, "mine", 0.45,
            "swamp", 0.55, "snow", 0.65, "volcano", 0.75);

    /** 品质档位：词条数量。 */
    public static final int[] QUALITY_AFFIX_COUNT = {1, 2, 3, 4};
    public static final String[] QUALITY_NAME = {"普通", "精良", "稀有", "史诗"};

    /** 背包基础格数（DESIGN.md §9：初始 40 格；负重腰带 +20 动态扩容）。 */
    public static final int BAG_MAX = 100;   // 2026-10-31 玩家要求 30/40→100(挂机自动卖档位细化后,背包更耐用)
    /** 视图下发背包截断上限(v3.13):3.3 万件全量下发=13.9MB JSON 每秒轮询,前端卡死实锤。 */
    public static final int BAG_VIEW_LIMIT = 200;
    public static final int ENHANCE_MAX_LV = 10;
    public static final double ENHANCE_STAT_BONUS = 0.05;
    public static final int ENHANCE_COST_BASE = 100;
    public static final double ENHANCE_COST_MUL = 1.5;

    public static final List<String> EQUIP_SLOTS = List.of(
            "weapon", "offhand", "bow", "head", "chest", "legs", "boots", "ring1", "ring2", "necklace");

    // ==================== 离线 ====================

    public static final int OFFLINE_CAP_HOURS = 8;
    public static final double OFFLINE_EFF = 0.5;
    public static final long OFFLINE_MIN_SEC = 60;

    // ==================== 天赋 ====================

    public record TalentDef(String id, String name, String emoji, String desc) {
    }

    /** 觉醒天赋池（新档第一次进入游戏抽一次，转生保留）。 */
    public static final Map<String, TalentDef> TALENTS = Map.of(
            "eternal", new TalentDef("eternal", "被动永续", "♾️", "所有增益被动永不消逝"),
            "baglord", new TalentDef("baglord", "无限装备栏", "🧳", "无相位装备栏无限：任何装备都能穿上，属性全部叠加"),
            "statlord", new TalentDef("statlord", "无限属性点", "💠", "每杀一怪 +1 自由属性点：属性面板自由加点"),
            "might", new TalentDef("might", "天生神力", "💪", "物攻 +10%"),
            "shell", new TalentDef("shell", "铜皮铁骨", "🐢", "物防 +10%、魔防 +10%"),
            "wind", new TalentDef("wind", "疾风之躯", "💨", "移速 +15%"),
            "goldhand", new TalentDef("goldhand", "点金手", "🪙", "击杀金币 +20%"),
            "master", new TalentDef("master", "武学奇才", "📕", "主动技能冷却 -15%"));

    /** 顶级机缘(被动永续/无限装备栏/无限属性点)抽中概率各 30%;其余天赋平分剩余 10%。 */
    public static final double TOP_TALENT_RATE = 0.30;

    /** 无限属性点天赋:每 1 自由属性点对属性的增量(单位与面板一致;暴击/命中/闪避为小数概率)。 */
    public static final Map<String, BigDecimal> STAT_POINT_STEP = Map.ofEntries(
            Map.entry("PATK", new BigDecimal("1")),
            Map.entry("MATK", new BigDecimal("1")),
            Map.entry("PDEF", new BigDecimal("1")),
            Map.entry("MDEF", new BigDecimal("1")),
            Map.entry("ASPD", new BigDecimal("0.05")),
            Map.entry("RNG", new BigDecimal("0.5")),
            Map.entry("SPD", new BigDecimal("5")),
            Map.entry("CRIT", new BigDecimal("0.005")),
            Map.entry("CRITD", new BigDecimal("0.05")),
            Map.entry("ACC", new BigDecimal("0.005")),
            Map.entry("EVA", new BigDecimal("0.005")),
            Map.entry("REGEN", new BigDecimal("0.5")),
            Map.entry("MREGEN", new BigDecimal("0.5")),
            Map.entry("LIFESTEAL", new BigDecimal("0.002")));

    /** 区域材料（DESIGN.md §9：皮毛/狼牙/矿石/毒腺/魔尘/火晶）。 */
    public static final Map<String, String> ZONE_MAT = Map.of(
            "plain", "pelt", "forest", "wolf_fang", "mine", "ore",
            "swamp", "toad_gland", "snow", "mana_dust", "volcano", "fire_crystal");

    /** 附魔卷轴（DESIGN.md §6.2/§8）：给武器/弓附加魔伤词条，火/冰/雷三系。 */
    public static final Map<String, Object[]> SCROLLS = Map.of(
            "scroll_fire", new Object[]{"火之卷轴", "🔥", 15},
            "scroll_ice", new Object[]{"冰之卷轴", "❄️", 12},
            "scroll_thunder", new Object[]{"雷之卷轴", "⚡", 20});

    /** 掉附魔卷轴的怪物 id → 卷轴 code。 */
    public static final Map<String, String> SCROLL_DROP = Map.of(
            "icemage", "scroll_ice", "lava", "scroll_fire",
            "inferno", "scroll_fire", "bats", "scroll_thunder");

    /** 材料展示信息：code → [名称, emoji]。 */
    public static final Map<String, String[]> MAT_INFO = Map.of(
            "pelt", new String[]{"兽皮", "🟫"},
            "wolf_fang", new String[]{"狼牙", "🦷"},
            "ore", new String[]{"矿石", "⛏️"},
            "toad_gland", new String[]{"毒腺", "🟢"},
            "mana_dust", new String[]{"魔尘", "✨"},
            "fire_crystal", new String[]{"火晶", "🔥"});

    /** 增益类 buff（永久存续天赋作用对象）。 */
    public static final Set<String> ETERNAL_BUFFS = Set.of(
            "burn", "block", "rapid", "surge", "regen_food", "spd_pot", "growth_pot", "iron_pot");
}
