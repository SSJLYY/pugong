package com.gongpu.game;

import com.gongpu.common.Num;
import com.gongpu.state.Life;
import com.gongpu.state.PlayerState;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 面板计算：把「基础成长值 + 装备 + 被动技能 + 增益」折算为生效面板。
 * <p>与旧版前端 getPanel() 一一对应，保证迁移后手感一致。</p>
 */
public final class PanelCalculator {

    private PanelCalculator() {
    }

    /** 计算后的面板快照（只读）。 */
    public record Panel(
            BigDecimal maxHP, BigDecimal curHP, BigDecimal MP, BigDecimal curMP,
            BigDecimal PATK, BigDecimal MATK, BigDecimal PDEF, BigDecimal MDEF,
            BigDecimal REGEN, BigDecimal MREGEN,
            BigDecimal ASPD, BigDecimal RNG, BigDecimal SPD,
            BigDecimal CRIT, BigDecimal CRITD, BigDecimal ACC, BigDecimal EVA,
            BigDecimal LIFESTEAL, BigDecimal BULLETMP, boolean silenced,
            BigDecimal regenHpPct) {

        /** 额外每秒回复(生命上限×regenHpPct),供战斗 regen 使用。 */
        public BigDecimal regenHpPerSec() {
            return Num.mul(maxHP, regenHpPct);
        }
    }

    public static Panel of(PlayerState s) {
        Life L = s.life;
        boolean silenced = isSilenced(s);
        boolean burn = active(s, "burn") && !silenced;
        BigDecimal burnMul = burn ? Num.of(2) : Num.of(1);

        // 被动技能乘区 / 加区
        Map<String, BigDecimal> pmul = new HashMap<>();
        Map<String, BigDecimal> padd = new HashMap<>();
        if (!silenced) {
            for (String id : s.passives) {
                switch (id) {
                    case "iron" -> pmul.merge("PDEF", Num.of(1.2), Num::mul);
                    case "resist" -> pmul.merge("MDEF", Num.of(1.2), Num::mul);
                    case "swift" -> pmul.merge("SPD", Num.of(1.2), Num::mul);
                    case "brute" -> {
                        pmul.merge("PATK", Num.of(1.15), Num::mul);
                        padd.merge("ACC", Num.of(-0.03), Num::add);
                    }
                    case "hawkeye" -> padd.merge("ACC", Num.of(0.05), Num::add);
                    case "element" -> pmul.merge("MATK", Num.of(1.25), Num::mul);
                    default -> { }
                }
            }
        }

        // 主动增益
        BigDecimal rapidMul = (active(s, "rapid") && !silenced) ? Num.of(3) : Num.of(1);
        BigDecimal spdPotMul = (active(s, "spd_pot") && !silenced) ? Num.of(1.2) : Num.of(1);
        BigDecimal ironPotMul = (active(s, "iron_pot") && !silenced) ? Num.of(1.5) : Num.of(1);
        BigDecimal regenFood = (active(s, "regen_food") && !silenced) ? Num.of(20) : Num.ZERO;
        // v3.14 药剂乘区(全部受沉默压制,与既有药剂同语义)
        BigDecimal atkPotMul = (active(s, "atk_pot") && !silenced) ? Num.of(1.5) : Num.of(1);
        BigDecimal defPotMul = (active(s, "def_pot") && !silenced) ? Num.of(1.5) : Num.of(1);
        BigDecimal aspdPotMul = (active(s, "aspd_pot") && !silenced) ? Num.of(1.5) : Num.of(1);
        BigDecimal critPot = (active(s, "crit_pot") && !silenced) ? Num.of(0.10) : Num.ZERO;
        BigDecimal critdPot = (active(s, "critd_pot") && !silenced) ? Num.of(0.50) : Num.ZERO;
        BigDecimal lifestealPot = (active(s, "lifesteal_pot") && !silenced) ? Num.of(0.10) : Num.ZERO;
        BigDecimal accPot = (active(s, "acc_pot") && !silenced) ? Num.of(0.10) : Num.ZERO;
        BigDecimal evaPot = (active(s, "eva_pot") && !silenced) ? Num.of(0.10) : Num.ZERO;
        BigDecimal mregenPot = (active(s, "mregen_pot") && !silenced) ? Num.of(10) : Num.ZERO;

        // 装备：底子 + 词条 + 强化
        Map<String, BigDecimal> flat = new HashMap<>();
        Map<String, BigDecimal> pct = new HashMap<>();
        Map<String, BigDecimal> pctHp = new HashMap<>();   // 每秒回复 X% 生命上限类词条
        for (PlayerState.Item it : s.equipped.values()) {
            if (it == null) {
                continue;
            }
            BigDecimal enh = Num.of(1 + (double) it.enhance * GameConst.ENHANCE_STAT_BONUS);
            for (Map.Entry<String, BigDecimal> e : it.base.entrySet()) {
                // BULLET_MP 是每发耗蓝的「成本」而非战力:强化只放大战力,成本不吃强化乘区
                // (否则魔弹枪越强化越哑火,与吞噬合并的同口径修复,见 GameService.devourInto)
                BigDecimal v = "BULLET_MP".equals(e.getKey())
                        ? e.getValue()
                        : Num.mul(e.getValue(), enh);
                flat.merge(e.getKey(), v, Num::add);
            }
            for (PlayerState.AffixInst a : it.affixes) {
                if ("pct_hp".equals(a.type)) {
                    // 特例:每秒回复 X% 生命上限(回春戒「生命泉涌」)——不进常规乘/加区
                    pctHp.merge(a.key, a.val, Num::add);
                    continue;
                }
                if ("flat".equals(a.type)) {
                    flat.merge(a.key, a.val, Num::add);
                } else {
                    pct.merge(a.key, a.val, Num::add);
                }
                if (a.sideKey != null && a.sideVal != null) {
                    pct.merge(a.sideKey, a.sideVal, Num::add);
                }
            }
        }
        BigDecimal regenHpPct = pctHp.getOrDefault("REGEN_HP", Num.ZERO);

        // 无限属性点天赋(statlord):自由加点并入 flat 区——全部属性走既有读法
        // (含 min(BLESS_CAP) 封顶),加点单位见 GameConst.STAT_POINT_STEP
        for (Map.Entry<String, BigDecimal> e : s.statPoints.entrySet()) {
            if (e.getValue() != null && e.getValue().signum() > 0) {
                flat.merge(e.getKey(), e.getValue(), Num::add);
            }
        }

        // 天赋加成（觉醒抽得，永久生效）
        applyTalent(s, flat, pct);

        BigDecimal maxHP = pctApply(L.maxHP, flat, pct, "maxHP");
        BigDecimal curHP = Num.add(L.curHP, flat.getOrDefault("curHP", Num.ZERO));

        return new Panel(
                maxHP,
                curHP,
                pctApply(L.MP, flat, pct, "MP"),
                Num.add(L.curMP, flat.getOrDefault("curMP", Num.ZERO)),
                Num.mul(Num.mul(Num.mul(pctApply(L.PATK, flat, pct, "PATK"), pmul.getOrDefault("PATK", Num.of(1))), burnMul), atkPotMul),
                Num.mul(Num.mul(Num.mul(pctApply(L.MATK, flat, pct, "MATK"), pmul.getOrDefault("MATK", Num.of(1))), burnMul), atkPotMul),
                Num.mul(Num.mul(Num.mul(pctApply(L.PDEF, flat, pct, "PDEF"), pmul.getOrDefault("PDEF", Num.of(1))), ironPotMul), defPotMul),
                Num.mul(Num.mul(Num.mul(pctApply(L.MDEF, flat, pct, "MDEF"), pmul.getOrDefault("MDEF", Num.of(1))), ironPotMul), defPotMul),
                Num.mul(Num.add(pctApply(L.REGEN, flat, pct, "REGEN"), regenFood), pmul.getOrDefault("REGEN", Num.of(1))),
                Num.add(pctApply(L.MREGEN, flat, pct, "MREGEN"), mregenPot),
                Num.mul(Num.mul(Num.mul(aspdWithBonus(s.fixed.ASPD, flat, pct), rapidMul), spdPotMul), aspdPotMul),
                Num.add(s.fixed.RNG, flat.getOrDefault("RNG", Num.ZERO)),
                Num.mul(Num.add(Num.add(s.fixed.SPD, flat.getOrDefault("SPD", Num.ZERO)),
                        Num.mul(s.fixed.SPD, pct.getOrDefault("SPD", Num.ZERO))), pmul.getOrDefault("SPD", Num.of(1))),
                Num.min(GameConst.BLESS_CAP.get("CRIT"), Num.add(Num.add(s.fixed.CRIT, flat.getOrDefault("CRIT", Num.ZERO)), critPot)),
                Num.add(Num.add(s.fixed.CRITD, flat.getOrDefault("CRITD", Num.ZERO)), critdPot),
                Num.min(GameConst.BLESS_CAP.get("ACC"),
                        Num.add(Num.add(Num.add(s.fixed.ACC, flat.getOrDefault("ACC", Num.ZERO)), padd.getOrDefault("ACC", Num.ZERO)), accPot)),
                Num.min(GameConst.BLESS_CAP.get("EVA"), Num.add(Num.add(s.fixed.EVA, flat.getOrDefault("EVA", Num.ZERO)), evaPot)),
                Num.add(flat.getOrDefault("LIFESTEAL", Num.ZERO), lifestealPot),
                // 枪械：纯魔法弹每发耗蓝标记（BULLET_MP，来自武器 base_stats；非枪/物理弹武器为 0）
                flat.getOrDefault("BULLET_MP", Num.ZERO),
                silenced,
                regenHpPct);
    }

    private static BigDecimal aspdWithBonus(BigDecimal base, Map<String, BigDecimal> flat, Map<String, BigDecimal> pct) {
        return Num.add(Num.add(base, flat.getOrDefault("ASPD", Num.ZERO)),
                Num.mul(base, pct.getOrDefault("ASPD", Num.ZERO)));
    }

    private static BigDecimal pctApply(BigDecimal base, Map<String, BigDecimal> flat,
                                       Map<String, BigDecimal> pct, String key) {
        BigDecimal v = Num.add(base, flat.getOrDefault(key, Num.ZERO));
        return Num.mul(v, Num.add(Num.of(1), pct.getOrDefault(key, Num.ZERO)));
    }

    private static void applyTalent(PlayerState s, Map<String, BigDecimal> flat, Map<String, BigDecimal> pct) {
        if (s.talent == null) {
            return;
        }
        switch (s.talent) {
            case "might" -> pct.merge("PATK", Num.of(0.10), Num::add);
            case "shell" -> {
                pct.merge("PDEF", Num.of(0.10), Num::add);
                pct.merge("MDEF", Num.of(0.10), Num::add);
            }
            case "wind" -> pct.merge("SPD", Num.of(0.15), Num::add);
            default -> { }
        }
    }

    public static boolean active(PlayerState s, String buff) {
        Double v = s.buffs.get(buff);
        return v != null && v > 0;
    }

    /** 沉默：Boss 减益生效时，被动技能与增益全部失效。 */
    public static boolean isSilenced(PlayerState s) {
        return active(s, "silence");
    }

    // ==================== 赋能步长 ====================

    /** 每拳赋能步长 = 基础步长 × (1 + 强化等级)。 */
    public static BigDecimal blessStep(PlayerState s, String key) {
        PlayerState.Bless b = s.blessings.get(key);
        if (b == null) {
            return null;
        }
        BigDecimal base = GameConst.BLESS_STEP.get(key);
        return Num.mul(base, Num.of(1 + b.lv));
    }

    /** 本世普攻成长描述行（界面用）。 */
    public static Map<String, Object> blessLine(PlayerState s) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, PlayerState.Bless> e : s.blessings.entrySet()) {
            String key = e.getKey();
            m.put(key, Num.plain(blessStep(s, key)));
        }
        return m;
    }

    /** 是否已达上限（百分比类赋能）。 */
}
