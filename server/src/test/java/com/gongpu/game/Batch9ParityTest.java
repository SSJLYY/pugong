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
        assertEquals(10, GameConst.PETS.size(), "宠物 9");
        assertEquals(14, GameConst.ZONES.size(), "区域 12");
        assertEquals(12, Sets.ALL.size(), "套装 10");
        assertEquals(143, Achievements.ALL.size(), "成就 128");
        assertEquals(32, GameConst.MOB_TRAITS.size(), "怪物词条 25");
        assertEquals(45, GameConst.PASSIVE_SKILLS.size() + GameConst.ACTIVE_SKILLS.size(), "技能 38");
        assertEquals(48, GameConst.POTIONS.size(), "药剂 45");
        assertEquals(13, MonthlyQuests.TEMPLATES.size(), "月常模板 9");
        assertEquals(11, WeeklyQuests.TEMPLATES.size(), "周常模板 9");
        assertEquals(10, GameConst.PETS.size(), "宠物 9（代码常量）");
        assertEquals(53, GameConst.codexTotal(), "图鉴 43");
        String sql = Files.readString(Path.of("src/main/resources/gongpu-full.sql"));
        for (String code : new String[]{
                "void_blade", "void_bow", "void_hood", "void_robe", "void_treads", "void_ring",
                "void_patrol", "void_heart", "c_void_blade", "c_void_ring",
                "void_slash", "pet_bond", "void"}) {
            assertTrue(sql.contains("'" + code + "'"), "第九批内容 " + code + " 应在 SQL 中");
        }
    }
}
