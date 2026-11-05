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
        assertEquals(47, GameConst.POTIONS.size(), "药剂 45");
        assertEquals(34, GameConst.AFFIXES.size(), "词条 26（第八批终版口径）");
        assertEquals(17, GameConst.ACTIVE_SKILLS.size(), "主动技能 16");
        assertEquals(25, GameConst.PASSIVE_SKILLS.size(), "被动技能 22");
        assertEquals(15, GameConst.TALENTS.size(), "天赋 12（第九批口径）");
        assertEquals(29, GameConst.MOB_TRAITS.size(), "怪物词条 20（第九批口径）");
        assertEquals(128, Achievements.ALL.size(), "成就 128");
        assertEquals(11, Sets.ALL.size(), "套装 10");
        assertEquals(13, GameConst.ZONES.size(), "区域 12");
    }

    @Test
    void batch7CodesExistInSql() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/gongpu-full.sql"), StandardCharsets.UTF_8);
        String[] codes = {
                // 深渊装备/委托/配方
                "abyss_blade", "abyss_bow", "rift_helm", "chaos_plate", "abyss_greaves", "abyss_ring",
                "abyss_probe", "abyss_lord", "c_abyss_blade", "c_abyss_ring",
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
