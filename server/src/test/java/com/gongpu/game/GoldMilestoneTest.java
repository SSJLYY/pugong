package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第八批第 45 轮：金币里程碑——跨过 10^k 播报一次且只播一次；一次跨多档逐档播报；
 * 未达门槛不播报、不抬升标记。
 */
class GoldMilestoneTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void announcesOncePerThreshold() {
        PlayerState s = NewState.create();
        s.gold = Num.of(9999);
        GameEngine.checkGoldMilestone(s);
        assertTrue(s.events.isEmpty(), "未过万金不应播报");
        assertEquals(3, s.goldMilestone);

        s.gold = Num.of(10000);
        GameEngine.checkGoldMilestone(s);
        assertEquals(1, s.events.size());
        assertTrue(s.events.get(0).contains("10000"));
        assertEquals(4, s.goldMilestone);

        GameEngine.checkGoldMilestone(s);
        assertEquals(1, s.events.size(), "同档不应重复播报");

        s.gold = Num.of(2500000);
        GameEngine.checkGoldMilestone(s);
        // 跨过 10^5 与 10^6 两档
        assertEquals(3, s.events.size());
        assertEquals(6, s.goldMilestone);
    }
}
