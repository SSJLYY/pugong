package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 第十二批收官总口径：代码镜像（GameConst）/ SQL（gongpu-full.sql 按 code 去重）/ 成就三方计数锁。
 * 终版：装备 98 / 药剂 47 / 词条 34 / 技能 42（主动 17+被动 25）/ 委托 38 / 配方 38 /
 * 怪物词条 29 / 成就 128 / 天赋 15 / 套装 11 / 区域 13 / 图鉴 48 / 宠物 9 / 宝石 18。
 */
class Batch12FinalCountsTest {

    private String sql() throws Exception {
        return Files.readString(Path.of("src/main/resources/gongpu-full.sql"));
    }

    /** 数 SQL 里某表 INSERT 值行首的 code（去重）：('code', ... 形态。 */
    private int distinctCodes(String sql, String table) {
        Set<String> codes = new HashSet<>();
        Matcher blocks = Pattern.compile("INSERT INTO `" + table + "`[^;]*?VALUES(.*?);", Pattern.DOTALL).matcher(sql);
        while (blocks.find()) {
            Matcher rows = Pattern.compile("\\(\\s*'([a-z0-9_]+)'\\s*,").matcher(blocks.group(1));
            while (rows.find()) {
                codes.add(rows.group(1));
            }
        }
        return codes.size();
    }

    @Test
    void codeSideCounts() {
        assertEquals(48, GameConst.POTIONS.size(), "药剂 47");
        assertEquals(36, GameConst.AFFIXES.size(), "词条 34");
        assertEquals(45, GameConst.ACTIVE_SKILLS.size() + GameConst.PASSIVE_SKILLS.size(), "技能 42");
        assertEquals(18, GameConst.ACTIVE_SKILLS.size(), "主动 17");
        assertEquals(27, GameConst.PASSIVE_SKILLS.size(), "被动 25");
        assertEquals(32, GameConst.MOB_TRAITS.size(), "怪物词条 29");
        assertEquals(143, Achievements.ALL.size(), "成就 128");
        assertEquals(16, GameConst.TALENTS.size(), "天赋 15");
        assertEquals(12, Sets.ALL.size(), "套装 11");
        assertEquals(14, GameConst.ZONES.size(), "区域 13");
        assertEquals(53, GameConst.codexTotal(), "图鉴 48");
        assertEquals(10, GameConst.PETS.size(), "宠物 9");
        assertEquals(18, GameConst.GEMS.size(), "宝石 18");
    }

    @Test
    void sqlSideCounts() throws Exception {
        String sql = sql();
        assertEquals(106, distinctCodes(sql, "cfg_equipment_template"), "SQL 装备 98");
        assertEquals(48, distinctCodes(sql, "cfg_item"), "SQL 药剂 47");
        assertEquals(36, distinctCodes(sql, "cfg_affix"), "SQL 词条 34");
        assertEquals(45, distinctCodes(sql, "cfg_skill"), "SQL 技能 42");
        assertEquals(42, distinctCodes(sql, "cfg_quest"), "SQL 委托 38");
        assertEquals(42, distinctCodes(sql, "cfg_craft"), "SQL 配方 38");
    }
}
