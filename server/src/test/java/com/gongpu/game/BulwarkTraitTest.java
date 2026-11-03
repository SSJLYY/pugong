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

/**
 * 第六批第 21 轮：怪物词条「堡垒」——玩家对它的伤害 ×0.8，雪原起入池。
 */
class BulwarkTraitTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void damageReducedBy20Percent() {
        PlayerState s = NewState.create();
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "golem";
        m.name = "石魔像";
        m.trait = "bulwark";
        m.maxHP = new BigDecimal("100000");
        m.curHP = new BigDecimal("100000");
        m.PATK = Num.ZERO;
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        s.mob = m;
        s.zone = "snow";
        s.life.set("PATK", new BigDecimal("100"));
        s.fixed.CRIT = BigDecimal.ZERO;
        s.life.maxHP = new BigDecimal("10000");
        s.life.curHP = new BigDecimal("10000");
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("10"), 1.0);
        assertEquals(0, s.mob.curHP.compareTo(Num.of(99200)), "10 拳 ×80 = 800 伤，实际余血=" + s.mob.curHP);
    }

    @Test
    void poolFromSnow() {
        Set<String> swamp = new HashSet<>();
        Set<String> snow = new HashSet<>();
        for (int i = 0; i < 4000; i++) {
            String a = GameConst.rollMobTrait("swamp");
            if (a != null) swamp.add(a);
            String b = GameConst.rollMobTrait("snow");
            if (b != null) snow.add(b);
        }
        assertFalse(swamp.contains("bulwark"), "沼泽池不应有堡垒");
        assertTrue(snow.contains("bulwark"), "雪原池应能掷出堡垒");
        assertTrue(GameConst.MOB_TRAITS.containsKey("bulwark"), "词条定义应注册");
    }
}
