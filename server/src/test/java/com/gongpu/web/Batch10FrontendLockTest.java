package com.gongpu.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十批前端接线锁：霓虹主题五处接线、新称号与签名依赖、混沌界文案表、时停技能行。 */
class Batch10FrontendLockTest {

    private static String app() throws Exception {
        return Files.readString(Path.of("../web/js/app.js"), StandardCharsets.UTF_8);
    }

    private static String css() throws Exception {
        return Files.readString(Path.of("../web/css/v2.css"), StandardCharsets.UTF_8);
    }

    @Test
    void neonThemeWired() throws Exception {
        String js = app();
        assertTrue(js.contains("theme-neon"), "applyThemePref 应切换 theme-neon");
        assertTrue(js.contains("'neon'"), "主题轮换应含 neon");
        assertTrue(js.contains("霓虹"), "THEME_LABEL 应含霓虹");
        assertTrue(css().contains("body.theme-neon"), "v2.css 应有霓虹变量块");
    }

    @Test
    void batch10TitlesPresent() throws Exception {
        String js = app();
        assertTrue(js.contains("万兽之王"), "缺万兽之王称号");
        assertTrue(js.contains("混沌主宰"), "缺混沌主宰称号");
        assertTrue(js.contains("boss_chaos"), "混沌主宰须以 boss_chaos 为条件");
        assertTrue(js.indexOf("'混沌主宰'") > js.indexOf("'虚空行者'"), "混沌主宰应压轴于虚空行者");
    }

    @Test
    void chaosZoneTextsAndTimeStop() throws Exception {
        String js = app();
        assertTrue(js.contains("chaos: '≥20亿血/双防3000/攻150000'"), "ZONE_REQ_TEXT 缺混沌界");
        assertTrue(js.contains("混沌核心（售 6000）"), "ZONE_LOOT 缺混沌核心");
        assertTrue(js.contains("'time_stop'"), "扩展技能行应含 time_stop");
        assertTrue(js.contains("chaos_pot"), "CHIP_META 应含 chaos_pot");
        assertTrue(js.contains("chaos_core"), "材料速览应含混沌核心");
    }

    @Test
    void petChipAndMotionSwitch() throws Exception {
        String js = app();
        String html = Files.readString(Path.of("../web/index.html"), StandardCharsets.UTF_8);
        assertTrue(html.contains("petChip"), "index.html 应有 petChip");
        assertTrue(js.contains("gongpu_motion"), "静态模式偏好键应接线");
        assertTrue(css().contains(".motion-off"), "v2.css 应有 motion-off 规则");
    }
}
