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
            Map.entry("tenacity", new TraitDef("tenacity", "坚韧", "🪨", "心志如磐：你的暴击很难撼动它，暴击率对它减半，堆面板平推克它")),
            Map.entry("star_eclipse", new TraitDef("star_eclipse", "星蚀", "🌑", "星光被吞噬：你的暴击威力大减（暴伤超额部分减半），只在星界出现，靠平推与攻速克它")),
            Map.entry("mana_drain", new TraitDef("mana_drain", "噬能", "🔋", "命中你时吞噬你的法力：按所造成伤害的 10% 抽蓝，蓝量见底的法师会被它活活耗死")),
            Map.entry("void_rend", new TraitDef("void_rend", "裂空", "✴️", "攻击撕裂空间：你的闪避对它减半，只在虚空界出现，身法流在它面前无处可躲")),
            Map.entry("blood_fury", new TraitDef("blood_fury", "燃血", "🩸", "血越少越疯：攻击随自身已损失生命放大，残血时 +60%，拖得越久越危险，速杀或留爆发收尾")),
            Map.entry("mirror", new TraitDef("mirror", "镜像", "🪞", "以彼之道：反弹你造成伤害的 10%（按它实际承受封顶，与荆棘同口径），高爆发慎打，先叠护盾或吸血克它")),
            Map.entry("weaken", new TraitDef("weaken", "虚弱", "🥀", "无序低语侵蚀你的气力：你的普攻伤害 -15%（与缴械同乘区），堆面板硬碾克它")),
            Map.entry("chaos_frenzy", new TraitDef("chaos_frenzy", "狂乱", "🌀", "无序狂乱：攻速 +40%、攻击 +20% 常驻，只在混沌界出现，时停与迟缓咒叠加压制克它")),
            Map.entry("immortal", new TraitDef("immortal", "不朽", "💀", "不朽之躯：首次受到致死伤害时不死，保留 30% 生命复苏（每只仅一次），留爆发二段收尾克它")),
            Map.entry("devour", new TraitDef("devour", "吞噬", "👹", "吞噬血肉：攻击时额外吞噬你 2% 生命上限的真实伤害（护盾可挡），血越厚它吃得越香，速杀克它")),
            Map.entry("taixu", new TraitDef("taixu", "太虚", "🕳️", "身在太虚：它自带 25% 闪避（等效你的普攻伤害 -25%，与潜行同口径），只在鸿蒙界出现，堆面板硬碾克它")),
            Map.entry("golden_bell", new TraitDef("golden_bell", "金钟", "🔔", "金钟罩：抵挡你对它的第一帧全部攻击（只挡一次），开局别把大招砸在钟上")),
            Map.entry("corrode", new TraitDef("corrode", "腐蚀", "🧪", "腐蚀之触：它的攻击会叠层锈蚀你的双防（每层 -3%、上限 10 层，本场有效），速杀或堆血克它")),
            Map.entry("thunder", new TraitDef("thunder", "雷霆", "⛈️", "雷霆万钧：它的每次攻击额外引一记天雷（其物攻 ×30% 的真实伤害，护盾可吸），只在归墟界出现，堆血与护盾克它")));

    /** 词条「再生」（第八批）：怪物每秒回复上限的比例。 */
    public static final double TRAIT_REGEN_PCT = 0.03;
    /** 词条「坚韧」（第八批）：玩家暴击率对它乘区。 */
    public static final double TRAIT_TENACITY_CRIT_MUL = 0.5;
    /** 词条「星蚀」（第八批，星界专属）：玩家暴伤超额部分（CRITD−1）乘区。 */
    public static final double TRAIT_ECLIPSE_CRITD_MUL = 0.5;
    /** 词条「噬能」（第九批）：怪命中按伤害比例抽玩家蓝的比例。 */
    public static final double TRAIT_DRAIN_MP_PCT = 0.10;
    /** 词条「裂空」（第九批）：玩家闪避被撕裂的乘数（仅虚空界池）。 */
    public static final double TRAIT_REND_EVA_MUL = 0.5;

    /** 词条·燃血（第九批）：残血攻击放大满额（按已损失生命比例线性）。 */
    public static final double TRAIT_BLOOD_FURY_MAX = 0.6;

    /** 狂暴触发血线 / 攻速倍率。 */
    public static final double TRAIT_FRENZY_HP_LINE = 0.30;
    public static final double TRAIT_FRENZY_HASTE = 1.5;
    /** 破甲无视的防御比例（结算时防御乘 1-该值）。 */
    public static final double TRAIT_SUNDER_IGNORE = 0.30;
    /** 缴械的普攻伤害乘区。 */
    public static final double TRAIT_DISARM_MUL = 0.85;
    /** 虚弱的普攻伤害乘区（第十批，与缴械同口径乘算）。 */
    public static final double TRAIT_WEAKEN_MUL = 0.85;
    /** 镜像的反弹比例（第十批，对开帧目标实际承受伤害，与荆棘同口径）。 */
    public static final double TRAIT_MIRROR_MUL = 0.10;
    /** 时停 buff 期间怪物攻速乘区（第十批主动技能）。 */
    public static final double TIME_STOP_ASPD_MUL = 0.5;
    /** 狂乱词条（第十批）：怪物攻速/攻击常驻乘区。 */
    public static final double TRAIT_CHAOS_FRENZY_HASTE = 1.4;
    public static final double TRAIT_CHAOS_FRENZY_ATK = 1.2;
    /** 词条「不朽」（第十一批）：首次致死保留的生命比例。 */
    public static final double TRAIT_IMMORTAL_KEEP = 0.30;
    /** 词条「吞噬」（第十一批）：怪攻击额外真伤 = 玩家生命上限 × 此比例。 */
    public static final double TRAIT_DEVOUR_MUL = 0.02;
    /** 词条「太虚」（第十一批）：怪自带 25% 闪避——玩家普攻伤害乘区（与潜行同口径）。 */
    public static final double TRAIT_TAIXU_MUL = 0.75;
    /** 词条「腐蚀」（第十二批）：每层锈蚀玩家双防的比例与层数上限。 */
    public static final double TRAIT_CORRODE_PER_STACK = 0.03;
    public static final int TRAIT_CORRODE_MAX_STACKS = 10;
    /** 区域精通（第十二批）：每满此击杀数升一档地利伤害加成。 */
    public static final int MASTERY_KILLS_PER_TIER = 500;
    /** 区域精通（第十二批）：每档加成与总上限（+20% 于 5000 杀封顶）。 */
    public static final double MASTERY_BONUS_PER_TIER = 0.02;
    public static final double MASTERY_BONUS_CAP = 0.20;
    /** 主动「万剑归宗」（第十一批）：下一击伤害倍率。 */
    public static final double SWORD_RAIN_MUL = 5;
    /** 主动技能「天罚」倍率（第十二批）：下一击伤害 ×6。 */
    public static final double HEAVEN_WRATH_MUL = 6;
    /** 被动·混沌亲和（第十批）：对混沌界怪物的伤害乘区。 */
    public static final double CHAOS_AFFINITY_MUL = 1.25;
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
            List.of("village", "plain", "forest", "mine", "swamp", "snow", "volcano", "abyss", "star", "void", "chaos", "hongmeng", "guixu");

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
                                        : zoneIdx >= 12
                                                // 归墟界专属池（第十二批）：先与鸿蒙池同源，第三/九集群再加嗜血/金钟/雷霆
                                                ? List.of("frenzy", "sunder", "disarm", "leech", "thorns", "rich", "swift", "bulwark", "venom", "crystal", "stealth", "slow", "curse", "frost", "regen", "tenacity", "mana_drain", "void_rend", "blood_fury", "mirror", "weaken", "chaos_frenzy", "immortal", "devour", "taixu", "golden_bell", "corrode", "thunder")
                                                : zoneIdx >= 11
                                                // 鸿蒙界专属池（第十一批）：混沌池 + 不朽/吞噬 + 太虚（专属）
                                                ? List.of("frenzy", "sunder", "disarm", "leech", "thorns", "rich", "swift", "bulwark", "venom", "crystal", "stealth", "slow", "curse", "frost", "regen", "tenacity", "mana_drain", "void_rend", "blood_fury", "mirror", "weaken", "chaos_frenzy", "immortal", "devour", "taixu", "golden_bell", "corrode")
                                                : zoneIdx >= 10
                                                // 混沌界专属池：虚空池 + 镜像 + 虚弱 + 狂乱（第十批）
                                                ? List.of("frenzy", "sunder", "disarm", "leech", "thorns", "rich", "swift", "bulwark", "venom", "crystal", "stealth", "slow", "curse", "frost", "regen", "tenacity", "mana_drain", "void_rend", "blood_fury", "mirror", "weaken", "chaos_frenzy", "immortal", "devour", "golden_bell", "corrode")
                                                : zoneIdx == 9
                                                // 虚空界专属池：全词条 + 噬能 + 裂空（无星蚀，星蚀只属星界）
                                                ? List.of("frenzy", "sunder", "disarm", "leech", "thorns", "rich", "swift", "bulwark", "venom", "crystal", "stealth", "slow", "curse", "frost", "regen", "tenacity", "mana_drain", "void_rend", "blood_fury", "mirror", "weaken")
                                                : zoneIdx == 8
                                                // 星界专属池：全词条 + 星蚀（仅此一池，别的区域刷不出）
                                                ? List.of("frenzy", "sunder", "disarm", "leech", "thorns", "rich", "swift", "bulwark", "venom", "crystal", "stealth", "slow", "curse", "frost", "regen", "tenacity", "star_eclipse", "mana_drain", "blood_fury", "mirror", "weaken")
                                                : List.of("frenzy", "sunder", "disarm", "leech", "thorns", "rich", "swift", "bulwark", "venom", "crystal", "stealth", "slow", "curse", "frost", "regen", "tenacity", "mana_drain", "blood_fury", "mirror", "weaken");
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

        // 虚空界（第九批）：星界之外的无——没有光、没有声、没有回响的终局之外
        m.put("void", new ZoneDef("void", "虚空界", "🌀", false,
                "建议生命 ≥ 2亿 / 双防 ≥ 1200 / 攻击 ≥ 40000（无光无声之境）",
                Map.of("maxHP", "200000000", "PDEF", "1200", "MDEF", "1200", "ATK", "40000"),
                List.of(
                        mob("void_mote", "虚空尘埃", "🌑", "120000000", "900", "2600", "300", "900", 1.1, 6, 100, 12000,
                                "虚空里漂浮的残渣，魔光无声，掉落虚空碎片", List.of()),
                        mob("void_reaper", "虚空收割者", "💀", "200000000", "5200", "0", "800", "420", 1.5, 2, 148, 16000,
                                "镰刀过处，连影子都被收割", List.of()),
                        mob("void_eye", "虚空之眼", "👁️", "320000000", "3600", "2900", "1150", "1150", 0.8, 3, 88, 22000,
                                "双防如事件视界，被它注视即被看穿", List.of()),
                        mob("void_leviathan", "虚空利维坦", "🐋", "480000000", "2900", "5600", "650", "1350", 1.0, 7, 92, 30000,
                                "以虚空本身为食的巨兽，魔焰无声滔天", List.of())),
                mob("boss_void", "虚空之主·无相天魔", "🌀", "3000000000", "8200", "9600", "1600", "1600", 1.2, 5, 100, 2000000,
                        "它没有形体、没有名字、没有过去——只有你出拳的回响是它存在的证明", List.of())));

        // 混沌界（第十批）：虚空之外的无序——规则本身在此崩坏重组
        m.put("chaos", new ZoneDef("chaos", "混沌界", "🌪️", false,
                "建议生命 ≥ 20亿 / 双防 ≥ 3000 / 攻击 ≥ 150000（无序之源）",
                Map.of("maxHP", "2000000000", "PDEF", "3000", "MDEF", "3000", "ATK", "150000"),
                List.of(
                        mob("chaos_wisp", "混沌灵", "🌪️", "1200000000", "2600", "8000", "900", "2600", 1.1, 6, 100, 40000,
                                "无序凝成的灵体，魔光乱流，掉落混沌核心", List.of()),
                        mob("chaos_hound", "混沌猎犬", "🐺", "2000000000", "15000", "0", "2400", "1200", 1.5, 2, 148, 55000,
                                "三头六眼，猎杀一切有序之物", List.of()),
                        mob("chaos_titan", "混沌泰坦", "🗿", "3200000000", "11000", "8800", "3400", "3400", 0.8, 3, 88, 75000,
                                "双防如崩坏的世界壁垒，混沌的执盾者", List.of()),
                        mob("chaos_dragon", "混沌魔龙", "🐉", "4800000000", "8800", "17000", "2000", "4000", 1.0, 7, 92, 100000,
                                "以秩序为食的魔龙，吐息即是熵增", List.of())),
                mob("boss_chaos", "混沌魔君·无序之源", "🌪️", "30000000000", "25000", "29000", "4800", "4800", 1.2, 5, 100, 20000000,
                        "它不是某个存在，而是所有规则失效的那一刻", List.of())));

        // 鸿蒙界（第十一批）：混沌之外的太初——万物未判、紫气东来
        m.put("hongmeng", new ZoneDef("hongmeng", "鸿蒙界", "🌌", false,
                "建议生命 ≥ 500亿 / 双防 ≥ 8000 / 攻击 ≥ 400000（鸿蒙道祖）",
                Map.of("maxHP", "50000000000", "PDEF", "8000", "MDEF", "8000", "ATK", "400000"),
                List.of(
                        mob("hongmeng_spirit", "鸿蒙灵体", "🌌", "15000000000", "8000", "24000", "3000", "8000", 1.1, 6, 100, 150000,
                                "太初未判时凝成的灵体，紫气缭绕，掉落鸿蒙紫气", List.of()),
                        mob("hongmeng_beast", "鸿蒙凶兽", "🦏", "30000000000", "45000", "0", "7000", "3500", 1.5, 2, 148, 200000,
                                "开天之前就已存在的凶兽，一撞碎山河", List.of()),
                        mob("hongmeng_guard", "鸿蒙守卫", "🛡️", "50000000000", "34000", "26000", "10000", "10000", 0.8, 3, 88, 280000,
                                "双防如太初壁垒，道祖门前的执戟者", List.of()),
                        mob("hongmeng_dragon", "鸿蒙祖龙", "🐲", "90000000000", "26000", "52000", "6000", "12000", 1.0, 7, 92, 400000,
                                "龙族之祖，鳞甲上刻着开天的年轮", List.of())),
                mob("boss_hongmeng", "鸿蒙道祖·太初", "🌌", "500000000000", "75000", "88000", "15000", "15000", 1.2, 5, 100, 100000000,
                        "它在一切开始之前就坐在那里，看你把拳头举起来", List.of())));

        // 归墟界（第十二批）：鸿蒙之外的万水之渊——一切终将沉入归墟
        m.put("guixu", new ZoneDef("guixu", "归墟界", "🌊", false,
                "建议生命 ≥ 5000亿 / 双防 ≥ 20000 / 攻击 ≥ 1000000（归墟之主）",
                Map.of("maxHP", "500000000000", "PDEF", "20000", "MDEF", "20000", "ATK", "1000000"),
                List.of(
                        mob("guixu_wraith", "归墟游魂", "🌊", "1500000000000", "20000", "60000", "8000", "20000", 1.1, 6, 100, 450000,
                                "沉入归墟的亡魂凝成的水影，掉落归墟珠", List.of()),
                        mob("guixu_whale", "沉渊巨鲸", "🐋", "3000000000000", "110000", "0", "18000", "9000", 1.5, 2, 148, 600000,
                                "在归墟里游了亿万年的鲸，背上驮着沉没的大陆", List.of()),
                        mob("guixu_warden", "归墟守卫", "🛡️", "5000000000000", "85000", "65000", "25000", "25000", 0.8, 3, 88, 840000,
                                "双防如海沟之壁，无量门前的执锚者", List.of()),
                        mob("guixu_turtle", "灭世玄龟", "🐢", "9000000000000", "65000", "130000", "15000", "30000", 1.0, 7, 92, 1200000,
                                "龟甲上刻着上一个纪元的墓志铭", List.of())),
                mob("boss_guixu", "归墟之主·无量", "🌊", "50000000000000", "190000", "220000", "38000", "38000", 1.2, 5, 100, 300000000,
                        "它不在海底，它就是海底——万水归墟，无量无边", List.of())));

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
            Map.entry("flurry", new SkillDef("flurry", "连环掌", "🤲", 25, 30, 0, "接下来 30 次命中各追加一次同额命中（双击）")),
            Map.entry("void_slash", new SkillDef("void_slash", "虚空斩", "🌀", 40, 45, 0, "下一击 ×4 伤害（撕裂空间的一斩）")),
            Map.entry("time_stop", new SkillDef("time_stop", "时停", "⌛", 60, 90, 10, "10s 内怪物攻速减半（时间在你这边冻结）")),
            Map.entry("sword_rain", new SkillDef("sword_rain", "万剑归宗", "🗡️", 80, 120, 0, "下一击 ×5 伤害（万剑齐发，归于一击）")),
            Map.entry("heaven_wrath", new SkillDef("heaven_wrath", "天罚", "⚡", 100, 150, 0, "下一击 ×6 伤害（天雷代罚，一击定罪）")));

    /** 主动「虚空斩」（第九批）：下一击伤害倍率（冲锋 ×3 的上位）。 */
    public static final double VOID_SLASH_MUL = 4;

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
            Map.entry("armor_pierce", new PassiveDef("armor_pierce", "破甲专精", "🏹", "攻击无视目标 20% 的物防与魔防，厚甲在你面前如薄纸")),
            Map.entry("gem_sniffer", new PassiveDef("gem_sniffer", "宝石嗅觉", "👃", "词条怪的宝石掉率 +50%（10%→15%），宝石党的鼻子")),
            Map.entry("pet_bond", new PassiveDef("pet_bond", "宠物亲和", "🐾", "出战宠物的面板加成 +30%（与宠物心意相通，被沉默压制）")),
            Map.entry("goldtouch", new PassiveDef("goldtouch", "招财手", "🪙", "击杀金币 +15%（招财进宝，经济类增益不受沉默影响）")),
            Map.entry("chrono_body", new PassiveDef("chrono_body", "时之体", "⌛", "攻速 +0.08（时间在你皮肤上流速不同，被沉默压制）")),
            Map.entry("chaos_affinity", new PassiveDef("chaos_affinity", "混沌亲和", "🌪️", "对混沌界怪物伤害 +25%（与无序共鸣，被沉默压制）")),
            Map.entry("hongmeng_body", new PassiveDef("hongmeng_body", "鸿蒙体", "🌌", "攻速 +0.10（紫气淬体，出手如太初之风，被沉默压制）")),
            Map.entry("taixu_sword", new PassiveDef("taixu_sword", "太虚剑意", "🗡️", "暴击伤害 +20%（剑意自太虚来，被沉默压制）")),
            Map.entry("guixu_body", new PassiveDef("guixu_body", "归墟体", "🌊", "暴击率 +5%（沉渊淬体，出手带万钧水势，被沉默压制）")),
            Map.entry("tide_body", new PassiveDef("tide_body", "潮汐之体", "🌊", "闪避率 +6%（身随潮汐，敌攻总差一线，被沉默压制）")));

    /** 被动「宠物亲和」（第九批）：宠物面板加成乘区。 */
    public static final double PET_BOND_MUL = 1.3;

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
            java.util.Map.entry("vigor", new PotionDef("vigor", "生机药剂", "🌿", "60s 内每秒回复 +150", 0, 0, "vigor_pot", 150, 60, 550)),
            // ---- 第九批扩充：虚空精华（暴伤+命中） ----
            java.util.Map.entry("void", new PotionDef("void", "虚空精华", "🌀", "60s 内暴击伤害 +30%、命中 +5%", 0, 0, "void_pot", 0.30, 60, 900)),
            // ---- 第九批集群九扩充：星尘甘露（暴击+平直攻速） ----
            java.util.Map.entry("stardust", new PotionDef("stardust", "星尘甘露", "✨", "90s 内暴击 +6%、攻速 +1.5", 0, 0, "stardust_pot", 0.06, 90, 1200)),
            java.util.Map.entry("pet_treat", new PotionDef("pet_treat", "宠物零食", "🍬", "喂给出战宠物，经验 +60", 0, 0, null, 0, 0, 300)),
            java.util.Map.entry("chaos", new PotionDef("chaos", "混沌精华", "🌪️", "60s 内暴击伤害 +40%、闪避 +5%", 0, 0, "chaos_pot", 0.40, 60, 1500)),
            java.util.Map.entry("phoenix", new PotionDef("phoenix", "凤凰之血", "🦅", "60s 内暴击伤害 +50%、吸血 +5%", 0, 0, "phoenix_pot", 0.50, 60, 2000)),
            java.util.Map.entry("hongmeng", new PotionDef("hongmeng", "鸿蒙灵液", "🌌", "60s 内暴击伤害 +60%、吸血 +6%", 0, 0, "hongmeng_pot", 0.60, 60, 5000)),
            java.util.Map.entry("taixu", new PotionDef("taixu", "太虚神液", "🕳️", "60s 内暴击率 +10%、暴击伤害 +30%", 0, 0, "taixu_pot", 0.30, 60, 8000)),
            java.util.Map.entry("guixu", new PotionDef("guixu", "归墟琼浆", "🌊", "60s 内暴击伤害 +80%、吸血 +8%", 0, 0, "guixu_pot", 0.80, 60, 15000)));

    /** 破甲药剂（第八批）：无视防御比例（与破甲专精被动在 effDef 内乘算叠加）。 */
    public static final double PIERCE_POT_RATIO = 0.20;

    /** 聚宝药剂的金币倍率（与点金手天赋乘算）。 */
    public static final double FORTUNE_POT_MUL = 1.5;

    /** 被动「点金手」（第九批）：击杀金币乘区。 */
    public static final double GOLDTOUCH_MUL = 1.15;

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
            new Affix("绝杀", "CRIT", "flat", 0.15, 0.25, "epic", null, null),
            // ---- 第八批：生存维度上位（轻盈/坚韧的史诗版） ----
            new Affix("无影", "EVA", "flat", 0.10, 0.18, "epic", null, null),
            new Affix("不息", "REGEN", "flat", 5, 12, "epic", null, null),
            // ---- 第九批：法系维度上位（魔力/回魔的史诗版） ----
            new Affix("裂帛", "MATK", "flat", 16, 40, "epic", null, null),
            new Affix("灵泉", "MREGEN", "flat", 5, 12, "epic", null, null),
            // ---- 第十批：终局维度上位（物攻/生命的混沌版） ----
            new Affix("混沌", "PATK", "flat", 40, 90, "epic", null, null),
            new Affix("永恒", "maxHP", "flat", 8000, 20000, "epic", null, null),
            // ---- 第十一批：鸿蒙维度上位（魔攻/生命的太初版） ----
            new Affix("鸿蒙", "MATK", "flat", 60, 120, "epic", null, null),
            new Affix("太初", "maxHP", "flat", 20000, 50000, "epic", null, null),
            // ---- 第十二批：归墟维度上位（物攻/生命的无量版） ----
            new Affix("归墟", "PATK", "flat", 90, 180, "epic", null, null),
            new Affix("瀚海", "maxHP", "flat", 50000, 120000, "epic", null, null));

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
    /** 升星上限与每星底子加成（第十批）。 */
    public static final int STAR_MAX_LV = 5;
    public static final double STAR_STAT_BONUS = 0.03;
    /** 升星金币成本基数：第 n 星（0 起）需 5000×(n+1) 金 + (n+1) 颗星核碎片。 */
    public static final long STAR_GOLD_BASE = 5000;
    /** 觉醒上限与每级底子加成（第十一批）：满星（5 星）后可觉醒，独立乘区 +5%/级。 */
    public static final int AWAKEN_MAX_LV = 3;
    public static final double AWAKEN_STAT_BONUS = 0.05;
    /** 觉醒金币成本基数：第 n 级（0 起）需 100000×(n+1) 金 + (n+1) 颗混沌核心。 */
    public static final long AWAKEN_GOLD_BASE = 100_000;

    // ==================== 宝石镶嵌（第八批） ====================

    /** 宝石定义：stats 为 flat 加成（与装备词条同区，属装备来源、不受沉默压制）。 */
    public record GemDef(String id, String name, String emoji, Map<String, BigDecimal> stats, String desc) {
    }

    /** 每件装备的镶嵌孔上限。 */
    public static final int GEM_SOCKET_MAX = 3;
    /** 词条怪掉落宝石概率（Boss 必掉，见 GameEngine 击杀漏斗）。 */
    public static final double GEM_TRAIT_DROP_RATE = 0.10;
    /** 被动·宝石嗅觉（第八批）：词条怪宝石掉率倍率。 */
    public static final double GEM_SNIFFER_MUL = 1.5;
    /** 宝石出售单价（第八批，金币/颗）：率型宝石（暴击/攻速）最贵；二阶为一阶 ×3、三阶为一阶 ×9（第十二批）。 */
    public static final Map<String, Integer> GEM_SELL_PRICE = Map.ofEntries(
            Map.entry("gem_ruby", 500), Map.entry("gem_sapphire", 500), Map.entry("gem_emerald", 600),
            Map.entry("gem_opal", 700), Map.entry("gem_topaz", 800), Map.entry("gem_amethyst", 800),
            Map.entry("gem_ruby_2", 1500), Map.entry("gem_sapphire_2", 1500), Map.entry("gem_emerald_2", 1800),
            Map.entry("gem_opal_2", 2100), Map.entry("gem_topaz_2", 2400), Map.entry("gem_amethyst_2", 2400),
            Map.entry("gem_ruby_3", 4500), Map.entry("gem_sapphire_3", 4500), Map.entry("gem_emerald_3", 5400),
            Map.entry("gem_opal_3", 6300), Map.entry("gem_topaz_3", 7200), Map.entry("gem_amethyst_3", 7200));

    /**
     * 宝石全表（一阶 6 种 + 璀璨二阶 6 种 + 曜三阶 6 种）：一阶由 Boss/词条怪掉落；
     * 二阶由 3 颗同种一阶融合（属性 ×2.5），三阶由 3 颗同种二阶融合（二阶再 ×2.2，合计 ×5.5），
     * code 为一阶 code + "_2"/"_3"，二三阶均不进掉落池。
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
            // 三阶（第十二批）：二阶再 ×2.2（对一阶合计 ×5.5），只能融合得到
            Map<String, BigDecimal> stats3 = new java.util.LinkedHashMap<>();
            t1.stats().forEach((k, v) -> stats3.put(k, v.multiply(new BigDecimal("5.5"))));
            StringBuilder sb3 = new StringBuilder();
            stats3.forEach((k, v) -> {
                if (sb3.length() > 0) {
                    sb3.append("、");
                }
                sb3.append(k).append(" +").append(v.stripTrailingZeros().toPlainString());
            });
            m.put(t1.id() + "_3", new GemDef(t1.id() + "_3", "曜·" + t1.name(), "💠", Map.copyOf(stats3),
                    "三阶融合宝石：" + sb3));
        }
        return java.util.Collections.unmodifiableMap(m);
    }

    /** 一阶宝石 id 列表（掉落池口径，二三阶不进池）。 */
    public static java.util.List<String> gemTier1Ids() {
        return GEMS.keySet().stream().filter(id -> !id.endsWith("_2") && !id.endsWith("_3")).toList();
    }

    /** 二阶宝石 id 列表（第十二批：三阶融合的原料口径）。 */
    public static java.util.List<String> gemTier2Ids() {
        return GEMS.keySet().stream().filter(id -> id.endsWith("_2")).toList();
    }
    // ==================== 宠物伙伴（第九批） ====================

    /** 宠物定义：stats 为 1 级 flat 加成，随等级线性放大（伙伴来源、不受沉默压制）。 */
    public record PetDef(String id, String name, String emoji, Map<String, BigDecimal> stats, String desc, int price) {
    }

    /** 宠物等级上限。 */
    public static final int PET_MAX_LEVEL = 50;
    /** 每级属性放大系数：实际加成 = 基础 × (1 + 0.15 × (等级 − 1))。 */
    public static final double PET_LEVEL_MUL = 0.15;
    /** 击杀一只怪宠物获得的经验。 */
    public static final int PET_EXP_PER_KILL = 1;
    /** 喂食一次获得的经验与金币成本。 */
    public static final int PET_FEED_EXP = 10;
    public static final int PET_FEED_COST = 150;

    /** 升到下一级所需经验：20 + (当前等级 − 1) × 10。 */
    public static int petExpNeed(int level) {
        return 20 + (level - 1) * 10;
    }

    /** 宠物全表（9 只）：金币领养，领养后可切换出战，出战宠物按等级放大面板加成。 */
    public static final Map<String, PetDef> PETS = Map.ofEntries(
            Map.entry("pet_cat", new PetDef("pet_cat", "招财猫", "🐱", Map.of("PATK", new BigDecimal("30")), "物攻 +30", 2000)),
            Map.entry("pet_dog", new PetDef("pet_dog", "忠犬", "🐶", Map.of("maxHP", new BigDecimal("1500")), "生命上限 +1500", 2000)),
            Map.entry("pet_fox", new PetDef("pet_fox", "灵狐", "🦊", Map.of("MATK", new BigDecimal("30")), "魔攻 +30", 3000)),
            Map.entry("pet_turtle", new PetDef("pet_turtle", "玄龟", "🐢",
                    Map.of("PDEF", new BigDecimal("25"), "MDEF", new BigDecimal("25")), "双防各 +25", 4000)),
            Map.entry("pet_hawk", new PetDef("pet_hawk", "苍鹰", "🦅", Map.of("CRIT", new BigDecimal("0.02")), "暴击率 +2%", 6000)),
            Map.entry("pet_deer", new PetDef("pet_deer", "森鹿", "🦌", Map.of("REGEN", new BigDecimal("20")), "生命回复 +20", 5000)),
            Map.entry("pet_panda", new PetDef("pet_panda", "团团熊猫", "🐼",
                    Map.of("maxHP", new BigDecimal("3000"), "REGEN", new BigDecimal("30")), "生命上限 +3000、生命回复 +30", 8000)),
            Map.entry("pet_ferret", new PetDef("pet_ferret", "雪貂", "🦦",
                    Map.of("SPD", new BigDecimal("25"), "EVA", new BigDecimal("0.03")), "速度 +25、闪避 +3%", 7000)),
            Map.entry("pet_qilin", new PetDef("pet_qilin", "麒麟", "🦄",
                    Map.of("PATK", new BigDecimal("400"), "MATK", new BigDecimal("400"), "CRITD", new BigDecimal("0.10")),
                    "物攻 +400、魔攻 +400、暴伤 +10%", 50000)));

    /** 宠物进化（第十一批）：满级（50）可进化，属性倍率 ×2；成本 20 万金 + 混沌核心 ×3。 */
    public static final long PET_EVOLVE_GOLD = 200_000L;
    public static final String PET_EVOLVE_MAT = "chaos_core";
    public static final int PET_EVOLVE_MAT_COUNT = 3;
    public static final double PET_EVOLVE_MUL = 2.0;

    /** 宠物羁绊（第十批）：全部已领养宠物总等级达档，出战宠物加成再乘羁绊倍率。 */
    public static final int[] PET_BOND_TIERS = {30, 100, 250};
    public static final double[] PET_BOND_MULS = {1.25, 1.6, 2.2};

    /** 羁绊倍率：总等级 <30 为 1.0，逐档 1.25/1.6/2.2；天赋「混沌之心」再 ×1.5（第十批）。 */
    public static double petBondMul(int totalLevels, boolean chaosHeart) {
        return petBondMul(totalLevels, chaosHeart, false);
    }

    /** 羁绊倍率（第十一批）：天赋「鸿蒙之心」再 ×1.5，与混沌之心叠乘（合计 ×2.25）。 */
    public static double petBondMul(int totalLevels, boolean chaosHeart, boolean hongmengHeart) {
        double mul = 1.0;
        for (int i = 0; i < PET_BOND_TIERS.length; i++) {
            if (totalLevels >= PET_BOND_TIERS[i]) {
                mul = PET_BOND_MULS[i];
            }
        }
        if (chaosHeart) {
            mul *= 1.5;
        }
        if (hongmengHeart) {
            mul *= 1.5;
        }
        return mul;
    }

    /** 宠物零食（第十批）：使用后出战宠物 +60 经验。 */
    public static final int PET_TREAT_EXP = 60;
    /** 经验分享（第十批）：非出战宠物获得出战宠物经验的 25%。 */
    public static final double PET_SHARE_RATE = 0.25;

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
    public static final Map<String, TalentDef> TALENTS = Map.ofEntries(
            Map.entry("eternal", new TalentDef("eternal", "被动永续", "♾️", "所有增益被动永不消逝")),
            Map.entry("baglord", new TalentDef("baglord", "无限装备栏", "🧳", "无相位装备栏无限：任何装备都能穿上，属性全部叠加")),
            Map.entry("statlord", new TalentDef("statlord", "无限属性点", "💠", "每杀一怪 +1 自由属性点：属性面板自由加点")),
            Map.entry("might", new TalentDef("might", "天生神力", "💪", "物攻 +10%")),
            Map.entry("shell", new TalentDef("shell", "铜皮铁骨", "🐢", "物防 +10%、魔防 +10%")),
            Map.entry("wind", new TalentDef("wind", "疾风之躯", "💨", "移速 +15%")),
            Map.entry("goldhand", new TalentDef("goldhand", "点金手", "🪙", "击杀金币 +20%")),
            Map.entry("master", new TalentDef("master", "武学奇才", "📕", "主动技能冷却 -15%")),
            Map.entry("lucky", new TalentDef("lucky", "幸运儿", "🍀", "装备与图纸掉落率 +15%")),
            Map.entry("iron_gut", new TalentDef("iron_gut", "铁胃", "🍲", "食物回血效果 +50%、持续时间 +50%")),
            Map.entry("gem_affinity", new TalentDef("gem_affinity", "宝石亲和", "🔷", "镶嵌宝石的效果 +25%，宝石在你手里更亮")),
            Map.entry("pet_whisper", new TalentDef("pet_whisper", "兽语者", "🐾", "宠物经验 +100%：击杀与喂食所得经验翻倍")),
            Map.entry("chaos_heart", new TalentDef("chaos_heart", "混沌之心", "🌪️", "宠物羁绊倍率 ×1.5：无序之心与万兽共鸣")),
            Map.entry("hongmeng_heart", new TalentDef("hongmeng_heart", "鸿蒙之心", "🌌", "宠物羁绊倍率 ×1.5：紫气之心与万兽共鸣，与混沌之心叠乘")),
            Map.entry("star_heart", new TalentDef("star_heart", "星辰之心", "⭐", "镶嵌宝石效果 ×1.5：星辰为眸，宝石在你手中如星辰运转，与宝石亲和叠乘")));

    /** 天赋「宝石亲和」（第八批）：宝石 flat 加成乘区（天赋不受沉默压制）。 */
    public static final double GEM_AFFINITY_MUL = 1.25;
    /** 天赋「星辰之心」（第十二批）：镶嵌宝石效果乘数（与宝石亲和叠乘）。 */
    public static final double STAR_HEART_GEM_MUL = 1.5;
    /** 词条「雷霆」（第十二批）：怪攻击额外一记雷击 = 其物攻 × 此比例。 */
    public static final double TRAIT_THUNDER_MUL = 0.3;

    /** 天赋「兽语者」（第九批）：宠物经验乘区（gainPetExp 单点，击杀/喂食同享）。 */
    public static final double PET_WHISPER_MUL = 2.0;

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
            Map.entry("abyss", "abyss_crystal"), Map.entry("star", "star_shard"), Map.entry("void", "void_shard"),
            Map.entry("chaos", "chaos_core"), Map.entry("hongmeng", "hongmeng_qi"),
            Map.entry("guixu", "guixu_pearl"));

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
            Map.entry("void_shard", new String[]{"虚空碎片", "🌀"}),
            Map.entry("chaos_core", new String[]{"混沌核心", "🌪️"}),
            Map.entry("hongmeng_qi", new String[]{"鸿蒙紫气", "🌌"}),
            Map.entry("guixu_pearl", new String[]{"归墟珠", "🌊"}),
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
            Map.entry("gem_opal_2", new String[]{"璀璨蛋白石", "💎"}),
            Map.entry("gem_ruby_3", new String[]{"曜·红宝石", "💠"}),
            Map.entry("gem_sapphire_3", new String[]{"曜·蓝宝石", "💠"}),
            Map.entry("gem_emerald_3", new String[]{"曜·翡翠", "💠"}),
            Map.entry("gem_topaz_3", new String[]{"曜·黄玉", "💠"}),
            Map.entry("gem_amethyst_3", new String[]{"曜·紫水晶", "💠"}),
            Map.entry("gem_opal_3", new String[]{"曜·蛋白石", "💠"}));

    /** 材料出售单价（第七批，金币/份）：越深的区域材料越值钱；仅区域材料可售（图纸/卷轴不可）。 */
    public static final Map<String, Integer> MAT_SELL_PRICE = Map.ofEntries(
            Map.entry("pelt", 20), Map.entry("wolf_fang", 35), Map.entry("ore", 60), Map.entry("toad_gland", 90),
            Map.entry("mana_dust", 140), Map.entry("fire_crystal", 220), Map.entry("abyss_crystal", 400),
            Map.entry("star_shard", 650), Map.entry("void_shard", 2000), Map.entry("chaos_core", 6000),
            Map.entry("hongmeng_qi", 20000), Map.entry("guixu_pearl", 60000));

    /** 增益类 buff（永久存续天赋作用对象）。 */
    public static final Set<String> ETERNAL_BUFFS = Set.of(
            "burn", "block", "rapid", "surge", "regen_food", "spd_pot", "growth_pot", "iron_pot");
}
