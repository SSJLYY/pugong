package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第六批第 3 轮：大力药剂——120s 内物攻魔攻各 +20（面板加区）。 */
class MightPotionTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void bothAtksPlus20() {
        PlayerState s = NewState.create();
        PanelCalculator.Panel p0 = PanelCalculator.of(s);
        s.buffs.put("might_pot", 120.0);
        PanelCalculator.Panel p1 = PanelCalculator.of(s);
        assertEquals(0, p1.PATK().subtract(p0.PATK()).compareTo(Num.of(20)), "物攻应 +20");
        assertEquals(0, p1.MATK().subtract(p0.MATK()).compareTo(Num.of(20)), "魔攻应 +20");
    }
}
