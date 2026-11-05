package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第七批第 9 轮：套装成员完整性锁——Sets 的每个成员 code 必须真实存在于
 * gongpu-full.sql 的装备模板里（写错一个 code，该件永不掉落、套装永远凑不齐），
 * 且成员 min_zone 与套装所属区域一致（掉落区域与套装主题对齐）。
 */
class SetParityTest {

    private static String allSql() throws Exception {
        Path testRoot = Path.of(SetParityTest.class.getResource("/").toURI());
        Path root = testRoot.resolveSibling("classes");
        if (!Files.isDirectory(root)) {
            root = testRoot;
        }
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.filter(p -> p.getFileName().toString().endsWith(".sql")).toList()) {
                sb.append(Files.readString(p, StandardCharsets.UTF_8)).append('\n');
            }
        }
        return sb.toString();
    }

    /** 装备模板 code → min_zone（解析全部 cfg_equipment_template INSERT 行）。 */
    private static Map<String, String> equipmentZones(String sql) {
        Map<String, String> out = new HashMap<>();
        Matcher m = Pattern.compile(
                "\\(\\s*(?:\\d+,\\s*)?'([a-z0-9_]+)',\\s*'[^']*',\\s*'[^']*',\\s*'[a-z0-9]+',\\s*\\d+,\\s*'([a-z]+)'")
                .matcher(sql);
        while (m.find()) {
            out.put(m.group(1), m.group(2));
        }
        return out;
    }

    private static final Map<String, String> SET_ZONE = Map.ofEntries(
            Map.entry("farwalker", "plain"),
            Map.entry("vinetalk", "forest"),
            Map.entry("ironwall", "mine"),
            Map.entry("marshwalk", "swamp"),
            Map.entry("snowwolf", "snow"),
            Map.entry("molten", "volcano"),
            Map.entry("starset", "star"), Map.entry("voidset", "void"), Map.entry("chaossuit", "chaos"),
            Map.entry("hongmengsuit", "hongmeng"), Map.entry("guixusuit", "guixu"));

    @Test
    void everySetMemberExistsInSqlWithMatchingZone() throws Exception {
        Map<String, String> zones = equipmentZones(allSql());
        assertTrue(zones.size() >= 52, "应解析到全部装备模板，实际 " + zones.size());
        for (Sets.SetDef d : Sets.ALL) {
            for (String code : d.members()) {
                String zone = zones.get(code);
                assertNotNull(zone, "套装 " + d.id() + " 成员 " + code + " 不在装备模板中（永不掉落）");
                assertEquals(SET_ZONE.get(d.id()), zone,
                        "套装 " + d.id() + " 成员 " + code + " 的 min_zone 应为 " + SET_ZONE.get(d.id()));
            }
        }
    }
}
