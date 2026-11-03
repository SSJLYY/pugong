package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第六批第 24 轮：怪物词条「潜行」——玩家对它的伤害 ×0.9，沼泽起入池。 */
class StealthTraitTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void damageReducedBy10Percent() {
        PlayerState s = NewState.create();
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "shade";
        m.name = "雾影";
        m.trait = "stealth";
        m.maxHP = new BigDecimal("100000");
        m.curHP = new BigDecimal("100000");
        m.PATK = Num.ZERO;
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        s.mob = m;
        s.zone = "swamp";
        s.life.set("PATK", new BigDecimal("100"));
        s.fixed.CRIT = BigDecimal.ZERO;
        s.life.maxHP = new BigDecimal("10000");
        s.life.curHP = new BigDecimal("10000");
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("10"), 1.0);
        assertEquals(0, s.mob.curHP.compareTo(Num.of(99100)), "10 拳 ×90 = 900 伤，实际余血=" + s.mob.curHP);
    }

    @Test
    void poolFromSwamp() {
        Set<String> mine = new HashSet<>();
        Set<String> swamp = new HashSet<>();
        for (int i = 0; i < 4000; i++) {
            String a = GameConst.rollMobTrait("mine");
            if (a != null) mine.add(a);
            String b = GameConst.rollMobTrait("swamp");
            if (b != null) swamp.add(b);
        }
        assertFalse(mine.contains("stealth"), "矿洞池不应有潜行");
        assertTrue(swamp.contains("stealth"), "沼泽池应能掷出潜行");
        assertEquals(11, GameConst.MOB_TRAITS.size(), "怪物词条总数应达 11（7 + 第六批 4）");
    }
}
