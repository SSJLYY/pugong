package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 第九批·主动虚空斩（第 23 轮）：下一击 ×4 一次性消费。 */
class VoidSlashTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fighter() {
        PlayerState s = NewState.create();
        s.life.set("PATK", new BigDecimal("100"));
        s.fixed.CRIT = BigDecimal.ZERO;
        s.fixed.CRITD = BigDecimal.ONE;
        s.life.curMP = new BigDecimal("1000");
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "dummy";
        m.name = "木桩";
        m.maxHP = new BigDecimal("100000000");
        m.curHP = m.maxHP;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        s.mob = m;
        return s;
    }

    @Test
    void castThenHitQuadruplesOnce() {
        PlayerState s = fighter();
        GameService.castSkill(s, "void_slash");
        PanelCalculator.Panel p = PanelCalculator.of(s);
        BigDecimal before = s.mob.curHP;
        GameEngine.dealDamage(s, p, BigDecimal.ONE, 1.0);
        BigDecimal first = before.subtract(s.mob.curHP);
        assertFalse(s.buffs.containsKey("void_slash"), "虚空斩应被消费");
        BigDecimal before2 = s.mob.curHP;
        GameEngine.dealDamage(s, p, BigDecimal.ONE, 1.0);
        BigDecimal second = before2.subtract(s.mob.curHP);
        assertEquals(0, second.multiply(new BigDecimal("4")).compareTo(first), "首击应为常击 ×4，实际 " + first + " vs " + second);
    }
}
