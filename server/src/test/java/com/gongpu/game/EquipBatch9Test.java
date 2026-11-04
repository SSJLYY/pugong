package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第六批第 81/82 轮：区域装备补缺——雪山头盔/火山护腿的 SQL 行与装备总数。 */
class EquipBatch9Test {

    private static String allSql() throws Exception {
        Path testRoot = Path.of(EquipBatch9Test.class.getResource("/").toURI());
        Path root = testRoot.resolveSibling("classes");
        if (!Files.isDirectory(root)) {
            root = testRoot;
        }
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.filter(p -> p.toString().endsWith(".sql")).toList()) {
                sb.append(Files.readString(p, StandardCharsets.UTF_8)).append('\n');
            }
        }
        return sb.toString();
    }

    private static void assertEquip(String sql, String code, String slot, String zone) {
        Matcher m = Pattern.compile("\\('" + code + "',\\s*'[^']*',\\s*'[^']*',\\s*'" + slot + "',\\s*\\d+,\\s*'" + zone + "',").matcher(sql);
        assertTrue(m.find(), code + " 应在 SQL 中且部位=" + slot + " 区域=" + zone);
    }

    @Test
    void snowHoodAndLavaGreaves() throws Exception {
        String sql = allSql();
        assertEquip(sql, "snow_hood", "head", "snow");
        assertEquip(sql, "lava_greaves", "legs", "volcano");
    }

    @Test
    void equipmentTotalIs52() throws Exception {
        String sql = allSql();
        Matcher m = Pattern.compile("INSERT INTO `cfg_equipment_template`.*?VALUES(.*?);\\s*\\n", Pattern.DOTALL).matcher(sql);
        int rows = 0;
        while (m.find()) {
            rows += (int) Pattern.compile("^\\s*\\('", Pattern.MULTILINE).matcher(m.group(1)).results().count();
        }
        assertEquals(74, rows, "装备模板总数应为 74（第九批口径）");
    }
}
