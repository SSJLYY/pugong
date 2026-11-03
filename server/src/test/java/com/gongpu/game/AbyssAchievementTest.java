package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第七批第 27 轮：深渊成就——首杀深渊怪解锁渊瞳初开、击杀无光君王解锁深渊主宰（5000 档）。 */
class AbyssAchievementTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void firstAbyssKillUnlocks() {
        PlayerState s = NewState.create();
        assertFalse(Achievements.evaluate(s).contains("abyss_first"));
        s.codex.add("rabbit");
        assertFalse(Achievements.evaluate(s).contains("abyss_first"), "浅区图鉴不算深渊首杀");
        s.codex.add("rift_hound");
        assertTrue(Achievements.evaluate(s).contains("abyss_first"));
        assertArrayEquals(new long[]{1, 1}, Achievements.progressOf(s, "abyss_first"));
    }

    @Test
    void abyssLordUnlocksWithTopReward() {
        PlayerState s = NewState.create();
        s.bossKills.add("boss_ifrit");
        assertFalse(Achievements.evaluate(s).contains("abyss_lord"), "杀火山 Boss 不算深渊主宰");
        s.bossKills.add("boss_abyss");
        assertTrue(Achievements.evaluate(s).contains("abyss_lord"));
        assertEquals(5_000L, Achievements.rewardOf("abyss_lord"), "深渊主宰应为最高档奖励");
    }
}
