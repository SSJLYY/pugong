package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第九批·宠物成就（第 8 轮）。 */
class PetAchievementTest {

    @Test
    void petProgress() {
        PlayerState s = NewState.create();
        assertArrayEquals(new long[]{0, 1}, Achievements.progressOf(s, "pet_first"));
        s.petsOwned.add("pet_cat");
        assertArrayEquals(new long[]{1, 1}, Achievements.progressOf(s, "pet_first"));
        s.petLevels.put("pet_cat", 25);
        assertArrayEquals(new long[]{25, 25}, Achievements.progressOf(s, "pet_25"));
        assertEquals(2_000L, Achievements.rewardOf("pet_25"));
    }

    @Test
    void countsNow68() {
        assertEquals(72, Achievements.ALL.size());
    }
}
