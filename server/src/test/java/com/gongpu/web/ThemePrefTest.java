package com.gongpu.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第九批第 61 轮：水墨主题（参照文字修仙布局）前端接线源码锁——
 * 主题轮换、body 类切换、CSS 变量块、叙事条与战力芯片缺一不可，防后续改版误删。
 */
class ThemePrefTest {

    @Test
    void inkThemeWiredEndToEnd() throws Exception {
        String js = Files.readString(Path.of("../web/js/app.js"));
        String css = Files.readString(Path.of("../web/css/v2.css"));
        String html = Files.readString(Path.of("../web/index.html"));
        assertTrue(js.contains("'ink'"), "主题轮换须含 ink");
        assertTrue(js.contains("theme-ink"), "applyThemePref 须切换 theme-ink 类");
        assertTrue(js.contains("水墨"), "主题标签须有水墨");
        assertTrue(css.contains("body.theme-ink{"), "v2.css 须有水墨变量块");
        assertTrue(css.contains(".story-line"), "叙事条样式须存在");
        assertTrue(html.contains("id=\"storyLine\""), "战斗区须有叙事条元素");
        assertTrue(html.contains("id=\"powerChip\""), "顶栏须有综合战力芯片");
        assertTrue(js.contains("powerChip"), "战力芯片须有渲染逻辑");
        assertTrue(js.contains("storyLine"), "叙事条须有渲染逻辑");
    }
}
