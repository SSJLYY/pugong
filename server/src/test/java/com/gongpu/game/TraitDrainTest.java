package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** 第九批·词条噬能（第 21 轮）：命中按伤害 10% 抽蓝；裂空只在虚空池。 */
class TraitDrainTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState.MobState testMob(String trait) {
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "testmob";
        m.name = "测试怪";
        m.emoji = "👾";
        m.maxHP = new BigDecimal("100000");
        m.curHP = m.maxHP;
        m.PATK = new BigDecimal("200");
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        m.ASPD = 1;
        m.RNG = 0;
        m.SPD = 1;
        m.trait = trait;
        return m;
    }

    @Test
    void drainTakesTenPercentOfDamageAsMp() {
        PlayerState s = NewState.create();
        s.life.maxHP = new BigDecimal("100000");
        s.life.curHP = s.life.maxHP;
        s.life.set("PDEF", Num.ZERO);
        s.life.curMP = new BigDecimal("1000");
        s.mob = testMob("mana_drain");
        GameEngine.mobAttack(s, PanelCalculator.of(s), 1.0);
        BigDecimal hpLoss = s.life.maxHP.subtract(s.life.curHP);
        assertTrue(hpLoss.signum() > 0);
        BigDecimal mpLoss = new BigDecimal("1000").subtract(s.life.curMP);
        assertEquals(0, hpLoss.multiply(new BigDecimal("0.10")).compareTo(mpLoss), "抽蓝 = 实际伤害 × 10%");
    }

    @Test
    void poolsWired() {
        assertTrue(GameConst.traitPool(9).contains("void_rend"), "虚空池应含裂空");
        assertTrue(GameConst.traitPool(9).contains("mana_drain"));
        assertFalse(GameConst.traitPool(9).contains("star_eclipse"), "虚空池不应含星蚀");
        assertTrue(GameConst.traitPool(8).contains("star_eclipse"), "星界池保留星蚀");
        assertFalse(GameConst.traitPool(8).contains("void_rend"), "裂空只在虚空界");
        assertEquals(32, GameConst.MOB_TRAITS.size());
    }
}
