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
        assertEquals(6, GameConst.PETS.size(), "宠物 6");
        assertEquals(10, GameConst.ZONES.size(), "区域 10");
        assertEquals(8, Sets.ALL.size(), "套装 8");
        assertEquals(78, Achievements.ALL.size(), "成就 78");
        assertEquals(19, GameConst.MOB_TRAITS.size(), "怪物词条 19");
        assertEquals(32, GameConst.PASSIVE_SKILLS.size() + GameConst.ACTIVE_SKILLS.size(), "技能 32");
        assertEquals(40, GameConst.POTIONS.size(), "药剂 40");
        assertEquals(5, MonthlyQuests.TEMPLATES.size(), "月常模板 5");
        assertEquals(5, WeeklyQuests.TEMPLATES.size(), "周常模板 5");
        assertEquals(6, GameConst.PETS.size(), "宠物 6（代码常量）");
        assertEquals(33, GameConst.codexTotal(), "图鉴 33（虚空 +5）");
        String sql = Files.readString(Path.of("src/main/resources/gongpu-full.sql"));
        for (String code : new String[]{
                "void_blade", "void_bow", "void_hood", "void_robe", "void_treads", "void_ring",
                "void_patrol", "void_heart", "c_void_blade", "c_void_ring",
                "void_slash", "pet_bond", "void"}) {
            assertTrue(sql.contains("'" + code + "'"), "第九批内容 " + code + " 应在 SQL 中");
        }
    }
}
