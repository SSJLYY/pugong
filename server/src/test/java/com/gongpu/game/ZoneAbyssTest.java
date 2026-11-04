package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.ApiException;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第七批第 21 轮：新区域「深渊裂隙」骨架——区域顺序/图鉴总数/材料映射/门槛/词条池深度。 */
class ZoneAbyssTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private final GameService game = new GameService();

    @Test
    void abyssIsDeepestZoneWithFullRoster() {
        GameConst.ZoneDef z = GameConst.ZONES.get("abyss");
        assertNotNull(z, "深渊裂隙应存在");
        assertEquals("深渊裂隙", z.name());
        assertEquals(4, z.mobs().size(), "深渊应有 4 种野怪");
        assertNotNull(z.boss());
        assertEquals("boss_abyss", z.boss().id());
        List<String> order = new ArrayList<>(GameConst.ZONES.keySet());
        assertEquals("star", order.get(order.size() - 2), "星界应为次深区域（第九批虚空界在其后）");
        assertEquals("abyss", GameEngine.nextZoneId("volcano"), "火山之后应推进到深渊");
        assertEquals("star", GameEngine.nextZoneId("abyss"), "第八批：深渊之后推进星界");
    }

    @Test
    void codexTotalGrowsTo23() {
        assertEquals(33, GameConst.codexTotal(), "图鉴总数 = 28 + 虚空界 4 怪 1 Boss（第九批）");
    }

    @Test
    void abyssMaterialMapping() {
        assertEquals("abyss_crystal", GameConst.ZONE_MAT.get("abyss"));
        assertEquals("深渊结晶", GameConst.MAT_INFO.get("abyss_crystal")[0]);
    }

    @Test
    void traitPoolAtAbyssDepthIsFull() {
        assertEquals(17, GameConst.traitPool(7).size(), "深区词条池应全开（17 条）");
    }

    /** 第 23 轮：深渊 6 件毕业装在 SQL 里 min_zone=abyss（掉落池按区域取模板）。 */
    @Test
    void abyssGearInSql() throws Exception {
        java.nio.file.Path testRoot = java.nio.file.Path.of(ZoneAbyssTest.class.getResource("/").toURI());
        java.nio.file.Path root = testRoot.resolveSibling("classes");
        StringBuilder sb = new StringBuilder();
        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(root)) {
            for (java.nio.file.Path p : walk.filter(x -> x.getFileName().toString().endsWith(".sql")).toList()) {
                sb.append(java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)).append('\n');
            }
        }
        String sql = sb.toString();
        for (String code : List.of("abyss_blade", "void_bow", "rift_helm", "chaos_plate", "abyss_greaves", "void_ring")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "\\('" + code + "',\\s*'[^']*',\\s*'[^']*',\\s*'[a-z0-9]+',\\s*\\d+,\\s*'([a-z]+)'").matcher(sql);
            assertTrue(m.find(), "深渊装备应在 SQL 模板中：" + code);
            assertEquals("abyss", m.group(1), code + " 的 min_zone 应为 abyss");
        }
    }

    /** 第 25 轮：深渊委托 ×2 在 SQL 中（target_zone=abyss，奖励药剂为真实物品 code）。 */
    @Test
    void abyssQuestsInSql() throws Exception {
        java.nio.file.Path testRoot = java.nio.file.Path.of(ZoneAbyssTest.class.getResource("/").toURI());
        java.nio.file.Path root = testRoot.resolveSibling("classes");
        StringBuilder sb = new StringBuilder();
        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(root)) {
            for (java.nio.file.Path p : walk.filter(x -> x.getFileName().toString().endsWith(".sql")).toList()) {
                sb.append(java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8)).append('\n');
            }
        }
        String sql = sb.toString();
        assertTrue(sql.contains("'abyss_probe'") && sql.contains("'abyss_lord'"), "两条深渊委托应在 SQL 中");
        assertTrue(sql.contains("'裂隙初探'") && sql.contains("'无光君王'"), "委托名应正确写入（防编码事故）");
    }

    @Test
    void gateRejectsWeakAndAdmitsStrong() {
        PlayerState weak = NewState.create();
        assertThrows(ApiException.class, () -> game.gotoZone(weak, "abyss"), "白板号应被深渊门槛拒绝");

        PlayerState s = NewState.create();
        s.life.maxHP = new BigDecimal("5000000");
        s.life.curHP = new BigDecimal("5000000");
        s.life.set("PATK", new BigDecimal("10000"));
        s.life.set("PDEF", new BigDecimal("500"));
        s.life.set("MDEF", new BigDecimal("500"));
        game.gotoZone(s, "abyss");
        assertEquals("abyss", s.zone);
        assertNotNull(s.mob, "进入深渊应刷出野怪");
        assertTrue(GameConst.ZONES.get("abyss").mobs().stream().anyMatch(d -> d.id().equals(s.mob.id)),
                "刷出的应是深渊怪种：" + s.mob.id);
    }
}
