package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 第七批第 81 轮：狂怒药剂——复合暴击（暴击率 +10% 且暴伤 +30%），与锐眼/残暴叠加，沉默压制。
 */
class RagePotionTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void rageBoostsCritAndCritd() {
        PlayerState s = NewState.create();
        s.fixed.CRIT = new java.math.BigDecimal("0.05");
        s.fixed.CRITD = new java.math.BigDecimal("1.50");
        PanelCalculator.Panel base = PanelCalculator.of(s);
        assertEquals(0, base.CRIT().compareTo(new java.math.BigDecimal("0.05")));
        assertEquals(0, base.CRITD().compareTo(new java.math.BigDecimal("1.50")));

        s.buffs.put("rage_pot", 90.0);
        PanelCalculator.Panel p = PanelCalculator.of(s);
        assertEquals(0, p.CRIT().compareTo(new java.math.BigDecimal("0.15")), "狂怒应 +10% 暴击率");
        assertEquals(0, p.CRITD().compareTo(new java.math.BigDecimal("1.80")), "狂怒应 +30% 暴击伤害");

        // 与锐眼叠加：暴击率再 +10%
        s.buffs.put("crit_pot", 90.0);
        assertEquals(0, PanelCalculator.of(s).CRIT().compareTo(new java.math.BigDecimal("0.25")));
    }

    @Test
    void rageSuppressedBySilence() {
        PlayerState s = NewState.create();
        s.fixed.CRIT = new java.math.BigDecimal("0.05");
        s.fixed.CRITD = new java.math.BigDecimal("1.50");
        s.buffs.put("rage_pot", 90.0);
        s.buffs.put("silence", 10.0);
        PanelCalculator.Panel p = PanelCalculator.of(s);
        assertEquals(0, p.CRIT().compareTo(new java.math.BigDecimal("0.05")), "沉默应压制狂怒");
        assertEquals(0, p.CRITD().compareTo(new java.math.BigDecimal("1.50")));
    }

    @Test
    void rageDefRegistered() {
        GameConst.PotionDef d = GameConst.POTIONS.get("rage");
        assertNotNull(d);
        assertEquals("rage_pot", d.buff());
        assertEquals(750, d.price());
    }
}
