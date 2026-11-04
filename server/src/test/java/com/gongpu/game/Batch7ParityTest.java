package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第七批第 95 轮：第七批收官口径总锁——代码侧内容计数一处汇总断言，
 * 且本批全部新内容 code 必须在 gongpu-full.sql 中存在（防只改代码漏 SQL 或反之）。
 * 任一数字变动都应是有意的内容增减，并同步改这里与文档口径。
 */
class Batch7ParityTest {

    @Test
    void batch7ContentCounts() {
        assertEquals(36, GameConst.POTIONS.size(), "药剂 36");
        assertEquals(24, GameConst.AFFIXES.size(), "词条 24");
        assertEquals(13, GameConst.ACTIVE_SKILLS.size(), "主动技能 13");
        assertEquals(16, GameConst.PASSIVE_SKILLS.size(), "被动技能 16");
        assertEquals(10, GameConst.TALENTS.size(), "天赋 10");
        assertEquals(16, GameConst.MOB_TRAITS.size(), "怪物词条 16");
        assertEquals(50, Achievements.ALL.size(), "成就 50");
        assertEquals(6, Sets.ALL.size(), "套装 6");
        assertEquals(8, GameConst.ZONES.size(), "区域 8");
    }

    @Test
    void batch7CodesExistInSql() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/gongpu-full.sql"), StandardCharsets.UTF_8);
        String[] codes = {
                // 深渊装备/委托/配方
                "abyss_blade", "void_bow", "rift_helm", "chaos_plate", "abyss_greaves", "void_ring",
                "abyss_probe", "abyss_lord", "c_abyss_blade", "c_void_ring",
                // 技能（血怒/冲锋/处决者/迟缓咒）
                "bloodrage", "charge", "executioner", "hex_slow",
                // 药剂（狂怒/时之砂）
                "rage", "timesand",
                // 词条（破军/绝杀）
                "pojun", "juesha",
                // 委托（火山/雪原）
                "volcano_core", "snow_throne",
                // 备份环列
                "backup_ring",
        };
        for (String code : codes) {
            assertTrue(sql.contains("'" + code + "'"), "SQL 应含第七批内容 code: " + code);
        }
    }
}
