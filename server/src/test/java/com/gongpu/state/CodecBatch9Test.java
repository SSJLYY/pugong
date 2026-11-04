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
    void freshStateHasEmptyPetFields() {
        PlayerState s = NewState.create();
        assertEquals(null, s.pet);
        assertTrue(s.petsOwned.isEmpty());
        assertTrue(s.petLevels.isEmpty());
    }
}
