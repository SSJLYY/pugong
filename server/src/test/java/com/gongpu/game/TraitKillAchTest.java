package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第九批·噬能/裂空击杀成就（第 26 轮）。 */
class TraitKillAchTest {

    @Test
    void progressFollowsCounters() {
        PlayerState s = NewState.create();
        s.stats.drainKills = 10;
        s.stats.rendKills = 4;
        assertArrayEquals(new long[]{10, 10}, Achievements.progressOf(s, "drain_10"));
        assertArrayEquals(new long[]{4, 10}, Achievements.progressOf(s, "rend_10"));
        assertEquals(74, Achievements.ALL.size());
    }
}
