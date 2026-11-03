package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第六批第 7 轮：聚灵药剂——120s 内蓝量上限 +300（面板加区）。 */
class SpiritPotionTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void mpPlus300() {
        PlayerState s = NewState.create();
        java.math.BigDecimal base = PanelCalculator.of(s).MP();
        s.buffs.put("spirit_pot", 120.0);
        assertEquals(0, PanelCalculator.of(s).MP().subtract(base).compareTo(Num.of(300)),
                "蓝量上限应 +300");
    }
}
