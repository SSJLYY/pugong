package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第六批成就扩充（第 31~34 轮）逐轮评估断言。 */
class AchievementBatch6Test {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void round31Dismantle100() {
        PlayerState s = NewState.create();
        s.stats.dismantles = 99;
        assertTrue(!Achievements.evaluate(s).contains("dismantle_100"), "99 件不应解锁");
        s.stats.dismantles = 100;
        assertTrue(Achievements.evaluate(s).contains("dismantle_100"), "100 件应解锁分解达人");
    }
}
