package com.gongpu.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第四批第 9 轮：数据一致性锁——SQL 资源（seed + 全部 migration）与代码镜像的对等校验。
 * 本批内容大头是数据（7 药剂 / 2 词条 / 9 装备），此前这类数据只有上线导库时才暴露错误；
 * 这里把三件事锁进单测：药剂 SQL 集合与 GameConst.POTIONS 恰好相等、新词条在 SQL 中存在、
 * 装备全集 ≥45 件且各区域覆盖达标、每件 base_stats 是合法 JSON 数值表。
 */
class DataParityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String allSql() throws Exception {
        // SQL 资源在主 classes 目录（target/classes），测试类路径根是同级的 test-classes
        Path testRoot = Path.of(DataParityTest.class.getResource("/").toURI());
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

    @Test
    void potionSqlMatchesGameConst() throws Exception {
        String sql = allSql();
        Set<String> codes = new HashSet<>();
        Matcher m = Pattern.compile("\\('([a-z0-9_]+)',\\s*'[^']*',\\s*'[^']*',\\s*'potion',").matcher(sql);
        while (m.find()) {
            codes.add(m.group(1));
        }
        assertEquals(GameConst.POTIONS.keySet(), codes,
                "SQL 里的 potion 商品集合应与 GameConst.POTIONS 镜像恰好相等（改一边必须同步另一边）");
    }

    @Test
    void newAffixesExistInSql() throws Exception {
        String sql = allSql();
        assertTrue(sql.contains("'lightness'") && sql.contains("'vitality'"),
                "新词条 lightness/vitality 应在 migration SQL 中");
        // 第六批高阶词条：新 code 必须在 SQL 里（镜像守恒）
        for (String code : new String[]{"bloodpact", "bedrock", "windhunter", "lifespring", "manaspring", "thunderstep"}) {
            assertTrue(sql.contains("'" + code + "'"), "第六批词条 " + code + " 应在 SQL 中");
        }
        // 被动技能镜像：GameConst.PASSIVE_SKILLS 的每个 id 必须在 SQL cfg_skill 里（学不到 = 不存在）
        for (String id : GameConst.PASSIVE_SKILLS.keySet()) {
            assertTrue(sql.contains("'" + id + "'"), "被动技能 " + id + " 应在 SQL 技能表里");
        }
        // 委托覆盖：总数 ≥12 且矿洞/沼泽/雪原各 ≥2（第五批第 10 轮补缺）
        for (String code : new String[]{"mine_deep", "swamp_venom", "snow_alpha"}) {
            assertTrue(sql.contains("'" + code + "'"), "新委托 " + code + " 应在 SQL 中");
        }
        // 第八批高阶词条：无影/不息必须在 SQL 里（镜像守恒）
        for (String code : new String[]{"wuying", "buxi"}) {
            assertTrue(sql.contains("'" + code + "'"), "第八批词条 " + code + " 应在 SQL 中");
        }
        assertEquals(30, GameConst.AFFIXES.size());
    }

    @Test
    void equipmentCoverageAndStatsValid() throws Exception {
        String sql = allSql();
        Pattern row = Pattern.compile(
                "\\('([a-z0-9_]+)',\\s*'[^']*',\\s*'[^']*',\\s*'(weapon|offhand|bow|head|chest|legs|boots|ring1|ring2|necklace)',\\s*\\d+,\\s*'(plain|forest|mine|swamp|snow|volcano)',\\s*'(\\{[^']*\\})'");
        Matcher m = row.matcher(sql);
        Map<String, String> zoneByCode = new HashMap<>();
        while (m.find()) {
            String code = m.group(1);
            zoneByCode.put(code, m.group(3));
            // base_stats 必须是合法 JSON 且值全为数值字符串
            var node = MAPPER.readTree(m.group(4).replace("\\\"", "\""));
            node.fields().forEachRemaining(e ->
                    assertTrue(e.getValue().asText().matches("-?[0-9.]+"),
                            code + " 的 base_stats 值应为数值: " + e.getKey()));
        }
        assertTrue(zoneByCode.size() >= 45, "装备模板总数应 ≥45，实际 " + zoneByCode.size());
        Map<String, Long> byZone = new HashMap<>();
        zoneByCode.values().forEach(z -> byZone.merge(z, 1L, Long::sum));
        assertTrue(byZone.getOrDefault("swamp", 0L) >= 5, "沼泽装备应 ≥5: " + byZone);
        assertTrue(byZone.getOrDefault("volcano", 0L) >= 6, "火山装备应 ≥6: " + byZone);
        assertTrue(byZone.getOrDefault("snow", 0L) >= 8, "雪原装备应 ≥8: " + byZone);
    }
}
