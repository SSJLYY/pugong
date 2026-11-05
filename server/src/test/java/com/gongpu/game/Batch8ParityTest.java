package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第八批第 90 轮：第八批内容总校准——代码侧计数终版口径 + 第八批全部新 code
 * 必须能在整合 SQL 中找到（镜像守恒，防「代码有、库里没有」的幽灵内容）。
 */
class Batch8ParityTest {

    @Test
    void batch8CountsAndCodes() throws Exception {
        assertEquals(47, GameConst.POTIONS.size(), "药剂 45");
        assertEquals(34, GameConst.AFFIXES.size(), "词条 26");
        assertEquals(42, GameConst.PASSIVE_SKILLS.size() + GameConst.ACTIVE_SKILLS.size(), "技能 38");
        assertEquals(29, GameConst.MOB_TRAITS.size(), "怪物词条 25");
        assertEquals(15, GameConst.TALENTS.size(), "天赋 12");
        assertEquals(128, Achievements.ALL.size(), "成就 128");
        assertEquals(11, Sets.ALL.size(), "套装 10");
        String sql = Files.readString(Path.of("src/main/resources/gongpu-full.sql"));
        for (String code : new String[]{
                // 药剂/被动（81/83）
                "focus", "vigor", "gem_sniffer",
                // 词条（82）
                "wuying", "buxi",
                // 委托/配方/装备（84/85/86）
                "star_dust", "void_feast", "c_nebula_helm", "c_galaxy_plate",
                "pulsar_necklace", "comet_boots",
                // 集群一至四代表性新内容
                "star_blade", "quasar_ring", "pierce", "flurry", "armor_pierce"}) {
            assertTrue(sql.contains("'" + code + "'"), "第八批内容 " + code + " 应在 SQL 中");
        }
        // 宝石是代码常量（不在 cfg 表），单独核 GEMS 规模：一阶 6 + 二阶 6
        assertEquals(18, GameConst.GEMS.size(), "宝石 12 种");
    }
}
