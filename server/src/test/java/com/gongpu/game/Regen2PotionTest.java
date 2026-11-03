package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第六批第 8 轮：回春药剂——60s 内回血 +100/秒（与生生药剂同区叠加）。 */
class Regen2PotionTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void regenPlus100AndStacks() {
        PlayerState s = NewState.create();
        java.math.BigDecimal base = PanelCalculator.of(s).REGEN();
        s.buffs.put("regen_pot_l", 60.0);
        assertEquals(0, PanelCalculator.of(s).REGEN().subtract(base).compareTo(Num.of(100)),
                "回春应 +100/秒");
        s.buffs.put("regen_pot", 120.0);
        assertEquals(0, PanelCalculator.of(s).REGEN().subtract(base).compareTo(Num.of(125)),
                "与生生（+25）应叠加到 +125");
    }
}
