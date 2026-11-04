package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第九批第 81 轮：星尘甘露——暴击 +6% 与平直攻速 +1.5 进面板（沉默压制、与时之砂同加区）。 */
class StardustPotionTest {

    @Test
    void stardustBuffAppliesToPanel() {
        PlayerState s = NewState.create();
        var base = PanelCalculator.of(s);
        s.buffs.put("stardust_pot", 90.0);
        var buffed = PanelCalculator.of(s);
        assertEquals(0, base.CRIT().add(new BigDecimal("0.06")).compareTo(buffed.CRIT()),
                "暴击应 +0.06");
        assertTrue(buffed.ASPD().subtract(base.ASPD()).compareTo(new BigDecimal("1.4")) > 0,
                "攻速应约 +1.5（乘区前平直加算），实际差 " + buffed.ASPD().subtract(base.ASPD()));
        assertTrue(GameConst.POTIONS.containsKey("stardust"), "POTIONS 镜像须含 stardust");
        assertEquals(1200, GameConst.POTIONS.get("stardust").price());
    }
}
