package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第七批第 19 轮：淬炼/洗炼成就——满级淬炼解锁 temper_5、累计洗炼 50 解锁 reroll_50，进度同口径。 */
class TemperAchievementTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void temperMaxUnlocks() {
        PlayerState s = NewState.create();
        PlayerState.Item it = new PlayerState.Item();
        it.name = "剑";
        it.slot = "weapon";
        it.temper = 4;
        s.equipBag.add(it);
        assertFalse(Achievements.evaluate(s).contains("temper_5"), "4 级未满不应解锁");
        assertArrayEquals(new long[]{4, 5}, Achievements.progressOf(s, "temper_5"));
        it.temper = 5;
        assertTrue(Achievements.evaluate(s).contains("temper_5"));
    }

    @Test
    void equippedTemperCountsToo() {
        PlayerState s = NewState.create();
        PlayerState.Item it = new PlayerState.Item();
        it.name = "甲";
        it.slot = "chest";
        it.temper = 5;
        s.equipped.put("chest", it);
        assertTrue(Achievements.evaluate(s).contains("temper_5"), "已装备的满级淬炼也应计数");
    }

    @Test
    void reroll50Unlocks() {
        PlayerState s = NewState.create();
        s.stats.rerolls = 49;
        assertFalse(Achievements.evaluate(s).contains("reroll_50"));
        assertArrayEquals(new long[]{49, 50}, Achievements.progressOf(s, "reroll_50"));
        s.stats.rerolls = 50;
        assertTrue(Achievements.evaluate(s).contains("reroll_50"));
    }
}
