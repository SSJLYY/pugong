package com.gongpu.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十批第十一区域·混沌界：区域定义、门槛、Boss、材料与套装。 */
class ZoneChaosTest {

    @Test
    void chaosZoneRegistered() {
        GameConst.ZoneDef z = GameConst.ZONES.get("chaos");
        assertNotNull(z, "混沌界应注册");
        assertEquals(4, z.mobs().size());
        assertEquals("boss_chaos", z.boss().id());
        assertEquals("30000000000", z.boss().maxHP(), "混沌魔君 300 亿血");
        assertEquals("chaos", GameEngine.nextZoneId("void"), "虚空界之后即混沌界");
        assertEquals(null, GameEngine.nextZoneId("chaos"), "混沌界为最深区域");
        assertEquals(11, GameConst.ZONES.size(), "区域 11");
        assertEquals(38, GameConst.codexTotal(), "图鉴 38（33 + 混沌 4 怪 1 Boss）");
    }

    @Test
    void chaosMaterialWired() {
        assertEquals("chaos_core", GameConst.ZONE_MAT.get("chaos"));
        assertEquals(6000, GameConst.MAT_SELL_PRICE.get("chaos_core"));
    }

    @Test
    void chaosSetRegistered() {
        assertEquals(9, Sets.ALL.size(), "套装 9 套");
        assertNotNull(Sets.byId("chaossuit"));
    }
}
