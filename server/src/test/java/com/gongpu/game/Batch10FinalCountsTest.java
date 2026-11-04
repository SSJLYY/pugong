package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 第十批收官总口径锁：代码常量 + SQL 种子（按 code 去重后的有效条目）+ 前端镜像三方一致。
 * 教训沿用第九批：跨批同名 code 的后段 DELETE+INSERT 会静默覆盖，计数必须按去重后实数。
 */
class Batch10FinalCountsTest {

    private static String sql() throws Exception {
        return Files.readString(Path.of("src/main/resources/gongpu-full.sql"), StandardCharsets.UTF_8);
    }

    /** 数某表 INSERT 值行里 code 字段的去重集合大小（值行以 ('code', 开头）。 */
    private static int distinctCodes(String sql, String table) {
        Matcher blocks = Pattern.compile(
                "INSERT INTO `" + table + "`[^;]*?VALUES\\s*(.*?);", Pattern.DOTALL).matcher(sql);
        Pattern row = Pattern.compile("\\('([a-z0-9_]+)',");
        Set<String> codes = new HashSet<>();
        while (blocks.find()) {
            Matcher rows = row.matcher(blocks.group(1));
            while (rows.find()) {
                codes.add(rows.group(1));
            }
        }
        return codes.size();
    }

    @Test
    void finalCounts() throws Exception {
        String sql = sql();
        // 代码侧
        assertEquals(98, Achievements.ALL.size(), "成就 98");
        assertEquals(13, GameConst.TALENTS.size(), "天赋 13");
        assertEquals(9, Sets.ALL.size(), "套装 9");
        assertEquals(11, GameConst.ZONES.size(), "区域 11");
        assertEquals(38, GameConst.codexTotal(), "图鉴 38");
        assertEquals(8, GameConst.PETS.size(), "宠物 8");
        assertEquals(23, GameConst.MOB_TRAITS.size(), "怪物词条 23");
        assertEquals(44, GameConst.POTIONS.size(), "药剂 44");
        assertEquals(30, GameConst.AFFIXES.size(), "词条 30");
        assertEquals(36, GameConst.ACTIVE_SKILLS.size() + GameConst.PASSIVE_SKILLS.size(), "技能 36");
        // SQL 侧（去重有效条目）
        assertEquals(82, distinctCodes(sql, "cfg_equipment_template"), "SQL 装备 82");
        assertEquals(44, distinctCodes(sql, "cfg_item"), "SQL 药剂 44");
        assertEquals(30, distinctCodes(sql, "cfg_affix"), "SQL 词条 30");
        assertEquals(36, distinctCodes(sql, "cfg_skill"), "SQL 技能 36");
        assertEquals(30, distinctCodes(sql, "cfg_quest"), "SQL 委托 30");
        assertEquals(30, distinctCodes(sql, "cfg_craft"), "SQL 配方 30");
    }
}
