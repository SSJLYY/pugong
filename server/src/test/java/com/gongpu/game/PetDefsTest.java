package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** 第九批·宠物定义表（第 1 轮）。 */
class PetDefsTest {

    @Test
    void sixPetsWithValidDefs() {
        assertEquals(6, GameConst.PETS.size());
        for (GameConst.PetDef d : GameConst.PETS.values()) {
            assertFalse(d.name().isBlank());
            assertFalse(d.stats().isEmpty());
            assertTrue(d.price() > 0);
            d.stats().values().forEach(v -> assertTrue(v.compareTo(BigDecimal.ZERO) > 0));
        }
    }

    @Test
    void expNeedGrowsWithLevel() {
        assertEquals(20, GameConst.petExpNeed(1));
        assertEquals(30, GameConst.petExpNeed(2));
        assertEquals(510, GameConst.petExpNeed(50));
    }
}
