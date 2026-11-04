package com.gongpu.game;

import com.gongpu.cfg.GameData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第八批第 21 轮：第九区域「星界」——定义/门槛/材料/词条池深度口径。
 */
class ZoneStarTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void starZoneDefined() {
        GameConst.ZoneDef z = GameConst.ZONES.get("star");
        assertNotNull(z, "星界应存在");
        assertEquals("20000000", z.req().get("maxHP"));
        assertEquals("400", z.req().get("PDEF"));
        assertEquals(4, z.mobs().size());
        assertEquals("boss_star", z.boss().id());
        assertEquals("300000000", z.boss().maxHP());
        assertEquals("star", GameEngine.nextZoneId("abyss"));
    }

    @Test
    void starMaterialWired() {
        assertEquals("star_shard", GameConst.ZONE_MAT.get("star"));
        assertEquals("星核碎片", GameConst.MAT_INFO.get("star_shard")[0]);
        assertEquals(650, GameConst.MAT_SELL_PRICE.get("star_shard"));
    }

    @Test
    void starEquipmentInSql() throws Exception {
        String sql = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Path.of("src/main/resources/gongpu-full.sql")), java.nio.charset.StandardCharsets.UTF_8);
        for (String code : new String[]{"star_blade", "comet_bow", "nebula_helm", "galaxy_plate", "meteor_greaves", "quasar_ring"}) {
            assertTrue(sql.contains("'" + code + "'"), "星界装备应写入 SQL：" + code);
            assertTrue(sql.contains("'star'"), "星界装备 min_zone 应为 star");
        }
    }

    @Test
    void starSetDefined() {
        java.util.Optional<com.gongpu.game.Sets.SetDef> set = Sets.ALL.stream()
                .filter(d -> d.id().equals("starset")).findFirst();
        assertTrue(set.isPresent(), "星辰战装应存在");
        assertEquals(4, set.get().members().size());
        assertTrue(set.get().members().contains("star_blade"));
        assertEquals(8, Sets.ALL.size(), "套装应达 8 套");
    }

    @Test
    void starDepthUsesFullTraitPool() {
        assertEquals(19, GameConst.traitPool(8).size(), "星界深度应全池 18 条（含星界专属星蚀）");
        assertTrue(new java.util.ArrayList<>(GameConst.ZONES.keySet()).indexOf("star") == 8, "星界应为第 9 区（序 8）");
    }
}
