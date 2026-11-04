package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十批内容扩充：凤凰之血、混沌/永恒词条、混沌亲和、混沌之心、狂乱词条。 */
class Batch10ContentTest {

    @BeforeAll
    static void initData() {
        new com.gongpu.cfg.GameData(null, null, null, null, null, null, null);
    }

    @Test
    void countsGrow() {
        assertEquals(44, GameConst.POTIONS.size(), "药剂 44");
        assertEquals(30, GameConst.AFFIXES.size(), "词条 30");
        assertEquals(23, GameConst.MOB_TRAITS.size(), "怪物词条 23");
        assertEquals(13, GameConst.TALENTS.size(), "天赋 13");
        assertEquals(36, GameConst.ACTIVE_SKILLS.size() + GameConst.PASSIVE_SKILLS.size(), "技能 36");
        assertTrue(GameConst.traitPool(10).contains("chaos_frenzy"), "混沌池应含狂乱");
        assertEquals(22, GameConst.traitPool(10).size(), "混沌池 22 条");
    }

    @Test
    void phoenixPotionBoosts() {
        PlayerState s = NewState.create();
        BigDecimal cd0 = PanelCalculator.of(s).CRITD();
        BigDecimal ls0 = PanelCalculator.of(s).LIFESTEAL();
        s.buffs.put("phoenix_pot", 60.0);
        assertEquals(0, PanelCalculator.of(s).CRITD().compareTo(cd0.add(new BigDecimal("0.50"))));
        assertEquals(0, PanelCalculator.of(s).LIFESTEAL().compareTo(ls0.add(new BigDecimal("0.05"))));
    }

    @Test
    void chaosAffinityOnlyInChaos() {
        PlayerState s = NewState.create();
        s.life.PATK = new BigDecimal("1000");
        s.fixed.CRIT = BigDecimal.ZERO;
        s.fixed.CRITD = BigDecimal.ONE;
        s.passives.add("chaos_affinity");
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "chaos_wisp";
        m.maxHP = new BigDecimal("100000000");
        m.curHP = new BigDecimal("100000000");
        m.PDEF = BigDecimal.ZERO;
        m.MDEF = BigDecimal.ZERO;
        s.zone = "void";
        PanelCalculator.Panel p = PanelCalculator.of(s);
        BigDecimal outside = GameEngine.expectedPerHit(s, p, m);
        s.zone = "chaos";
        BigDecimal inside = GameEngine.expectedPerHit(s, p, m);
        assertEquals(0, inside.compareTo(outside.multiply(new BigDecimal("1.25"))),
                "混沌界内混沌亲和应 ×1.25，界外不生效");
    }

    @Test
    void chaosFrenzyHasteAndAtk() {
        assertEquals(GameConst.TRAIT_CHAOS_FRENZY_HASTE, GameEngine.traitHasteMul(traitMob("chaos_frenzy")));
        assertEquals(1.0, GameEngine.traitHasteMul(traitMob(null)));
    }

    private static PlayerState.MobState traitMob(String trait) {
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "chaos_hound";
        m.maxHP = new BigDecimal("1000");
        m.curHP = new BigDecimal("1000");
        m.trait = trait;
        return m;
    }
}
