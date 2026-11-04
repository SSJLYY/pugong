package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第九批·内容总校准（第 19 轮）：宠物/虚空界计数口径 + 新 code 在 SQL/代码两侧守恒。 */
class Batch9ParityTest {

    @Test
    void batch9CountsAndCodes() throws Exception {
        assertEquals(8, GameConst.PETS.size(), "宠物 8");
        assertEquals(11, GameConst.ZONES.size(), "区域 11");
        assertEquals(9, Sets.ALL.size(), "套装 9");
        assertEquals(98, Achievements.ALL.size(), "成就 98");
        assertEquals(23, GameConst.MOB_TRAITS.size(), "怪物词条 23");
        assertEquals(36, GameConst.PASSIVE_SKILLS.size() + GameConst.ACTIVE_SKILLS.size(), "技能 36");
        assertEquals(44, GameConst.POTIONS.size(), "药剂 41");
        assertEquals(7, MonthlyQuests.TEMPLATES.size(), "月常模板 7");
        assertEquals(5, WeeklyQuests.TEMPLATES.size(), "周常模板 5");
        assertEquals(8, GameConst.PETS.size(), "宠物 8（代码常量）");
        assertEquals(38, GameConst.codexTotal(), "图鉴 38（混沌 +5）");
        String sql = Files.readString(Path.of("src/main/resources/gongpu-full.sql"));
        for (String code : new String[]{
                "void_blade", "void_bow", "void_hood", "void_robe", "void_treads", "void_ring",
                "void_patrol", "void_heart", "c_void_blade", "c_void_ring",
                "void_slash", "pet_bond", "void"}) {
            assertTrue(sql.contains("'" + code + "'"), "第九批内容 " + code + " 应在 SQL 中");
        }
    }
}
