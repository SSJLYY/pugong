package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 第六批第 48 轮：成就进度——可量化成就返回 [cur,target]，事件型返回 null。 */
class AchProgressTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void countableProgress() {
        PlayerState s = NewState.create();
        s.stats.kills = java.math.BigDecimal.valueOf(40);
        s.stats.dismantles = 30;
        s.stats.bossKillCount = 7;
        s.usedPotions.add("hp_s");
        s.usedPotions.add("mp");
        assertArrayEquals(new long[]{40, 100}, Achievements.progressOf(s, "kill_100"));
        assertArrayEquals(new long[]{30, 100}, Achievements.progressOf(s, "dismantle_100"));
        assertArrayEquals(new long[]{7, 50}, Achievements.progressOf(s, "boss_50"));
        assertArrayEquals(new long[]{2, 25}, Achievements.progressOf(s, "potion_25"));
    }

    @Test
    void eventAchievementsHaveNoProgress() {
        PlayerState s = NewState.create();
        assertNull(Achievements.progressOf(s, "first_kill"));
        assertNull(Achievements.progressOf(s, "talent"));
        assertNull(Achievements.progressOf(s, "wear_epic"));
    }
}
