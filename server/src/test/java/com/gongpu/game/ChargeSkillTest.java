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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第七批第 45 轮：主动「冲锋」——施放后下一击 ×3，消费后消失；
 * 与蓄力重击叠乘 ×15；不施放时无加成。
 */
class ChargeSkillTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fighter() {
        PlayerState s = NewState.create();
        s.zone = "plain";
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "boar";
        m.name = "野猪";
        m.emoji = "🐗";
        m.maxHP = new BigDecimal("100000000");
        m.curHP = new BigDecimal("100000000");
        m.PATK = Num.ZERO;
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        s.mob = m;
        s.life.set("PATK", new BigDecimal("100"));
        s.fixed.CRIT = BigDecimal.ZERO;
        s.life.curMP = Num.of(100);
        s.life.maxHP = new BigDecimal("100000");
        s.life.curHP = new BigDecimal("100000");
        return s;
    }

    @Test
    void chargeTriplesNextPunchThenConsumed() {
        GameService game = new GameService();
        PlayerState s = fighter();
        game.castSkill(s, "charge");
        assertTrue(s.buffs.containsKey("charge"), "施放后应挂冲锋 buff");
        BigDecimal before = s.mob.curHP;
        GameEngine.dealDamage(s, PanelCalculator.of(s), BigDecimal.ONE, 1.0);
        assertEquals(0, before.subtract(s.mob.curHP).compareTo(new BigDecimal("300")),
                "冲锋一击应为 100×3=300");
        assertFalse(s.buffs.containsKey("charge"), "冲锋应被一击消费");
        BigDecimal before2 = s.mob.curHP;
        GameEngine.dealDamage(s, PanelCalculator.of(s), BigDecimal.ONE, 1.0);
        assertEquals(0, before2.subtract(s.mob.curHP).compareTo(new BigDecimal("100")),
                "消费后应恢复普通伤害 100");
    }

    @Test
    void chargeStacksWithHeavy() {
        GameService game = new GameService();
        PlayerState s = fighter();
        game.castSkill(s, "charge");
        game.castSkill(s, "heavy");
        BigDecimal before = s.mob.curHP;
        GameEngine.dealDamage(s, PanelCalculator.of(s), BigDecimal.ONE, 1.0);
        assertEquals(0, before.subtract(s.mob.curHP).compareTo(new BigDecimal("1500")),
                "冲锋 ×3 与重击 ×5 应叠乘为 ×15");
    }
}
