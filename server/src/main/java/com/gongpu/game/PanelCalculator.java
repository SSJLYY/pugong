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
                    case "diamond_body" -> {
                        pmul.merge("PDEF", Num.of(1.15), Num::mul);
                        pmul.merge("MDEF", Num.of(1.15), Num::mul);
                    }
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
        // 饱食回血：基础 20/秒；铁胃天赋（第七批）×1.5（天赋不受沉默压制，但 buff 本身仍受压制）
        BigDecimal regenFood = (active(s, "regen_food") && !silenced)
                ? Num.of(GameConst.FOOD_REGEN * ("iron_gut".equals(s.talent) ? GameConst.IRON_GUT_MUL : 1.0))
                : Num.ZERO;
        // v3.14 药剂乘区(全部受沉默压制,与既有药剂同语义)
        BigDecimal atkPotMul = (active(s, "atk_pot") && !silenced) ? Num.of(1.5) : Num.of(1);
        BigDecimal defPotMul = (active(s, "def_pot") && !silenced) ? Num.of(1.5) : Num.of(1);
        BigDecimal aspdPotMul = (active(s, "aspd_pot") && !silenced) ? Num.of(1.5) : Num.of(1);
        // 时滞减益（怪物词条）：黏液缠身攻速打折；减益不走沉默压制口径
        BigDecimal slowMul = active(s, "slow") ? Num.of(GameConst.TRAIT_SLOW_ASPD_MUL) : Num.of(1);
        // 寒霜减益（第七批，怪物词条）：与时滞同乘区叠加
        BigDecimal frostMul = active(s, "frost") ? Num.of(GameConst.TRAIT_FROST_ASPD_MUL) : Num.of(1);
        BigDecimal critPot = (active(s, "crit_pot") && !silenced) ? Num.of(0.10) : Num.ZERO;
        BigDecimal critdPot = (active(s, "critd_pot") && !silenced) ? Num.of(0.50) : Num.ZERO;
        // 狂怒药剂（第七批）：复合暴击——暴击率与暴伤同补，与锐眼/残暴可叠加
        BigDecimal rageCrit = (active(s, "rage_pot") && !silenced) ? Num.of(0.10) : Num.ZERO;
        BigDecimal rageCritd = (active(s, "rage_pot") && !silenced) ? Num.of(0.30) : Num.ZERO;
        // 虚空精华（第九批）：暴伤 +30%（与狂怒同加区）
        BigDecimal voidCritd = (active(s, "void_pot") && !silenced) ? Num.of(0.30) : Num.ZERO;
        BigDecimal voidAcc = (active(s, "void_pot") && !silenced) ? Num.of(0.05) : Num.ZERO;
        // 星尘甘露（第九批）：暴击 +6%（锐眼同加区）、攻速平直 +1.5（时之砂同口径，进乘区前）
        BigDecimal stardustCrit = (active(s, "stardust_pot") && !silenced) ? Num.of(0.06) : Num.ZERO;
        BigDecimal stardustAspd = (active(s, "stardust_pot") && !silenced) ? Num.of(1.5) : Num.ZERO;
        // 混沌精华（第十批）：暴伤 +40%（虚空同加区）、闪避 +5%（幻影同加区）
        BigDecimal chaosCritd = (active(s, "chaos_pot") && !silenced) ? Num.of(0.40) : Num.ZERO;
        BigDecimal chaosEva = (active(s, "chaos_pot") && !silenced) ? Num.of(0.05) : Num.ZERO;
        // 凤凰之血（第十批）：暴伤 +50%（同加区）、吸血 +5%（吸血药剂同加区）
        BigDecimal phoenixCritd = (active(s, "phoenix_pot") && !silenced) ? Num.of(0.50) : Num.ZERO;
        BigDecimal phoenixSteal = (active(s, "phoenix_pot") && !silenced) ? Num.of(0.05) : Num.ZERO;
        // 被动·时之体（第十批）：平直攻速 +0.08（进乘区前，被动受沉默压制）
        BigDecimal chronoAspd = (s.passives.contains("chrono_body") && !silenced) ? Num.of(0.08) : Num.ZERO;
        // 时之砂（第七批）：平直攻速加算（进乘区之前，与词条平直攻速同口径）
        BigDecimal sandAspd = (active(s, "sand_pot") && !silenced) ? Num.of(0.2) : Num.ZERO;
        BigDecimal lifestealPot = (active(s, "lifesteal_pot") && !silenced) ? Num.of(0.10) : Num.ZERO;
        BigDecimal bloodlustBuff = (active(s, "bloodlust") && !silenced) ? Num.of(0.20) : Num.ZERO;
        BigDecimal accPot = (active(s, "acc_pot") && !silenced) ? Num.of(0.10) : Num.ZERO;
        // 凝神药剂（第八批）：命中平直加算，与鹰眼药剂同区叠加
        BigDecimal focusPot = (active(s, "focus_pot") && !silenced) ? Num.of(0.12) : Num.ZERO;
        // 生机药剂（第八批）：持续回复平直加算，与回春药剂同区叠加
        BigDecimal vigorPot = (active(s, "vigor_pot") && !silenced) ? Num.of(150) : Num.ZERO;
        BigDecimal evaPot = (active(s, "eva_pot") && !silenced) ? Num.of(0.10) : Num.ZERO;
        BigDecimal mregenPot = (active(s, "mregen_pot") && !silenced) ? Num.of(10) : Num.ZERO;
        // 第四批新维度药剂：持续回复/射程/移速（同受沉默压制）
        BigDecimal regenPot = (active(s, "regen_pot") && !silenced) ? Num.of(25) : Num.ZERO;
        BigDecimal rngPot = (active(s, "rng_pot") && !silenced) ? Num.of(1) : Num.ZERO;
        BigDecimal fleetPot = (active(s, "fleet_pot") && !silenced) ? Num.of(40) : Num.ZERO;
        // 第六批药剂：生命上限乘区（同受沉默压制）
        BigDecimal vitalPotMul = (active(s, "vital_pot") && !silenced) ? Num.of(1.3) : Num.of(1);
        BigDecimal guardPot = (active(s, "guard_pot") && !silenced) ? Num.of(15) : Num.ZERO;
        BigDecimal mightPot = (active(s, "might_pot") && !silenced) ? Num.of(20) : Num.ZERO;
        BigDecimal spiritPot = (active(s, "spirit_pot") && !silenced) ? Num.of(300) : Num.ZERO;
        BigDecimal regenPotL = (active(s, "regen_pot_l") && !silenced) ? Num.of(100) : Num.ZERO;
        // 被动·鹰骨：射程 +1（与远眺药剂同加区，被动受沉默压制）
        BigDecimal rngPassive = (!silenced && s.passives.contains("eagle_bone")) ? Num.of(1) : Num.ZERO;

        // 装备：底子 + 词条 + 强化
        Map<String, BigDecimal> flat = new HashMap<>();
        Map<String, BigDecimal> pct = new HashMap<>();
        Map<String, BigDecimal> pctHp = new HashMap<>();   // 每秒回复 X% 生命上限类词条
        for (PlayerState.Item it : s.equipped.values()) {
            if (it == null) {
                continue;
            }
            // 强化/淬炼/升星三乘区（淬炼 +4%/级、升星第十批 +3%/星，各自独立计层）；BULLET_MP 成本不吃任一乘区（下方分支处理）
            BigDecimal enh = Num.mul(Num.mul(
                    Num.of(1 + (double) it.enhance * GameConst.ENHANCE_STAT_BONUS),
                    Num.of(1 + (double) it.temper * GameConst.TEMPER_STAT_BONUS)),
                    Num.of(1 + (double) it.star * GameConst.STAR_STAT_BONUS));
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
            // 镶嵌宝石（第八批）：flat 加成与词条同区（装备来源，不受沉默压制）；
            // 天赋·宝石亲和再乘 1.25（天赋为先天禀赋，同不受沉默压制）
            if (it.gems != null) {
                BigDecimal gemMul = "gem_affinity".equals(s.talent)
                        ? Num.of(GameConst.GEM_AFFINITY_MUL) : Num.of(1);
                for (String g : it.gems) {
                    GameConst.GemDef gd = GameConst.GEMS.get(g);
                    if (gd != null) {
                        gd.stats().forEach((k, v) -> flat.merge(k, Num.mul(v, gemMul), Num::add));
                    }
                }
            }
        }
        BigDecimal regenHpPct = pctHp.getOrDefault("REGEN_HP", Num.ZERO);

        // 套装加成（第七批）：已穿套装件数达标的 flat 加成，与装备词条同区（不受沉默压制）
        Sets.activeBonus(s).forEach((k, v) -> flat.merge(k, v, Num::add));

        // 宠物伙伴（第九批）：出战宠物按等级放大 flat 加成（伙伴来源，不受沉默压制）；
        // 被动·宠物亲和再乘 1.3（被动本身受沉默压制）；羁绊倍率（第十批，总等级档位）同乘、不受压制
        int petTotalLevels = s.petLevels.values().stream().mapToInt(Integer::intValue).sum();
        BigDecimal petMul = Num.mul(
                (!silenced && s.passives.contains("pet_bond")) ? Num.of(GameConst.PET_BOND_MUL) : Num.of(1),
                Num.of(GameConst.petBondMul(petTotalLevels, "chaos_heart".equals(s.talent))));
        petBonus(s).forEach((k, v) -> flat.merge(k, Num.mul(v, petMul), Num::add));

        // 无限属性点天赋(statlord):自由加点并入 flat 区——全部属性走既有读法
        // (含 min(BLESS_CAP) 封顶),加点单位见 GameConst.STAT_POINT_STEP
        for (Map.Entry<String, BigDecimal> e : s.statPoints.entrySet()) {
            if (e.getValue() != null && e.getValue().signum() > 0) {
                flat.merge(e.getKey(), e.getValue(), Num::add);
            }
        }

        // 天赋加成（觉醒抽得，永久生效）
        applyTalent(s, flat, pct);

        BigDecimal maxHP = Num.mul(pctApply(L.maxHP, flat, pct, "maxHP"), vitalPotMul);
        BigDecimal curHP = Num.add(L.curHP, flat.getOrDefault("curHP", Num.ZERO));

        // 被动·血怒（第七批）：残血狂怒——当前生命低于 30% 上限时双攻 ×1.15
        // （需在 maxHP/curHP 定型后判定，故不走前置被动乘区循环；沉默压制口径同其他被动）
        if (!silenced && s.passives.contains("bloodrage")
                && Num.gt(maxHP, Num.ZERO)
                && Num.lt(Num.div(curHP, maxHP), Num.of(GameConst.BLOODRAGE_HP_LINE))) {
            pmul.merge("PATK", Num.of(GameConst.BLOODRAGE_ATK_MUL), Num::mul);
            pmul.merge("MATK", Num.of(GameConst.BLOODRAGE_ATK_MUL), Num::mul);
        }

        return new Panel(
                maxHP,
                curHP,
                Num.add(pctApply(L.MP, flat, pct, "MP"), spiritPot),
                Num.add(L.curMP, flat.getOrDefault("curMP", Num.ZERO)),
                Num.add(Num.mul(Num.mul(Num.mul(pctApply(L.PATK, flat, pct, "PATK"), pmul.getOrDefault("PATK", Num.of(1))), burnMul), atkPotMul), mightPot),
                Num.add(Num.mul(Num.mul(Num.mul(pctApply(L.MATK, flat, pct, "MATK"), pmul.getOrDefault("MATK", Num.of(1))), burnMul), atkPotMul), mightPot),
                Num.add(Num.mul(Num.mul(Num.mul(pctApply(L.PDEF, flat, pct, "PDEF"), pmul.getOrDefault("PDEF", Num.of(1))), ironPotMul), defPotMul), guardPot),
                Num.add(Num.mul(Num.mul(Num.mul(pctApply(L.MDEF, flat, pct, "MDEF"), pmul.getOrDefault("MDEF", Num.of(1))), ironPotMul), defPotMul), guardPot),
                Num.mul(Num.add(Num.add(Num.add(Num.add(pctApply(L.REGEN, flat, pct, "REGEN"), regenFood), regenPot), regenPotL), vigorPot), pmul.getOrDefault("REGEN", Num.of(1))),
                Num.add(pctApply(L.MREGEN, flat, pct, "MREGEN"), mregenPot),
                Num.mul(Num.mul(Num.mul(Num.mul(Num.mul(Num.add(Num.add(Num.add(aspdWithBonus(s.fixed.ASPD, flat, pct), sandAspd), stardustAspd), chronoAspd), rapidMul), spdPotMul), aspdPotMul), slowMul), frostMul),
                Num.add(Num.add(Num.add(s.fixed.RNG, flat.getOrDefault("RNG", Num.ZERO)), rngPot), rngPassive),
                Num.mul(Num.add(Num.add(s.fixed.SPD, Num.add(flat.getOrDefault("SPD", Num.ZERO), fleetPot)),
                        Num.mul(s.fixed.SPD, pct.getOrDefault("SPD", Num.ZERO))), pmul.getOrDefault("SPD", Num.of(1))),
                Num.min(GameConst.BLESS_CAP.get("CRIT"), Num.add(Num.add(Num.add(Num.add(s.fixed.CRIT, flat.getOrDefault("CRIT", Num.ZERO)), critPot), rageCrit), stardustCrit)),
                Num.add(Num.add(Num.add(Num.add(Num.add(Num.add(s.fixed.CRITD, flat.getOrDefault("CRITD", Num.ZERO)), critdPot), rageCritd), voidCritd), chaosCritd), phoenixCritd),
                Num.min(GameConst.BLESS_CAP.get("ACC"),
                        Num.add(Num.add(Num.add(Num.add(Num.add(s.fixed.ACC, flat.getOrDefault("ACC", Num.ZERO)), padd.getOrDefault("ACC", Num.ZERO)), accPot), focusPot), voidAcc)),
                Num.min(GameConst.BLESS_CAP.get("EVA"), Num.add(Num.add(Num.add(s.fixed.EVA, flat.getOrDefault("EVA", Num.ZERO)), evaPot), chaosEva)),
                Num.add(Num.add(Num.add(flat.getOrDefault("LIFESTEAL", Num.ZERO), lifestealPot), bloodlustBuff), phoenixSteal),
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

    /** 出战宠物的面板加成（第九批）：基础属性 × (1 + 0.15 × (等级 − 1))；无出战宠物为空。 */
    public static Map<String, BigDecimal> petBonus(PlayerState s) {
        Map<String, BigDecimal> out = new HashMap<>();
        if (s.pet == null) {
            return out;
        }
        GameConst.PetDef def = GameConst.PETS.get(s.pet);
        if (def == null) {
            return out;
        }
        int lv = s.petLevels.getOrDefault(s.pet, 1);
        BigDecimal mul = Num.of(1 + GameConst.PET_LEVEL_MUL * (lv - 1));
        def.stats().forEach((k, v) -> out.put(k, Num.mul(v, mul)));
        return out;
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
