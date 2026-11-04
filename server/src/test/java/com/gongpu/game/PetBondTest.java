package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第九批·被动宠物亲和（第 24 轮）：宠物加成 ×1.3。 */
class PetBondTest {

    @Test
    void bondMultipliesPetBonus() {
        PlayerState s = NewState.create();
        s.pet = "pet_cat";
        s.petLevels.put("pet_cat", 1);
        BigDecimal base = PanelCalculator.of(s).PATK();
        s.passives.add("pet_bond");
        BigDecimal bonded = PanelCalculator.of(s).PATK();
        // 宠物 PATK 30 × 1.3 = 39 → 面板多 9
        assertEquals(0, new BigDecimal("9").compareTo(bonded.subtract(base)));
    }
}
