package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第六批第 2 轮：金钟药剂——120s 内物防魔防各 +15（面板加区）。 */
class GuardPotionTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void bothDefsPlus15() {
        PlayerState s = NewState.create();
        PanelCalculator.Panel p0 = PanelCalculator.of(s);
        s.buffs.put("guard_pot", 120.0);
        PanelCalculator.Panel p1 = PanelCalculator.of(s);
        assertEquals(0, p1.PDEF().subtract(p0.PDEF()).compareTo(Num.of(15)), "物防应 +15");
        assertEquals(0, p1.MDEF().subtract(p0.MDEF()).compareTo(Num.of(15)), "魔防应 +15");
    }
}
