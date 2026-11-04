package com.gongpu.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第九批·宠物字段编解码往返（第 2 轮）。 */
class CodecBatch9Test {

    @Test
    void petFieldsSurviveRoundTrip() {
        PlayerState s = NewState.create();
        s.pet = "pet_fox";
        s.petsOwned.add("pet_cat");
        s.petsOwned.add("pet_fox");
        s.petLevels.put("pet_fox", 12);
        s.petExps.put("pet_fox", 33.5);
        PlayerState back = StateCodec.decode(StateCodec.encode(s));
        assertEquals("pet_fox", back.pet);
        assertEquals(2, back.petsOwned.size());
        assertTrue(back.petsOwned.contains("pet_cat"));
        assertEquals(12, back.petLevels.get("pet_fox"));
        assertEquals(33.5, back.petExps.get("pet_fox"));
    }

    @Test
    void monthlyFieldsSurviveRoundTrip() {
        PlayerState s = NewState.create();
        s.monthlyMonth = "2026-11";
        s.monthlyBonusGiven = true;
        PlayerState.DailyTask t = new PlayerState.DailyTask();
        t.id = "hunt";
        t.target = 8000;
        t.rewardGold = 250000;
        t.baseline = "999";
        s.monthlyTasks = java.util.List.of(t);
        s.stats.monthlyClaims = 7;
        s.stats.drainKills = 3;
        s.stats.rendKills = 2;
        PlayerState back = StateCodec.decode(StateCodec.encode(s));
        assertEquals("2026-11", back.monthlyMonth);
        assertTrue(back.monthlyBonusGiven);
        assertEquals(1, back.monthlyTasks.size());
        assertEquals(8000, back.monthlyTasks.get(0).target);
        assertEquals(7, back.stats.monthlyClaims);
        assertEquals(3, back.stats.drainKills);
    }

    @Test
    void freshStateHasEmptyPetFields() {
        PlayerState s = NewState.create();
        assertEquals(null, s.pet);
        assertTrue(s.petsOwned.isEmpty());
        assertTrue(s.petLevels.isEmpty());
    }
}
