package com.gongpu.game;

import com.gongpu.common.Num;
import com.gongpu.state.PlayerState;
import com.gongpu.state.StateCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * 成就系统（服务端权威）：18 项成就全部由存档状态推导,在每次落库前评估。
 * <p>解锁集合持久化在 state_json 的 achievements 里,转生/死亡重开不清空
 * （成就记录的是江湖足迹,不是当路面板）。
 * <p>前端 web/js/achievements.js 保留同名 id 表用于渲染;两侧 id 必须一致,
 * 由 AchievementsConsistencyTest 锁死。
 */
public final class Achievements {

    /** 成就定义（id 与前端 achievements.js 一一对应）。 */
    public record Def(String id, String name, String emoji, String desc) {
    }

    public static final List<Def> ALL = List.of(
            new Def("first_punch", "出拳", "👊", "累计出拳 1 次"),
            new Def("punch_10k", "万拳", "💯", "累计出拳 1 万次"),
            new Def("first_kill", "首杀", "💀", "击杀 1 只怪物"),
            new Def("kill_100", "百人斩", "⚔️", "击杀 100 只怪物"),
            new Def("hp_5000", "皮糙肉厚", "❤️", "生命上限达 5000"),
            new Def("hp_1m", "百万雄师", "🫀", "生命上限达 100 万"),
            new Def("gold_10k", "小有身家", "💰", "持有 1 万金币"),
            new Def("zone_volcano", "踏足火山", "🌋", "到达火山"),
            new Def("lived_1h", "熬过一时辰", "⏱️", "单世存活 1 小时"),
            new Def("first_death", "第一次死亡", "⚰️", "死亡 1 次（墓园见）"),
            new Def("equip_5", "收集家", "🎒", "背包与装备合计 5 件"),
            new Def("wear_epic", "史诗加身", "🟣", "穿上一件史诗装备"),
            new Def("boss_win", "以下克上", "👑", "击败任意区域 Boss"),
            new Def("gen_1", "转世者", "🌟", "完成一次飞升转生"),
            new Def("gen_12", "万世圆满", "🧬", "达到第 12 世（集齐 13 条赋能）"),
            new Def("talent", "觉醒者", "✨", "觉醒一项天赋"),
            new Def("bless_3", "三线成长", "🧬", "同时拥有 3 条赋能"),
            new Def("duelist", "切磋者", "⚔️", "觉醒天赋并击杀 10 只怪物"),
            new Def("codex_10", "图鉴·十种", "📖", "图鉴记录 10 种怪物"),
            new Def("codex_all", "万物图鉴", "📚", "集齐全部怪物图鉴（含各区域 Boss）"),
            new Def("first_craft", "初试锻造", "⚒️", "完成第一次图纸合成"),
            new Def("first_enchant", "初试附魔", "✨", "完成第一次装备附魔"),
            new Def("potion_5", "品鉴五味", "🧪", "使用过 5 种不同的药剂"),
            new Def("potion_15", "神农后人", "🌿", "使用过 15 种不同的药剂"),
            new Def("kill_10k", "万怪斩", "🗡️", "终身击杀 1 万只怪物"),
            new Def("gold_1m", "富甲一方", "🏦", "持有 100 万金币"),
            new Def("boss_10", "屠龙者", "🐉", "累计击败 Boss 10 次"),
            new Def("best_hit_1m", "开山一击", "💥", "单拳伤害达 100 万"),
            new Def("dismantle_100", "分解达人", "🔨", "累计分解 100 件装备"),
            new Def("craft_25", "锻造大师", "🏭", "累计合成 25 次"),
            new Def("boss_50", "屠龙宗师", "🐲", "累计击败 Boss 50 次"),
            new Def("potion_25", "神农尝百草", "🧉", "使用过 25 种不同的药剂"),
            new Def("boss_100", "屠龙神话", "🐉", "累计击败 Boss 100 次"),
            new Def("craft_50", "锻造宗师", "🏯", "累计合成 50 次"),
            new Def("set_4", "整装待发", "🧩", "集齐任意一套装的 4 件"),
            new Def("set_dual", "双套加身", "🎭", "同时激活两套套装（各 ≥2 件）"),
            new Def("temper_5", "淬火成钢", "🔨", "任意一件装备淬炼满级"),
            new Def("reroll_50", "洗炼大师", "🎲", "累计洗炼 50 次"),
            new Def("abyss_first", "渊瞳初开", "🕳️", "首次击杀深渊裂隙的怪物"),
            new Def("abyss_lord", "深渊主宰", "👑", "击杀渊底之主·无光君王"),
            new Def("daily_1", "每日事每日毕", "📅", "首次领取每日任务奖励"),
            new Def("daily_30", "勤勉不辍", "🗓️", "累计领取 30 次每日任务奖励"),
            new Def("phase_breaker", "破相者", "🔥", "击破 1 只二阶段 Boss"),
            new Def("weak_100", "弱点猎手", "🎯", "累计触发 100 次弱点打击"),
            new Def("market_1", "黑市客", "🌑", "首次在每日黑市购买药剂"),
            new Def("merchant", "商贾之道", "🧮", "卖材料累计收入 50,000 金币"),
            new Def("slow_20", "迟缓大师", "🐌", "迟缓咒熟练度达到 20"),
            new Def("epic_affix", "史诗词条", "🟣", "拥有 3 条史诗词条（已装备与背包合计）"),
            new Def("gem_first", "镶嵌初体验", "💠", "首次把宝石镶嵌到装备上"),
            new Def("gem_10", "珠光宝气", "💎", "累计镶嵌 10 颗宝石"),
            new Def("pierce_learn", "破甲之道", "🏹", "学会被动「破甲专精」"),
            new Def("flurry_500", "连环如雨", "🤲", "连环掌累计追加命中 500 次"),
            new Def("star_first", "星瞳初开", "🌌", "首次击杀星界的怪物"),
            new Def("star_lord", "群星主宰", "👑", "击杀星核主宰·噬光之心"),
            new Def("weekly_1", "周常初成", "🗓️", "领取 3 个周常任务奖励（整整一周全清）"),
            new Def("weekly_8", "周常老兵", "🎖️", "累计领取 24 个周常任务奖励（八周全清）"),
            new Def("gem_collector", "宝石收藏家", "💠", "累计直购 10 颗宝石"),
            new Def("set_wearer", "套装行者", "👕", "同时穿戴任意一套装的全部 4 件"),
            new Def("punch_1m", "百万连拳", "🥊", "累计出拳 100 万次"),
            new Def("kill_100k", "十万斩", "⚔️", "终身击杀 10 万只怪物"),
            new Def("gold_100m", "金山银海", "🏔️", "持有 1 亿金币"),
            new Def("craft_200", "锻造传说", "🔥", "累计合成 200 次"),
            new Def("dismantle_1000", "万物分解", "♻️", "累计分解 1000 件装备"),
            new Def("gen_30", "三十世轮回", "☸️", "达到第 30 世")
    );

    private Achievements() {
    }

    /** 成就奖励金币（领取制）：按达成难度分四档，服务端权威，前端只展示 view 下发的值。 */
    public static long rewardOf(String id) {
        return switch (id) {
            case "first_punch", "first_kill", "first_death", "first_craft", "first_enchant",
                    "potion_5", "codex_10", "talent" -> 100L;
            case "hp_1m", "wear_epic", "lived_1h", "gen_12", "codex_all", "potion_15",
                    "kill_10k", "boss_10", "craft_25", "boss_50", "potion_25", "set_4", "temper_5",
                    "daily_30", "phase_breaker", "merchant", "epic_affix", "gem_10", "flurry_500", "weekly_1", "gem_collector", "set_wearer", "punch_1m", "dismantle_1000" -> 2_000L;
            case "gold_1m", "best_hit_1m", "boss_100", "craft_50", "abyss_lord", "star_lord", "weekly_8",
                    "kill_100k", "gold_100m", "craft_200", "gen_30" -> 5_000L;
            default -> 500L;
        };
    }

    /** 评估当前状态新解锁的成就 id（不含已解锁的）。在 SaveService.persist 前调用。 */
    /**
     * 成就进度（当前值/目标值）：可量化成就返回 [cur, target]，事件型成就返回 null（前端不画进度条）。
     * 与 evaluate 的判定口径逐项一致（改判定必须同步这里）。
     */
    /** 图鉴中已收集的深渊怪种数（abyss_first 成就口径）。 */
    static int abyssCodexHits(PlayerState s) {
        if (s.codex == null) {
            return 0;
        }
        int n = 0;
        for (String id : new String[]{"abyss_wisp", "rift_hound", "void_knight", "chaos_spawn"}) {
            if (s.codex.contains(id)) {
                n++;
            }
        }
        return n;
    }

    /** 套装最大穿戴件数（第八批）：任一套装的不同成员穿戴数取最大（套装行者口径）。 */
    static int maxSetWorn(PlayerState s) {
        int max = 0;
        for (int v : Sets.wornCounts(s).values()) {
            max = Math.max(max, v);
        }
        return max;
    }

    /** 图鉴中已收集的星界怪种数（star_first 成就口径，第八批）。 */
    static int starCodexHits(PlayerState s) {
        if (s.codex == null) {
            return 0;
        }
        int n = 0;
        for (String id : new String[]{"star_sprite", "void_walker", "star_guard", "star_eater"}) {
            if (s.codex.contains(id)) {
                n++;
            }
        }
        return n;
    }

    /** 当前最高淬炼等级（已装备 + 背包，temper_5 成就口径）。 */
    static int maxTemper(PlayerState s) {
        int max = 0;
        for (PlayerState.Item it : s.equipped.values()) {
            if (it != null) {
                max = Math.max(max, it.temper);
            }
        }
        for (PlayerState.Item it : s.equipBag) {
            if (it != null) {
                max = Math.max(max, it.temper);
            }
        }
        return max;
    }

    /** 史诗词条总数（已装备 + 背包，epic_affix 成就口径）。 */
    static int epicAffixCount(PlayerState s) {
        int n = 0;
        List<PlayerState.Item> all = new ArrayList<>();
        for (PlayerState.Item it : s.equipped.values()) {
            if (it != null) {
                all.add(it);
            }
        }
        if (s.equipBag != null) {
            all.addAll(s.equipBag);
        }
        for (PlayerState.Item it : all) {
            if (it.affixes != null) {
                for (PlayerState.AffixInst a : it.affixes) {
                    if ("epic".equals(a.rarity)) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    public static long[] progressOf(PlayerState s, String id) {
        return progressOf(s, id, null);
    }

    /** 带预建面板的版本：fullView 每帧调用时复用其面板，避免 hp 类进度再算两遍面板。 */
    public static long[] progressOf(PlayerState s, String id, PanelCalculator.Panel panel) {
        if (s == null || s.stats == null) {
            return null;
        }
        return switch (id) {
            case "punch_10k" -> new long[]{sat(s.stats.lifetimePunches), 10_000};
            case "punch_1m" -> new long[]{sat(s.stats.lifetimePunches), 1_000_000};
            case "kill_100k" -> new long[]{sat(s.stats.lifetimeKills), 100_000};
            case "gold_100m" -> new long[]{sat(s.gold), 100_000_000};
            case "kill_100" -> new long[]{sat(s.stats.kills), 100};
            case "kill_10k" -> new long[]{sat(s.stats.lifetimeKills), 10_000};
            case "hp_5000" -> new long[]{sat((panel == null ? PanelCalculator.of(s) : panel).maxHP()), 5_000};
            case "hp_1m" -> new long[]{sat((panel == null ? PanelCalculator.of(s) : panel).maxHP()), 1_000_000};
            case "gold_10k" -> new long[]{sat(s.gold), 10_000};
            case "gold_1m" -> new long[]{sat(s.gold), 1_000_000};
            case "lived_1h" -> new long[]{com.gongpu.state.StateCodec.aliveSeconds(s), 3600};
            case "equip_5" -> new long[]{s.equipBag.size()
                    + s.equipped.values().stream().filter(it -> it != null).count(), 5};
            case "gen_12" -> new long[]{s.gen, 12};
            case "gen_30" -> new long[]{s.gen, 30};
            case "bless_3" -> new long[]{s.blessings == null ? 0 : s.blessings.size(), 3};
            case "duelist" -> new long[]{sat(s.stats.kills), 10};
            case "codex_10" -> new long[]{s.codex == null ? 0 : s.codex.size(), 10};
            case "codex_all" -> new long[]{s.codex == null ? 0 : s.codex.size(), GameConst.codexTotal()};
            case "first_craft" -> new long[]{s.stats.crafts, 1};
            case "craft_25" -> new long[]{s.stats.crafts, 25};
            case "first_enchant" -> new long[]{s.stats.enchants, 1};
            case "potion_5" -> new long[]{s.usedPotions == null ? 0 : s.usedPotions.size(), 5};
            case "potion_15" -> new long[]{s.usedPotions == null ? 0 : s.usedPotions.size(), 15};
            case "potion_25" -> new long[]{s.usedPotions == null ? 0 : s.usedPotions.size(), 25};
            case "boss_10" -> new long[]{s.stats.bossKillCount, 10};
            case "boss_50" -> new long[]{s.stats.bossKillCount, 50};
            case "boss_100" -> new long[]{s.stats.bossKillCount, 100};
            case "craft_50" -> new long[]{s.stats.crafts, 50};
            case "craft_200" -> new long[]{s.stats.crafts, 200};
            case "temper_5" -> new long[]{maxTemper(s), GameConst.TEMPER_MAX_LV};
            case "reroll_50" -> new long[]{s.stats.rerolls, 50};
            case "abyss_first" -> new long[]{abyssCodexHits(s), 1};
            case "abyss_lord" -> new long[]{s.bossKills != null && s.bossKills.contains("boss_abyss") ? 1 : 0, 1};
            case "star_first" -> new long[]{starCodexHits(s), 1};
            case "star_lord" -> new long[]{s.bossKills != null && s.bossKills.contains("boss_star") ? 1 : 0, 1};
            case "weekly_1" -> new long[]{s.stats.weeklyClaims, 3};
            case "weekly_8" -> new long[]{s.stats.weeklyClaims, 24};
            case "gem_collector" -> new long[]{s.stats.gemsBought, 10};
            case "set_wearer" -> new long[]{maxSetWorn(s), 4};
            case "daily_1" -> new long[]{s.stats.dailyClaims, 1};
            case "daily_30" -> new long[]{s.stats.dailyClaims, 30};
            case "phase_breaker" -> new long[]{s.stats.bossPhaseKills, 1};
            case "weak_100" -> new long[]{s.stats.weakCrits, 100};
            case "market_1" -> new long[]{s.stats.marketBuys, 1};
            case "merchant" -> new long[]{s.stats.matSoldGold, 50_000};
            case "slow_20" -> new long[]{s.mastery == null ? 0
                    : s.mastery.getOrDefault("hex_slow", java.math.BigDecimal.ZERO).longValue(), 20};
            case "epic_affix" -> new long[]{epicAffixCount(s), 3};
            case "gem_first" -> new long[]{s.stats.gemsSocketed, 1};
            case "gem_10" -> new long[]{s.stats.gemsSocketed, 10};
            case "pierce_learn" -> new long[]{s.passives.contains("armor_pierce") ? 1 : 0, 1};
            case "flurry_500" -> new long[]{s.stats.flurryExtra.longValue(), 500};
            case "best_hit_1m" -> new long[]{sat(s.stats.bestHit), 1_000_000};
            case "dismantle_100" -> new long[]{s.stats.dismantles, 100};
            case "dismantle_1000" -> new long[]{s.stats.dismantles, 1000};
            case "set_4" -> new long[]{Sets.wornCounts(s).values().stream()
                    .mapToInt(Integer::intValue).max().orElse(0), 4};
            case "set_dual" -> new long[]{Sets.wornCounts(s).values().stream()
                    .filter(n -> n >= 2).count(), 2};
            default -> null;
        };
    }

    public static List<String> evaluate(PlayerState s) {
        if (s == null || s.achievements == null) {
            return List.of();
        }
        List<String> newly = new ArrayList<>();
        PanelCalculator.Panel p = PanelCalculator.of(s);
        long punches = sat(s.stats.lifetimePunches);
        long kills = sat(s.stats.kills);
        long maxHp = sat(p.maxHP());
        long gold = sat(s.gold);
        int equipCount = s.equipBag.size() + (int) s.equipped.values().stream().filter(it -> it != null).count();
        boolean wearEpic = s.equipped.values().stream()
                .anyMatch(it -> it != null && it.quality >= 3);
        java.util.Map<String, Integer> setCounts = Sets.wornCounts(s);

        for (Def d : ALL) {
            if (s.achievements.contains(d.id())) {
                continue;
            }
            boolean ok = switch (d.id()) {
                case "first_punch" -> punches >= 1;
                case "punch_10k" -> punches >= 10_000;
                case "punch_1m" -> punches >= 1_000_000;
                case "first_kill" -> kills >= 1;
                case "kill_100" -> kills >= 100;
                case "hp_5000" -> maxHp >= 5_000;
                case "hp_1m" -> maxHp >= 1_000_000;
                case "gold_10k" -> gold >= 10_000;
                case "zone_volcano" -> "volcano".equals(s.zone);
                case "lived_1h" -> StateCodec.aliveSeconds(s) >= 3600;
                case "first_death" -> s.stats.deaths >= 1;
                case "equip_5" -> equipCount >= 5;
                case "wear_epic" -> wearEpic;
                case "boss_win" -> !s.bossKills.isEmpty();
                case "gen_1" -> s.gen >= 1 || s.stats.rebirths >= 1;
                case "gen_12" -> s.gen >= 12;
                case "gen_30" -> s.gen >= 30;
                case "talent" -> s.talent != null && !s.talent.isBlank();
                case "bless_3" -> s.blessings.size() >= 3;
                case "duelist" -> s.talent != null && !s.talent.isBlank() && kills >= 10;
                case "codex_10" -> s.codex != null && s.codex.size() >= 10;
                case "codex_all" -> s.codex != null && s.codex.size() >= GameConst.codexTotal();
                case "first_craft" -> s.stats.crafts >= 1;
                case "first_enchant" -> s.stats.enchants >= 1;
                case "potion_5" -> s.usedPotions.size() >= 5;
                case "potion_15" -> s.usedPotions.size() >= 15;
                case "kill_10k" -> sat(s.stats.lifetimeKills) >= 10_000;
                case "kill_100k" -> sat(s.stats.lifetimeKills) >= 100_000;
                case "gold_1m" -> gold >= 1_000_000;
                case "gold_100m" -> gold >= 100_000_000;
                case "boss_10" -> s.stats.bossKillCount >= 10;
                case "best_hit_1m" -> sat(s.stats.bestHit) >= 1_000_000;
                case "dismantle_100" -> s.stats.dismantles >= 100;
                case "dismantle_1000" -> s.stats.dismantles >= 1000;
                case "craft_25" -> s.stats.crafts >= 25;
                case "boss_50" -> s.stats.bossKillCount >= 50;
                case "boss_100" -> s.stats.bossKillCount >= 100;
                case "craft_50" -> s.stats.crafts >= 50;
                case "craft_200" -> s.stats.crafts >= 200;
                case "potion_25" -> s.usedPotions.size() >= 25;
                case "set_4" -> setCounts.values().stream().anyMatch(n -> n >= 4);
                case "set_dual" -> setCounts.values().stream().filter(n -> n >= 2).count() >= 2;
                case "temper_5" -> maxTemper(s) >= GameConst.TEMPER_MAX_LV;
                case "reroll_50" -> s.stats.rerolls >= 50;
                case "abyss_first" -> abyssCodexHits(s) >= 1;
                case "abyss_lord" -> s.bossKills != null && s.bossKills.contains("boss_abyss");
                case "star_first" -> starCodexHits(s) >= 1;
                case "star_lord" -> s.bossKills != null && s.bossKills.contains("boss_star");
                case "weekly_1" -> s.stats.weeklyClaims >= 3;
                case "weekly_8" -> s.stats.weeklyClaims >= 24;
                case "gem_collector" -> s.stats.gemsBought >= 10;
                case "set_wearer" -> maxSetWorn(s) >= 4;
                case "daily_1" -> s.stats.dailyClaims >= 1;
                case "daily_30" -> s.stats.dailyClaims >= 30;
                case "phase_breaker" -> s.stats.bossPhaseKills >= 1;
                case "weak_100" -> s.stats.weakCrits >= 100;
                case "market_1" -> s.stats.marketBuys >= 1;
                case "merchant" -> s.stats.matSoldGold >= 50_000;
                case "slow_20" -> s.mastery != null
                        && s.mastery.getOrDefault("hex_slow", java.math.BigDecimal.ZERO).longValue() >= 20;
                case "epic_affix" -> epicAffixCount(s) >= 3;
                case "gem_first" -> s.stats.gemsSocketed >= 1;
                case "gem_10" -> s.stats.gemsSocketed >= 10;
                case "pierce_learn" -> s.passives.contains("armor_pierce");
                case "flurry_500" -> s.stats.flurryExtra.compareTo(java.math.BigDecimal.valueOf(500)) >= 0;
                default -> false;
            };
            if (ok) {
                newly.add(d.id());
            }
        }
        return newly;
    }

    /** 成就只做阈值比较,doubleValue 安全;超 long 量级按 long 上限饱和。 */
    private static long sat(java.math.BigDecimal v) {
        if (v == null) {
            return 0;
        }
        return v.compareTo(java.math.BigDecimal.valueOf(Long.MAX_VALUE)) >= 0
                ? Long.MAX_VALUE
                : Math.max(0, v.longValue());
    }
}
