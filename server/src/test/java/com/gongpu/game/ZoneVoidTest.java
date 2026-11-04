package com.gongpu.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 第九批·第十区域虚空界（第 11 轮）。 */
class ZoneVoidTest {

    @Test
    void voidZoneDefined() {
        GameConst.ZoneDef z = GameConst.ZONES.get("void");
        assertNotNull(z, "虚空界应存在");
        assertEquals("200000000", z.req().get("maxHP"));
        assertEquals("1200", z.req().get("PDEF"));
        assertEquals("40000", z.req().get("ATK"));
        assertEquals(4, z.mobs().size());
        assertEquals("boss_void", z.boss().id());
        assertEquals("3000000000", z.boss().maxHP());
        assertEquals(10, GameConst.ZONES.size());
    }

    @Test
    void advanceChainEndsAtVoid() {
        assertEquals("void", GameEngine.nextZoneId("star"));
        assertNull(GameEngine.nextZoneId("void"));
    }
}
