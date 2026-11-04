package com.gongpu.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第六批第 74 轮：数据一致性锁（二）——主动技能/委托总数/配方引用完整性。
 * DataParityTest 锁了药剂/词条/装备/被动；这里补：主动技能必须在 SQL 技能表、
 * 委托总数 =12、配方材料只能是已知区域材料、配方产物必须真实存在。
 */
class DataParity2Test {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String allSql() throws Exception {
        Path testRoot = Path.of(DataParity2Test.class.getResource("/").toURI());
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

    /** 提取某张表全部 INSERT 块的 VALUES 部分。 */
    private static String insertBlocks(String sql, String table) {
        StringBuilder sb = new StringBuilder();
        Matcher m = Pattern.compile("INSERT INTO `" + table + "`.*?VALUES(.*?);\\s*\\n", Pattern.DOTALL).matcher(sql);
        while (m.find()) {
            sb.append(m.group(1)).append('\n');
        }
        return sb.toString();
    }

    private static int countRows(String valuesBlock) {
        return (int) Pattern.compile("^\\s*\\('", Pattern.MULTILINE).matcher(valuesBlock).results().count();
    }

    @Test
    void activeSkillsExistInSql() throws Exception {
        String sql = allSql();
        for (String id : GameConst.ACTIVE_SKILLS.keySet()) {
            assertTrue(sql.contains("'" + id + "'"), "主动技能 " + id + " 应在 SQL 技能表里");
        }
        // 主动技能总数 = 12（+ 第七批迟缓咒）；被动 16（+ 第八批破甲专精）
        assertEquals(12, GameConst.ACTIVE_SKILLS.size());
        assertEquals(16, GameConst.PASSIVE_SKILLS.size());
    }

    @Test
    void questCountIs18() throws Exception {
        String sql = allSql();
        assertEquals(18, countRows(insertBlocks(sql, "cfg_quest")), "委托总数应为 18（16 + 第七批火山/雪原 2）");
    }

    @Test
    void craftRefsValid() throws Exception {
        String sql = allSql();
        String crafts = insertBlocks(sql, "cfg_craft");
        assertEquals(18, countRows(crafts), "配方总数应为 18（16 + 第七批深渊 2）");
        // 产物全集：装备 code + 药剂/物品 code（item: 前缀）
        Set<String> equipCodes = new HashSet<>();
        Matcher em = Pattern.compile("\\('([a-z0-9_]+)',\\s*'[^']*',\\s*'[^']*',\\s*'(weapon|bow|offhand|head|chest|legs|boots|ring1|ring2|necklace)',").matcher(sql);
        while (em.find()) {
            equipCodes.add(em.group(1));
        }
        Set<String> itemCodes = new HashSet<>();
        Matcher im = Pattern.compile("\\('([a-z0-9_]+)',\\s*'[^']*',\\s*'[^']*',\\s*'(potion|food|scroll)',").matcher(sql);
        while (im.find()) {
            itemCodes.add(im.group(1));
        }
        // 逐行解析配方：材料键与产物
        Matcher rm = Pattern.compile("\\('([a-z0-9_]+)',\\s*'[^']*',\\s*'[^']*',\\s*'([^']+)',\\s*'(\\{[^']*\\})',").matcher(crafts);
        int parsed = 0;
        while (rm.find()) {
            parsed++;
            String product = rm.group(2);
            if (product.startsWith("item:")) {
                assertTrue(itemCodes.contains(product.substring(5)) || GameConst.POTIONS.containsKey(product.substring(5)),
                        "配方 " + rm.group(1) + " 的产物 " + product + " 应存在于物品表");
            } else {
                assertTrue(equipCodes.contains(product),
                        "配方 " + rm.group(1) + " 的产物 " + product + " 应存在于装备表");
            }
            var mats = MAPPER.readValue(rm.group(3), java.util.Map.class);
            for (Object k : mats.keySet()) {
                assertTrue(GameConst.MAT_INFO.containsKey((String) k),
                        "配方 " + rm.group(1) + " 的材料 " + k + " 应是已知区域材料");
            }
        }
        assertTrue(parsed >= 10, "应解析到绝大多数配方行，实际 " + parsed);
    }
}
