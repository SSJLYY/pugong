package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 第九批·虚空精华（第 25 轮）：暴伤 +30%、命中 +5% 面板消费。 */
class VoidPotionTest {

    @Test
    void potionDefinedAndPanelConsumes() {
        assertNotNull(GameConst.POTIONS.get("void"));
        PlayerState s = NewState.create();
        PanelCalculator.Panel base = PanelCalculator.of(s);
        s.buffs.put("void_pot", 60.0);
        PanelCalculator.Panel buffed = PanelCalculator.of(s);
        assertEquals(0, buffed.CRITD().subtract(base.CRITD()).compareTo(new BigDecimal("0.30")));
        assertEquals(0, buffed.ACC().subtract(base.ACC()).compareTo(new BigDecimal("0.05")));
    }
}
