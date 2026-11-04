package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /** 第九批第 71 轮回归：pet_first/pet_25 曾只有 Def+progressOf、evaluate 缺判定永不解锁——连同新三项一并锁死。 */
    @Test
    void petAchievementsActuallyUnlock() {
        PlayerState s = NewState.create();
        s.petsOwned.add("pet_cat");
        assertTrue(Achievements.evaluate(s).contains("pet_first"), "领养后应解锁 pet_first");
        s.petLevels.put("pet_cat", 50);
        var newly = Achievements.evaluate(s);
        assertTrue(newly.contains("pet_25") && newly.contains("pet_50"), "50 级应连解 pet_25/pet_50");
        s.stats.petFeeds = 100;
        assertTrue(Achievements.evaluate(s).contains("pet_feed_100"), "喂食 100 应解锁 pet_feed_100");
        for (String id : com.gongpu.game.GameConst.PETS.keySet()) {
            if (!s.petsOwned.contains(id)) {
                s.petsOwned.add(id);
            }
        }
        assertTrue(Achievements.evaluate(s).contains("pet_all"), "集齐应解锁 pet_all");
        assertArrayEquals(new long[]{9, 9}, Achievements.progressOf(s, "pet_all"));
    }

    @Test
    void countsNow68() {
        assertEquals(113, Achievements.ALL.size());
    }
}
