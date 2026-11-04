package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第九批·词条裂空（第 22 轮）：玩家闪避减半的单点口径与实战效果。 */
class TraitRendTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void effEvaHalvedOnlyForRend() {
        PlayerState s = NewState.create();
        s.fixed.EVA = new BigDecimal("0.40");
        PanelCalculator.Panel p = PanelCalculator.of(s);
        PlayerState.MobState m = new PlayerState.MobState();
        m.trait = "void_rend";
        assertEquals(0, new BigDecimal("0.20").compareTo(GameEngine.effEva(p, m)));
        m.trait = "frenzy";
        assertEquals(0, new BigDecimal("0.40").compareTo(GameEngine.effEva(p, m)));
    }

    @Test
    void rendMobHitsMoreOften() {
        PlayerState s = NewState.create();
        s.life.maxHP = new BigDecimal("10000000");
        s.life.curHP = s.life.maxHP;
        s.life.set("PDEF", Num.ZERO);
        s.fixed.EVA = new BigDecimal("0.90");
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "testmob";
        m.name = "裂空怪";
        m.maxHP = new BigDecimal("100000");
        m.curHP = m.maxHP;
        m.PATK = new BigDecimal("100");
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        m.ASPD = 1;
        m.RNG = 0;
        m.SPD = 1;
        m.trait = "void_rend";
        s.mob = m;
        GameEngine.mobAttack(s, PanelCalculator.of(s), 1.0);
        BigDecimal loss = s.life.maxHP.subtract(s.life.curHP);
        // 闪避 0.9 → 裂空后 0.45，命中期望 0.55 次 × 100 = 55（浮点帧容差）
        assertTrue(loss.doubleValue() > 40 && loss.doubleValue() < 70, "裂空应显著提高命中，实际掉血=" + loss);
    }
}
