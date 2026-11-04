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

    public static final Map<String, TraitDef> MOB_TRAITS = Map.ofEntries(
            Map.entry("frenzy", new TraitDef("frenzy", "狂暴", "💢", "生命低于 30% 时攻速 +50%，小心它临死反扑")),
            Map.entry("sunder", new TraitDef("sunder", "破甲", "🪓", "攻击无视你 30% 的物防与魔防")),
            Map.entry("disarm", new TraitDef("disarm", "缴械", "🔗", "压制你的武器，你的普攻伤害 -15%")),
            Map.entry("leech", new TraitDef("leech", "嗜血", "🩸", "命中你时吸回所造成伤害 30% 的生命，拖得越久它越难杀")),
            Map.entry("thorns", new TraitDef("thorns", "荆棘", "🌵", "你打它时被反扎：所造成伤害的 10% 反弹给你，出手越重扎得越狠")),
            Map.entry("rich", new TraitDef("rich", "富饶", "💰", "行走的钱袋子：击杀金币 ×3，它不伤人，只散财")),
            Map.entry("swift", new TraitDef("swift", "疾影", "⚡", "出手如风：攻速 +50%，别被它连绵的攻势磨死")),
            Map.entry("bulwark", new TraitDef("bulwark", "堡垒", "🛡️", "皮糙肉厚如堡垒：你对它造成的伤害 -20%，得靠耐心磨")),
            Map.entry("venom", new TraitDef("venom", "剧毒", "☠️", "獠牙带毒：命中可能让你中毒持续掉血，出门记得带解毒剂")),
            Map.entry("crystal", new TraitDef("crystal", "晶化", "💎", "通体晶化：击杀必额外掉 2 份本区材料，锻造党的福星")),
            Map.entry("stealth", new TraitDef("stealth", "潜行", "🌫️", "身形飘忽：你的攻击屡屡落空，对它的伤害 -10%，堆命中克它")),
            Map.entry("slow", new TraitDef("slow", "时滞", "🐌", "黏液缠身：命中可能让你陷入时滞 4 秒，出拳速度 -20%，速战速决或备好净化")),
            Map.entry("curse", new TraitDef("curse", "诅咒", "💀", "阴气蚀体：与它交战时你的一切回血（回复/吸血/喝药）减半，先解决它再续航")),
            Map.entry("frost", new TraitDef("frost", "寒霜", "🧊", "寒气侵体：命中让你寒霜附体 5 秒、出拳速度 -15%，解毒剂与净化可解")),
            Map.entry("regen", new TraitDef("regen", "再生", "🌿", "生机旺盛：交战中每秒回复 3% 生命上限，拖得越久越难杀，爆发速杀克它")),
            Map.entry("tenacity", new TraitDef("tenacity", "坚韧", "🪨", "心志如磐：你的暴击很难撼动它，暴击率对它减半，堆面板平推克它")));

    /** 词条「再生」（第八批）：怪物每秒回复上限的比例。 */
    public static final double TRAIT_REGEN_PCT = 0.03;
    /** 词条「坚韧」（第八批）：玩家暴击率对它乘区。 */
    public static final double TRAIT_TENACITY_CRIT_MUL = 0.5;

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
    /** 诅咒的回血乘区（回复/吸血/喝药同口径减半）。 */
    public static final double TRAIT_CURSE_HEAL_MUL = 0.5;
    /** 富饶的金币倍率（击杀结算乘区）。 */
    public static final double TRAIT_RICH_GOLD_MUL = 3.0;
    /** 疾影的攻速倍率（常驻乘区，与狂暴残血加速同口径叠加）。 */
    public static final double TRAIT_SWIFT_HASTE = 1.5;
    /** 堡垒的承伤乘区（玩家对它造成的伤害 ×0.8）。 */
    public static final double TRAIT_BULWARK_MUL = 0.8;
    /** 剧毒上毒时长（秒；物种毒为 10 秒，词条毒略短）。 */
    public static final double TRAIT_VENOM_DUR = 6.0;
    /** 晶化击杀额外材料份数。 */
    public static final int TRAIT_CRYSTAL_MATS = 2;
    /** 潜行的伤害乘区（攻击落空等价：玩家对它造成的伤害 ×0.9）。 */
    public static final double TRAIT_STEALTH_MUL = 0.9;
    /** 时滞：命中上时滞的每秒概率（与剧毒同口径）/ 时滞时长（秒）/ 时滞时攻速倍率。 */
    public static final double TRAIT_SLOW_CHANCE = 0.3;
    public static final double TRAIT_SLOW_DUR = 4.0;
    public static final double TRAIT_SLOW_ASPD_MUL = 0.8;
    /** 寒霜：命中上霜概率（/秒）/持续秒数/攻速乘区（与时滞同机制、不同 buff 键，可叠加）。 */
    public static final double TRAIT_FROST_CHANCE = 0.3;
    public static final double TRAIT_FROST_DUR = 5.0;
    public static final double TRAIT_FROST_ASPD_MUL = 0.85;
    /** 被动「幸运星」的掉落率乘区。 */
    public static final double LUCKY_STAR_MUL = 1.1;
    /** 主动「治愈术」的即时回复比例（按生命上限）。 */
    public static final double HEAL_SKILL_RATIO = 0.35;
    /** 材料兑换汇率：N 份源材料换 1 份目标材料。 */
    public static final int MAT_EXCHANGE_COST = 3;

    /** 区域由浅到深的顺序（与 buildZones 的插入顺序一致）。 */
    private static final List<String> ZONE_ORDER =
            List.of("village", "plain", "forest", "mine", "swamp", "snow", "volcano", "abyss", "star");

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
        List<String> pool = traitPool(idx);
        return pool.get((int) (Math.random() * pool.size()));
    }

    /** 词条池（按区域深度解锁）：平原仅狂暴，深区逐步解锁至全 11 条。供单测锁定解锁线。 */
    static List<String> traitPool(int zoneIdx) {
        return zoneIdx == 1
                ? List.of("frenzy")
                : zoneIdx == 2
                        ? List.of("frenzy", "sunder", "rich", "crystal")
                        : zoneIdx == 3
                                ? List.of("frenzy", "sunder", "disarm", "rich", "swift", "venom", "crystal")
                                : zoneIdx == 4
                                        ? List.of("frenzy", "sunder", "disarm", "leech", "rich", "swift", "venom", "crystal", "stealth", "slow", "curse", "regen")
                                        : List.of("frenzy", "sunder", "disarm", "leech", "thorns", "rich", "swift", "bulwark", "venom", "crystal", "stealth", "slow", "curse", "frost", "regen", "tenacity");
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

        // 深渊裂隙（第七批）：火山之下的裂隙，跨世毕业区——双防与双攻都要成型才站得住
        m.put("abyss", new ZoneDef("abyss", "深渊裂隙", "🕳️", false,
                "建议生命 ≥ 200万 / 双防 ≥ 150 / 攻击 ≥ 4000（跨世毕业区）",
                Map.of("maxHP", "2000000", "PDEF", "150", "MDEF", "150", "ATK", "4000"),
                List.of(
                        mob("abyss_wisp", "虚空游魂", "👻", "1500000", "100", "700", "60", "160", 0.9, 6, 95, 1200,
                                "无形游魂，魔伤穿防，掉落深渊结晶", List.of()),
                        mob("rift_hound", "裂隙猎犬", "🐺", "2500000", "900", "0", "150", "80", 1.4, 2, 140, 1500,
                                "攻速极快移速如电，獠牙撕裂空间", List.of()),
                        mob("void_knight", "虚空骑士", "♞", "4000000", "800", "500", "220", "220", 0.8, 3, 80, 2200,
                                "双防如壁、双伤如潮，裂隙的守门人", List.of()),
                        mob("chaos_spawn", "混沌魔胎", "🦑", "6000000", "600", "1100", "120", "260", 1.0, 7, 85, 3000,
                                "魔伤滔天射程极远，混沌在裂隙深处胎动", List.of())),
                mob("boss_abyss", "渊底之主·无光君王", "🕳️", "30000000", "1500", "1800", "300", "300", 1.2, 5, 100, 60000,
                        "裂隙最深处的主人，沉默如渊，万光不入", List.of())));

        // 星界（第八批）：深渊之上的群星回廊——当前版本终局之终局
        m.put("star", new ZoneDef("star", "星界", "🌌", false,
                "建议生命 ≥ 2000万 / 双防 ≥ 400 / 攻击 ≥ 12000（群星回廊）",
                Map.of("maxHP", "20000000", "PDEF", "400", "MDEF", "400", "ATK", "12000"),
                List.of(
                        mob("star_sprite", "星尘精灵", "✨", "12000000", "300", "2000", "120", "400", 1.1, 6, 100, 4000,
                                "星尘凝成的精灵，魔光如雨，掉落星核碎片", List.of()),
                        mob("void_walker", "虚空行者", "🚶", "20000000", "2400", "0", "380", "200", 1.5, 2, 145, 5000,
                                "踏虚而行，出手快到只剩残影", List.of()),
                        mob("star_guard", "星核守卫", "🛡️", "32000000", "2000", "1400", "550", "550", 0.8, 3, 85, 7000,
                                "双防如星核般致密，回廊的执盾者", List.of()),
                        mob("star_eater", "噬星巨兽", "🦖", "48000000", "1600", "2800", "300", "650", 1.0, 7, 90, 9500,
                                "以星辰为食的巨兽，魔焰滔天射程极远", List.of())),
                mob("boss_star", "星核主宰·噬光之心", "🌌", "300000000", "3800", "4500", "750", "750", 1.2, 5, 100, 200000,
                        "群星回廊的尽头，它的心跳即是星辰的熄灭", List.of())));

        return java.util.Collections.unmodifiableMap(m);
    }

    /** Boss 沉默参数：每 silenceCd 秒触发一次，持续 silenceDur 秒。 */
    public static final double BOSS_SILENCE_CD = 15;
    public static final double BOSS_SILENCE_DUR = 30;

    // ==================== 技能 ====================

    public record SkillDef(String id, String name, String emoji, int mp, int cd, int dur, String desc) {
    }

    public static final Map<String, SkillDef> ACTIVE_SKILLS = Map.ofEntries(
            Map.entry("heavy", new SkillDef("heavy", "蓄力重击", "💥", 20, 30, 0, "下一击 ×5 伤害")),
            Map.entry("block", new SkillDef("block", "格挡反击", "🛡️", 25, 20, 3, "3s 内受击减半并反弹 50%")),
            Map.entry("roar", new SkillDef("roar", "怒吼", "😤", 15, 60, 0, "吓退当前怪物（移速判定）")),
            Map.entry("rapid", new SkillDef("rapid", "急速射击", "⚡", 40, 90, 5, "5s 内攻速 ×3")),
            Map.entry("burn", new SkillDef("burn", "燃烧生命", "🔥", 0, 300, 300, "烧当前血 25%，300s 内攻击 ×2")),
            Map.entry("surge", new SkillDef("surge", "魔力涌动", "✨", 50, 180, 10, "10s 内技能不耗蓝")),
            Map.entry("heal", new SkillDef("heal", "治愈术", "💚", 40, 60, 0, "立即恢复 35% 生命上限")),
            Map.entry("bloodlust", new SkillDef("bloodlust", "嗜血术", "🩸", 30, 45, 10, "10s 内吸血 +20%")),
            Map.entry("iron_wall", new SkillDef("iron_wall", "铁壁", "🧱", 35, 40, 8, "8s 内受击减半")),
            Map.entry("cleanse", new SkillDef("cleanse", "净化", "🕊️", 20, 30, 0, "清除中毒/灼烧/沉默/时滞/寒霜全部负面状态")),
            Map.entry("charge", new SkillDef("charge", "冲锋", "🐎", 15, 20, 0, "下一击 ×3 伤害")),
            Map.entry("hex_slow", new SkillDef("hex_slow", "迟缓咒", "🐌", 20, 25, 8, "当前怪物攻速 -30%，持续 8 秒")),
            Map.entry("flurry", new SkillDef("flurry", "连环掌", "🤲", 25, 30, 0, "接下来 30 次命中各追加一次同额命中（双击）")));

    /** 主动「连环掌」（第八批）：每次施放赋予的双击次数。 */
    public static final int FLURRY_HITS = 30;

    /** 主动「迟缓咒」（第七批）：怪物攻速乘区（MobState.slowT 计时）。 */
    public static final double HEX_SLOW_ASPD_MUL = 0.7;

    public record PassiveDef(String id, String name, String emoji, String desc) {
    }

    public static final Map<String, PassiveDef> PASSIVE_SKILLS = Map.ofEntries(
            Map.entry("combo", new PassiveDef("combo", "连击", "👊", "15% 概率一拳变两拳")),
            Map.entry("vitality", new PassiveDef("vitality", "健体", "💪", "每 100 次普攻额外 +50 生命上限")),
            Map.entry("iron", new PassiveDef("iron", "铁布衫", "🛡️", "物防 ×1.2")),
            Map.entry("resist", new PassiveDef("resist", "魔抗皮肤", "🔮", "魔防 ×1.2")),
            Map.entry("swift", new PassiveDef("swift", "疾风步", "💨", "+20% 移速")),
            Map.entry("hawkeye", new PassiveDef("hawkeye", "鹰眼", "🦅", "+5% 命中")),
            Map.entry("brute", new PassiveDef("brute", "蛮力", "💢", "物攻 ×1.15，-3% 命中")),
            Map.entry("element", new PassiveDef("element", "元素亲和", "⚡", "附魔魔伤 +25%")),
            Map.entry("boss_hunter", new PassiveDef("boss_hunter", "巨人杀手", "🗡️", "对 Boss 伤害 +20%")),
            Map.entry("diamond_body", new PassiveDef("diamond_body", "金刚体", "💎", "物防与魔防 ×1.15")),
            Map.entry("lucky_star", new PassiveDef("lucky_star", "幸运星", "⭐", "装备与图纸掉落率 +10%")),
            Map.entry("eagle_bone", new PassiveDef("eagle_bone", "鹰骨", "🦴", "射程 +1")),
            Map.entry("material_sense", new PassiveDef("material_sense", "材料嗅觉", "👃", "击杀材料掉率 +25 个百分点（50%→75%），锻造党的鼻子")),
            Map.entry("bloodrage", new PassiveDef("bloodrage", "血怒", "🩸", "生命低于 30% 时物理与魔法攻击 +15%，越残越怒")),
            Map.entry("executioner", new PassiveDef("executioner", "处决者", "🪓", "Boss 生命低于 30% 时对其伤害 +25%，斩尽杀绝")),
            Map.entry("armor_pierce", new PassiveDef("armor_pierce", "破甲专精", "🏹", "攻击无视目标 20% 的物防与魔防，厚甲在你面前如薄纸")));

    public static final double COMBO_CHANCE = 0.15;

    /** 洗炼保底（第七批）：连续 REROLL_PITY_MAX 次洗炼未出史诗词条时，第 REROLL_PITY_MAX 次必出一条。 */
    public static final int REROLL_PITY_MAX = 10;
    /** 被动「巨人杀手」对 Boss 的伤害乘区。 */
    public static final double BOSS_HUNTER_MUL = 1.2;
    /** 被动「处决者」（第七批）：Boss 血线（当前/上限）低于此值触发，伤害乘区。 */
    public static final double EXECUTIONER_HP_LINE = 0.30;
    public static final double EXECUTIONER_MUL = 1.25;
    /** 被动「破甲专精」（第八批）：无视目标防御的比例（防御项 ×(1−此值)）。 */
    public static final double ARMOR_PIERCE_RATIO = 0.20;
    /** 被动「血怒」（第七批）：触发血线（当前生命/上限）与双攻乘区。 */
    public static final double BLOODRAGE_HP_LINE = 0.30;
    public static final double BLOODRAGE_ATK_MUL = 1.15;
    /** 主动「冲锋」（第七批）：下一击伤害乘区（一次性消费 buff，与蓄力重击同机制）。 */
    public static final double CHARGE_MUL = 3.0;
    /** Boss 二阶段（第七批）：触发血线与攻速/攻击乘区。 */
    public static final double BOSS_PHASE2_HP_LINE = 0.5;
    public static final double BOSS_PHASE2_ASPD_MUL = 1.3;
    public static final double BOSS_PHASE2_ATK_MUL = 1.2;
    /** 被动「材料嗅觉」的材料掉率加成（百分点，叠加在基础 50% 上）。 */
    public static final double MAT_SENSE_BONUS = 0.25;
    /** 击杀基础材料掉率。 */
    public static final double MAT_BASE_CHANCE = 0.5;
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
            java.util.Map.entry("giant", new PotionDef("giant", "巨人药剂", "🦍", "180s 内对 Boss 伤害 +15%", 0, 0, "giant_pot", 1.15, 180, 700)),
            java.util.Map.entry("spirit", new PotionDef("spirit", "聚灵药剂", "🔮", "120s 内蓝量上限 +300", 0, 0, "spirit_pot", 300, 120, 450)),
            java.util.Map.entry("regen2", new PotionDef("regen2", "回春药剂", "🌸", "60s 内回血 +100/秒", 0, 0, "regen_pot_l", 100, 60, 650)),
            java.util.Map.entry("fortune", new PotionDef("fortune", "聚宝药剂", "🧧", "300s 内击杀金币 +50%", 0, 0, "fortune_pot", 1.5, 300, 800)),
            // ---- 第七批扩充：复合暴击（狂怒）/ 平直攻速（时之砂） ----
            java.util.Map.entry("rage", new PotionDef("rage", "狂怒药剂", "😤", "90s 内暴击率 +10%、暴击伤害 +30%", 0, 0, "rage_pot", 0.10, 90, 750)),
            java.util.Map.entry("timesand", new PotionDef("timesand", "时之砂", "⌛", "120s 内攻速 +0.2（平直加算，吃攻速乘区）", 0, 0, "sand_pot", 0.2, 120, 800)),
            // ---- 第八批扩充：破甲（与破甲专精被动乘算） ----
            java.util.Map.entry("pierce", new PotionDef("pierce", "破甲药剂", "🏹", "60s 内攻击无视目标 20% 防御", 0, 0, "pierce_pot", 0.2, 60, 600)),
            java.util.Map.entry("focus", new PotionDef("focus", "鹰目药剂", "🎯", "90s 内命中 +12%", 0, 0, "focus_pot", 0.12, 90, 500)),
            java.util.Map.entry("vigor", new PotionDef("vigor", "生机药剂", "🌿", "60s 内每秒回复 +150", 0, 0, "vigor_pot", 150, 60, 550)));

    /** 破甲药剂（第八批）：无视防御比例（与破甲专精被动在 effDef 内乘算叠加）。 */
    public static final double PIERCE_POT_RATIO = 0.20;

    /** 聚宝药剂的金币倍率（与点金手天赋乘算）。 */
    public static final double FORTUNE_POT_MUL = 1.5;

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
            new Affix("活力", "maxHP", "flat", 50, 300, "rare", null, null),
            // ---- 第六批：高阶词条（既有维度上位版，史诗池） ----
            new Affix("血契", "LIFESTEAL", "flat", 0.03, 0.06, "epic", null, null),
            new Affix("磐石", "PDEF", "flat", 20, 45, "epic", null, null),
            new Affix("猎风", "SPD", "flat", 30, 60, "epic", null, null),
            new Affix("生命之源", "maxHP", "flat", 300, 800, "epic", null, null),
            new Affix("魔力源泉", "MP", "flat", 500, 1200, "epic", null, null),
            new Affix("疾雷", "ASPD", "flat", 0.10, 0.20, "epic", null, null),
            // ---- 第七批：攻击维度上位（锋利/致命的史诗版） ----
            new Affix("破军", "PATK", "flat", 16, 40, "epic", null, null),
            new Affix("绝杀", "CRIT", "flat", 0.15, 0.25, "epic", null, null));

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
    /** 淬炼（第七批）：与强化独立的第二成长线，满 5 级，底子 +4%/级（成本属性不吃乘区，同强化口径）。 */
    public static final int TEMPER_MAX_LV = 5;
    public static final double TEMPER_STAT_BONUS = 0.04;

    // ==================== 宝石镶嵌（第八批） ====================

    /** 宝石定义：stats 为 flat 加成（与装备词条同区，属装备来源、不受沉默压制）。 */
    public record GemDef(String id, String name, String emoji, Map<String, BigDecimal> stats, String desc) {
    }

    /** 每件装备的镶嵌孔上限。 */
    public static final int GEM_SOCKET_MAX = 3;
    /** 词条怪掉落宝石概率（Boss 必掉，见 GameEngine 击杀漏斗）。 */
    public static final double GEM_TRAIT_DROP_RATE = 0.10;
    /** 宝石出售单价（第八批，金币/颗）：率型宝石（暴击/攻速）最贵；二阶为一阶 ×3。 */
    public static final Map<String, Integer> GEM_SELL_PRICE = Map.ofEntries(
            Map.entry("gem_ruby", 500), Map.entry("gem_sapphire", 500), Map.entry("gem_emerald", 600),
            Map.entry("gem_opal", 700), Map.entry("gem_topaz", 800), Map.entry("gem_amethyst", 800),
            Map.entry("gem_ruby_2", 1500), Map.entry("gem_sapphire_2", 1500), Map.entry("gem_emerald_2", 1800),
            Map.entry("gem_opal_2", 2100), Map.entry("gem_topaz_2", 2400), Map.entry("gem_amethyst_2", 2400));

    /**
     * 宝石全表（一阶 6 种 + 璀璨二阶 6 种）：一阶由 Boss/词条怪掉落；
     * 二阶由 3 颗同种一阶融合（属性 ×2.5），code 为一阶 code + "_2"，不进掉落池。
     */
    public static final Map<String, GemDef> GEMS = buildGems();

    private static Map<String, GemDef> buildGems() {
        Map<String, GemDef> m = new java.util.LinkedHashMap<>();
        m.put("gem_ruby", new GemDef("gem_ruby", "红宝石", "🔺", Map.of("PATK", new BigDecimal("60")), "物攻 +60"));
        m.put("gem_sapphire", new GemDef("gem_sapphire", "蓝宝石", "🔷", Map.of("MATK", new BigDecimal("60")), "魔攻 +60"));
        m.put("gem_emerald", new GemDef("gem_emerald", "翡翠", "💚", Map.of("maxHP", new BigDecimal("3000")), "生命上限 +3000"));
        m.put("gem_topaz", new GemDef("gem_topaz", "黄玉", "💛", Map.of("CRIT", new BigDecimal("0.03")), "暴击率 +3%"));
        m.put("gem_amethyst", new GemDef("gem_amethyst", "紫水晶", "💜", Map.of("ASPD", new BigDecimal("0.12")), "攻速 +0.12"));
        m.put("gem_opal", new GemDef("gem_opal", "蛋白石", "⚪",
                Map.of("PDEF", new BigDecimal("40"), "MDEF", new BigDecimal("40")), "双防各 +40"));
        for (GemDef t1 : new java.util.ArrayList<>(m.values())) {
            Map<String, BigDecimal> stats = new java.util.LinkedHashMap<>();
            t1.stats().forEach((k, v) -> stats.put(k, v.multiply(new BigDecimal("2.5"))));
            StringBuilder sb = new StringBuilder();
            stats.forEach((k, v) -> {
                if (sb.length() > 0) {
                    sb.append("、");
                }
                sb.append(k).append(" +").append(v.stripTrailingZeros().toPlainString());
            });
            m.put(t1.id() + "_2", new GemDef(t1.id() + "_2", "璀璨" + t1.name(), "💎", Map.copyOf(stats),
                    "融合宝石：" + sb));
        }
        return java.util.Collections.unmodifiableMap(m);
    }

    /** 一阶宝石 id 列表（掉落池口径，二阶不进池）。 */
    public static java.util.List<String> gemTier1Ids() {
        return GEMS.keySet().stream().filter(id -> !id.endsWith("_2")).toList();
    }
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
            "master", new TalentDef("master", "武学奇才", "📕", "主动技能冷却 -15%"),
            "lucky", new TalentDef("lucky", "幸运儿", "🍀", "装备与图纸掉落率 +15%"),
            "iron_gut", new TalentDef("iron_gut", "铁胃", "🍲", "食物回血效果 +50%、持续时间 +50%"));

    /** 天赋「幸运儿」（第七批）：掉落率乘区（与鸿运药剂/幸运星被动乘算）。 */
    public static final double LUCKY_TALENT_MUL = 1.15;
    /** 天赋「铁胃」（第七批）：食物（regen_food）效果与时长乘区。 */
    public static final double IRON_GUT_MUL = 1.5;
    /** 饱食回血基础强度（/秒），铁胃时 ×IRON_GUT_MUL。 */
    public static final double FOOD_REGEN = 20;

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
    public static final Map<String, String> ZONE_MAT = Map.ofEntries(
            Map.entry("plain", "pelt"), Map.entry("forest", "wolf_fang"), Map.entry("mine", "ore"),
            Map.entry("swamp", "toad_gland"), Map.entry("snow", "mana_dust"), Map.entry("volcano", "fire_crystal"),
            Map.entry("abyss", "abyss_crystal"), Map.entry("star", "star_shard"));

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
    public static final Map<String, String[]> MAT_INFO = Map.ofEntries(
            Map.entry("pelt", new String[]{"兽皮", "🟫"}),
            Map.entry("wolf_fang", new String[]{"狼牙", "🦷"}),
            Map.entry("ore", new String[]{"矿石", "⛏️"}),
            Map.entry("toad_gland", new String[]{"毒腺", "🟢"}),
            Map.entry("mana_dust", new String[]{"魔尘", "✨"}),
            Map.entry("fire_crystal", new String[]{"火晶", "🔥"}),
            Map.entry("abyss_crystal", new String[]{"深渊结晶", "🕳️"}),
            Map.entry("star_shard", new String[]{"星核碎片", "🌟"}),
            Map.entry("gem_ruby", new String[]{"红宝石", "🔺"}),
            Map.entry("gem_sapphire", new String[]{"蓝宝石", "🔷"}),
            Map.entry("gem_emerald", new String[]{"翡翠", "💚"}),
            Map.entry("gem_topaz", new String[]{"黄玉", "💛"}),
            Map.entry("gem_amethyst", new String[]{"紫水晶", "💜"}),
            Map.entry("gem_opal", new String[]{"蛋白石", "⚪"}),
            Map.entry("gem_ruby_2", new String[]{"璀璨红宝石", "💎"}),
            Map.entry("gem_sapphire_2", new String[]{"璀璨蓝宝石", "💎"}),
            Map.entry("gem_emerald_2", new String[]{"璀璨翡翠", "💎"}),
            Map.entry("gem_topaz_2", new String[]{"璀璨黄玉", "💎"}),
            Map.entry("gem_amethyst_2", new String[]{"璀璨紫水晶", "💎"}),
            Map.entry("gem_opal_2", new String[]{"璀璨蛋白石", "💎"}));

    /** 材料出售单价（第七批，金币/份）：越深的区域材料越值钱；仅区域材料可售（图纸/卷轴不可）。 */
    public static final Map<String, Integer> MAT_SELL_PRICE = Map.ofEntries(
            Map.entry("pelt", 20), Map.entry("wolf_fang", 35), Map.entry("ore", 60), Map.entry("toad_gland", 90),
            Map.entry("mana_dust", 140), Map.entry("fire_crystal", 220), Map.entry("abyss_crystal", 400),
            Map.entry("star_shard", 650));

    /** 增益类 buff（永久存续天赋作用对象）。 */
    public static final Set<String> ETERNAL_BUFFS = Set.of(
            "burn", "block", "rapid", "surge", "regen_food", "spd_pot", "growth_pot", "iron_pot");
}
