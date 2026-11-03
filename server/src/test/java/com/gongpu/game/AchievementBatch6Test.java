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

    @Test
    void round32Craft25() {
        PlayerState s = NewState.create();
        s.stats.crafts = 24;
        assertTrue(!Achievements.evaluate(s).contains("craft_25"), "24 次不应解锁");
        s.stats.crafts = 25;
        assertTrue(Achievements.evaluate(s).contains("craft_25"), "25 次应解锁锻造大师");
        org.junit.jupiter.api.Assertions.assertEquals(2000L, Achievements.rewardOf("craft_25"),
                "锻造大师应为 2000 金奖励档");
    }

    @Test
    void round33Boss50() {
        PlayerState s = NewState.create();
        s.stats.bossKillCount = 49;
        assertTrue(!Achievements.evaluate(s).contains("boss_50"), "49 次不应解锁");
        s.stats.bossKillCount = 50;
        assertTrue(Achievements.evaluate(s).contains("boss_50"), "50 次应解锁屠龙宗师");
    }

    @Test
    void round34Potion25() {
        PlayerState s = NewState.create();
        for (int i = 0; i < 24; i++) {
            s.usedPotions.add("pot_" + i);
        }
        assertTrue(!Achievements.evaluate(s).contains("potion_25"), "24 种不应解锁");
        s.usedPotions.add("pot_24");
        assertTrue(Achievements.evaluate(s).contains("potion_25"), "25 种应解锁神农尝百草");
    }
}
