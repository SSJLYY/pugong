package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第九批收官总口径锁：代码常量 + SQL 种子 + 前端镜像三方一致（2026-11-04 第九批终版）。
 * 后续批次增删内容时，本测试与 Batch9ParityTest 一并更新。
 */
class Batch9FinalCountsTest {

    private static String sql() throws Exception {
        return Files.readString(Path.of("src/main/resources/gongpu-full.sql"), StandardCharsets.UTF_8);
    }

    private static int countRows(String sql, String table) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "INSERT INTO `" + table + "`").matcher(sql);
        int blocks = 0;
        StringBuilder all = new StringBuilder();
        while (m.find()) {
            blocks++;
            int end = sql.indexOf(';', m.start());
            all.append(sql, m.start(), end);
        }
        // 同 code 多段重播种（DELETE+INSERT）只计一次：按行首 code 去重
        java.util.Set<String> codes = new java.util.HashSet<>();
        java.util.regex.Matcher v = java.util.regex.Pattern.compile("\\n\\s*\\('([a-z0-9_]+)'").matcher(all);
        while (v.find()) codes.add(v.group(1));
        assertTrue(blocks > 0, table + " 应有 INSERT 段");
        return codes.size();
    }

    @Test
    void codeSideCounts() {
        assertEquals(41, GameConst.POTIONS.size(), "药剂 41");
        assertEquals(28, GameConst.AFFIXES.size(), "词条 28");
        assertEquals(33, GameConst.ACTIVE_SKILLS.size() + GameConst.PASSIVE_SKILLS.size(), "技能 33");
        assertEquals(20, GameConst.MOB_TRAITS.size(), "怪物词条 20");
        assertEquals(12, GameConst.TALENTS.size(), "天赋 12");
        assertEquals(6, GameConst.PETS.size(), "宠物 6");
        assertEquals(10, GameConst.ZONES.size(), "区域 10");
        assertEquals(33, GameConst.codexTotal(), "图鉴 33");
        assertEquals(82, Achievements.ALL.size(), "成就 82");
    }

    @Test
    void sqlSideCounts() throws Exception {
        String sql = sql();
        assertEquals(74, countRows(sql, "cfg_equipment_template"), "SQL 装备 74");
        assertEquals(41, countRows(sql, "cfg_item"), "SQL 药剂 41");
        assertEquals(28, countRows(sql, "cfg_affix"), "SQL 词条 28");
        assertEquals(33, countRows(sql, "cfg_skill"), "SQL 技能 33");
        assertEquals(26, countRows(sql, "cfg_quest"), "SQL 委托 26");
        assertEquals(26, countRows(sql, "cfg_craft"), "SQL 配方 26");
    }
}
