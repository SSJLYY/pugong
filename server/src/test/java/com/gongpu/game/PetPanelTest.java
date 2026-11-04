package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第九批·宠物面板加成（第 3 轮）。 */
class PetPanelTest {

    @Test
    void noPetNoBonus() {
        PlayerState s = NewState.create();
        assertTrue(PanelCalculator.petBonus(s).isEmpty());
    }

    @Test
    void levelScalesBonus() {
        PlayerState s = NewState.create();
        s.pet = "pet_cat";
        s.petLevels.put("pet_cat", 1);
        assertEquals(0, new BigDecimal("30").compareTo(PanelCalculator.petBonus(s).get("PATK")));
        s.petLevels.put("pet_cat", 11);
        // 1 + 0.15*10 = 2.5 倍 → 75
        assertEquals(0, new BigDecimal("75").compareTo(PanelCalculator.petBonus(s).get("PATK")));
    }

    @Test
    void bonusReachesPanel() {
        PlayerState s = NewState.create();
        BigDecimal base = PanelCalculator.of(s).PATK();
        s.pet = "pet_cat";
        s.petLevels.put("pet_cat", 1);
        BigDecimal with = PanelCalculator.of(s).PATK();
        assertEquals(0, base.add(new BigDecimal("30")).compareTo(with));
    }
}
