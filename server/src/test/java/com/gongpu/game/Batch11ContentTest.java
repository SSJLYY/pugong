package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十一批集群九：太虚神液/太虚剑意/鸿蒙之心/太虚词条/新词条。 */
class Batch11ContentTest {

    @Test
    void taixuPotionRaisesCritAndCritd() {
        PlayerState s = NewState.create();
        BigDecimal crit0 = PanelCalculator.of(s).CRIT();
        BigDecimal critd0 = PanelCalculator.of(s).CRITD();
        s.buffs.put("taixu_pot", 30.0);
        assertEquals(0, PanelCalculator.of(s).CRIT().subtract(crit0).compareTo(new BigDecimal("0.10")),
                "太虚神液暴击率应 +0.10");
        assertEquals(0, PanelCalculator.of(s).CRITD().subtract(critd0).compareTo(new BigDecimal("0.30")),
                "太虚神液暴伤应 +0.30");
    }

    @Test
    void taixuSwordPassiveRaisesCritd() {
        PlayerState s = NewState.create();
        BigDecimal critd0 = PanelCalculator.of(s).CRITD();
        s.passives.add("taixu_sword");
        assertEquals(0, PanelCalculator.of(s).CRITD().subtract(critd0).compareTo(new BigDecimal("0.20")),
                "太虚剑意暴伤应 +0.20");
    }

    @Test
    void hongmengHeartStacksBondMul() {
        assertEquals(3.3, GameConst.petBondMul(250, true), 1e-9, "混沌之心 ×1.5");
        assertEquals(4.95, GameConst.petBondMul(250, true, true), 1e-9, "混沌+鸿蒙之心叠乘 ×2.25");
        assertEquals(1.875, GameConst.petBondMul(30, false, true), 1e-9, "鸿蒙之心一段羁绊 ×1.5");
        assertTrue(GameConst.TALENTS.containsKey("hongmeng_heart"));
        assertEquals(15, GameConst.TALENTS.size(), "天赋 14");
    }

    @Test
    void taixuTraitCutsDamageAndCounts() throws Exception {
        new com.gongpu.cfg.GameData(null, null, null, null, null, null, null);
        PlayerState s = NewState.create();
        s.fixed.CRIT = BigDecimal.ZERO;
        s.fixed.CRITD = BigDecimal.ONE;
        s.life.PATK = new BigDecimal("10000");
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "hongmeng_spirit";
        m.name = "太虚怪";
        m.maxHP = new BigDecimal("100000000");
        m.curHP = new BigDecimal("100000000");
        m.trait = "taixu";
        s.mob = m;
        PanelCalculator.Panel p = PanelCalculator.of(s);
        BigDecimal withTrait = GameEngine.expectedPerHit(s, p, m);
        m.trait = null;
        BigDecimal plain = GameEngine.expectedPerHit(s, p, m);
        assertTrue(plain.signum() > 0);
        assertEquals(0, withTrait.compareTo(plain.multiply(new BigDecimal("0.75"))),
                "太虚怪应使普攻伤害 ×0.75");

        m.trait = "taixu";
        m.curHP = BigDecimal.ONE;
        Method kill = GameEngine.class.getDeclaredMethod("onMobKilled", PlayerState.class, PlayerState.MobState.class);
        kill.setAccessible(true);
        kill.invoke(null, s, m);
        assertEquals(1, s.stats.taixuKills, "太虚击杀计数 +1");
    }

    @Test
    void newAffixesAndCounts() {
        assertTrue(GameConst.AFFIXES.stream().anyMatch(a -> "鸿蒙".equals(a.name()) && a.max() == 120));
        assertTrue(GameConst.AFFIXES.stream().anyMatch(a -> "太初".equals(a.name()) && a.max() == 50000));
        assertEquals(34, GameConst.AFFIXES.size(), "词条 32");
        assertEquals(47, GameConst.POTIONS.size(), "药剂 46");
        assertEquals(25, GameConst.PASSIVE_SKILLS.size(), "被动 23");
        assertEquals(29, GameConst.MOB_TRAITS.size(), "怪物词条 26");
    }
}
