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
            new Def("codex_all", "万物图鉴", "📚", "集齐全部怪物图鉴（含各区域 Boss）")
    );

    private Achievements() {
    }

    /** 评估当前状态新解锁的成就 id（不含已解锁的）。在 SaveService.persist 前调用。 */
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

        for (Def d : ALL) {
            if (s.achievements.contains(d.id())) {
                continue;
            }
            boolean ok = switch (d.id()) {
                case "first_punch" -> punches >= 1;
                case "punch_10k" -> punches >= 10_000;
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
                case "talent" -> s.talent != null && !s.talent.isBlank();
                case "bless_3" -> s.blessings.size() >= 3;
                case "duelist" -> s.talent != null && !s.talent.isBlank() && kills >= 10;
                case "codex_10" -> s.codex != null && s.codex.size() >= 10;
                case "codex_all" -> s.codex != null && s.codex.size() >= GameConst.codexTotal();
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
