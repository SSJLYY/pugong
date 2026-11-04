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
        assertEquals(72, Achievements.ALL.size(), "成就 72");
        assertEquals(33, GameConst.codexTotal(), "图鉴 33（虚空 +5）");
        String sql = Files.readString(Path.of("src/main/resources/gongpu-full.sql"));
        for (String code : new String[]{
                "void_blade", "void_bow", "void_hood", "void_robe", "void_treads", "void_ring",
                "void_patrol", "void_heart", "c_void_blade", "c_void_ring"}) {
            assertTrue(sql.contains("'" + code + "'"), "第九批内容 " + code + " 应在 SQL 中");
        }
    }
}
