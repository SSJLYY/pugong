package com.gongpu.craft;

import com.gongpu.game.GameConst;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 第六批第 44 轮：材料来源提示——六种区域材料反查来源区域名。 */
class MatSourceTest {

    @Test
    void allZoneMatsHaveSource() {
        assertEquals("平原", CraftService.matSourceZone("pelt"));
        assertEquals("森林", CraftService.matSourceZone("wolf_fang"));
        assertEquals("矿洞", CraftService.matSourceZone("ore"));
        assertEquals("沼泽", CraftService.matSourceZone("toad_gland"));
        assertEquals("雪山", CraftService.matSourceZone("mana_dust"));
        assertEquals("火山", CraftService.matSourceZone("fire_crystal"));
        for (String mat : GameConst.ZONE_MAT.values()) {
            org.junit.jupiter.api.Assertions.assertNotNull(CraftService.matSourceZone(mat), mat);
        }
    }

    @Test
    void nonZoneMatHasNoSource() {
        assertNull(CraftService.matSourceZone("bp_iron_sword"));
        assertNull(CraftService.matSourceZone("unknown_mat"));
    }
}
