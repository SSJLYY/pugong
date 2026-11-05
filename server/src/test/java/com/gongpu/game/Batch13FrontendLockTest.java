package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十三批前端接线源码锁：熔火主题/扫荡按钮/传承 UI/太初文案/双称号/太初之心镜像。 */
class Batch13FrontendLockTest {

    private String app() throws Exception {
        return Files.readString(Path.of("../web/js/app.js"));
    }

    private String css() throws Exception {
        return Files.readString(Path.of("../web/css/v2.css"));
    }

    private String html() throws Exception {
        return Files.readString(Path.of("../web/index.html"));
    }

    private String api() throws Exception {
        return Files.readString(Path.of("../web/js/api.js"));
    }

    @Test
    void emberThemeWired() throws Exception {
        String app = app();
        assertTrue(css().contains("body.theme-ember"), "css 应有熔火主题变量块");
        assertTrue(app.contains("ember: '🔥 熔火'"), "THEME_LABEL 应有熔火");
        assertTrue(app.contains("theme-ember"), "applyThemePref 应切换 theme-ember");
        assertTrue(app.contains("'tide', 'ember'"), "主题轮换应以 ember 压轴");
        assertTrue(app.contains("八种主题"), "帮助应写八种主题");
    }

    @Test
    void sweepAndInheritWired() throws Exception {
        String app = app();
        String html = html();
        assertTrue(html.contains("id=\"btnSweep10\"") && html.contains("id=\"btnSweep100\""), "index 应有扫荡按钮");
        assertTrue(api().contains("/sweep"), "api 应有 sweep 方法");
        assertTrue(api().contains("/inherit"), "api 应有 inherit 方法");
        assertTrue(app.contains("btnSweep10"), "app 应接线扫荡按钮");
        assertTrue(app.contains("data-act=\"inherit\"") || app.contains("data-act=\\\"inherit\\\"")
                || app.contains("act === 'inherit'"), "背包应有传承入口");
        assertTrue(app.contains("act === 'inherit'"), "app 应有传承处理分支");
    }

    @Test
    void taichuTextAndTitlesWired() throws Exception {
        String app = app();
        assertTrue(app.contains("taichu: '≥5万亿血/双防50000/攻2500000'"), "ZONE_REQ_TEXT 应有太初");
        assertTrue(app.contains("太初源晶（售 200000）"), "ZONE_LOOT 应有太初");
        assertTrue(app.contains("origin_slash"), "扩展技能/buff 芯片应有源初斩");
        assertTrue(app.contains("taichu_pot"), "buff 芯片应有太初源液");
        int guixuLord = app.indexOf("归墟主宰");
        int inheritMaster = app.indexOf("传承宗师");
        int taichuLord = app.indexOf("太初主宰");
        assertTrue(guixuLord > 0 && inheritMaster > guixuLord && taichuLord > inheritMaster,
                "称号顺序应为 归墟主宰 → 传承宗师 → 太初主宰（压轴）");
        assertTrue(app.contains("taichu_heart"), "天赋双镜像应有太初之心");
        assertTrue(app.contains("太初与传承"), "帮助应有太初与传承行");
        assertTrue(app.contains("taichu_crystal"), "材料速览/出售表应有太初源晶");
    }
}
