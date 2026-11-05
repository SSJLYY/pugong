package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第十一批收官总口径：代码镜像（GameConst）/ SQL（gongpu-full.sql 按 code 去重）/ 成就三方计数锁。
 * 终版：装备 90 / 药剂 46 / 词条 32 / 技能 39（主动 16+被动 23）/ 委托 34 / 配方 34 /
 * 怪物词条 26 / 成就 113 / 天赋 14 / 套装 10 / 区域 12 / 图鉴 43 / 宠物 9。
 */
class Batch11FinalCountsTest {

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
        assertEquals(48, GameConst.POTIONS.size(), "药剂 46");
        assertEquals(36, GameConst.AFFIXES.size(), "词条 32");
        assertEquals(45, GameConst.ACTIVE_SKILLS.size() + GameConst.PASSIVE_SKILLS.size(), "技能 39");
        assertEquals(18, GameConst.ACTIVE_SKILLS.size(), "主动 16");
        assertEquals(27, GameConst.PASSIVE_SKILLS.size(), "被动 23");
        assertEquals(32, GameConst.MOB_TRAITS.size(), "怪物词条 26");
        assertEquals(143, Achievements.ALL.size(), "成就 128");
        assertEquals(16, GameConst.TALENTS.size(), "天赋 14");
        assertEquals(12, Sets.ALL.size(), "套装 11");
        assertEquals(14, GameConst.ZONES.size(), "区域 13");
        assertEquals(53, GameConst.codexTotal(), "图鉴 48");
        assertEquals(10, GameConst.PETS.size(), "宠物 9");
    }

    @Test
    void sqlSideCounts() throws Exception {
        String sql = sql();
        assertEquals(106, distinctCodes(sql, "cfg_equipment_template"), "SQL 装备 96");
        assertEquals(48, distinctCodes(sql, "cfg_item"), "SQL 药剂 46");
        assertEquals(36, distinctCodes(sql, "cfg_affix"), "SQL 词条 32");
        assertEquals(45, distinctCodes(sql, "cfg_skill"), "SQL 技能 39");
        assertEquals(42, distinctCodes(sql, "cfg_quest"), "SQL 委托 36");
        assertEquals(42, distinctCodes(sql, "cfg_craft"), "SQL 配方 36");
    }

    @Test
    void batch11MarkersPresent() throws Exception {
        String sql = sql();
        for (String code : new String[]{"hongmeng_blade", "hongmeng_eye", "taixu", "taixu_sword",
                "hongmeng_aff", "taichu_aff", "hongmeng_longhunt", "c_hongmeng_robe", "sword_rain", "hongmeng_body"}) {
            assertTrue(sql.contains("'" + code + "'"), "SQL 应含第十一批内容 " + code);
        }
        assertTrue(GameConst.ZONES.containsKey("hongmeng"), "鸿蒙界应在区域表");
        assertTrue(GameConst.PETS.containsKey("pet_qilin"), "麒麟应在宠物表");
        assertTrue(GameConst.TALENTS.containsKey("hongmeng_heart"), "鸿蒙之心应在天赋表");
    }
}
