package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 第六批技能扩充（第 25~30 轮）逐轮断言。
 */
class SkillBatch6Test {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void round25DiamondBody() {
        PlayerState s = NewState.create();
        s.life.set("PDEF", Num.of(100));
        s.life.set("MDEF", Num.of(100));
        PanelCalculator.Panel p0 = PanelCalculator.of(s);
        s.passives.add("diamond_body");
        PanelCalculator.Panel p1 = PanelCalculator.of(s);
        assertEquals(0, p1.PDEF().compareTo(Num.mul(p0.PDEF(), Num.of(1.15))), "金刚体物防应 ×1.15");
        assertEquals(0, p1.MDEF().compareTo(Num.mul(p0.MDEF(), Num.of(1.15))), "金刚体魔防应 ×1.15");
    }
}
