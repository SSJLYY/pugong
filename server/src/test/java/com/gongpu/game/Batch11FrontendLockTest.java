package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十一批前端接线源码锁：极光主题/日志搜索/标题 DPS/新称号/鸿蒙文案/进化与觉醒按钮。 */
class Batch11FrontendLockTest {

    private String app() throws Exception {
        return Files.readString(Path.of("../web/js/app.js"));
    }

    private String css() throws Exception {
        return Files.readString(Path.of("../web/css/v2.css"));
    }

    private String html() throws Exception {
        return Files.readString(Path.of("../web/index.html"));
    }

    @Test
    void auroraThemeWired() throws Exception {
        String app = app();
        String css = css();
        assertTrue(css.contains("body.theme-aurora"), "css 应有极光主题变量块");
        assertTrue(app.contains("aurora:'🌌 极光'") || app.contains("aurora: '🌌 极光'"), "THEME_LABEL 应有极光");
        assertTrue(app.contains("theme-aurora"), "applyThemePref 应切换 theme-aurora");
        assertTrue(app.contains("'dark','light','star','ink','neon','aurora'")
                || app.contains("'dark', 'light', 'star', 'ink', 'neon', 'aurora'"), "主题轮换应含 aurora 且压轴");
    }

    @Test
    void logSearchAndTitleDpsWired() throws Exception {
        String app = app();
        String html = html();
        assertTrue(html.contains("id=\"logSearch\""), "index 应有日志搜索框");
        assertTrue(app.contains("logSearchKw"), "app 应有日志搜索状态");
        assertTrue(app.contains("gongpu_dps_max"), "标题速览应读 DPS 峰值");
    }

    @Test
    void batch11ContentWired() throws Exception {
        String app = app();
        assertTrue(app.contains("hongmeng: '≥500亿血/双防8000/攻400000'"), "区域门槛文案应有鸿蒙界");
        assertTrue(app.contains("hongmeng: '🌌 鸿蒙紫气（售 20000）'"), "区域收益应有鸿蒙紫气");
        assertTrue(app.contains("'sword_rain'"), "扩展技能行应含万剑归宗");
        assertTrue(app.contains("sword_rain: ['🗡️'"), "buff 芯片应有万剑归宗");
        assertTrue(app.contains("hongmeng_pot: ['🌌'"), "buff 芯片应有鸿蒙灵液");
        assertTrue(app.contains("taixu_pot: ['🕳️'"), "buff 芯片应有太虚神液");
        assertTrue(app.contains("data-pet-evolve"), "宠物卡应有进化按钮");
        assertTrue(app.contains("evolvePet"), "应接 evolvePet 端点");
        assertTrue(app.contains("data-act=\"awaken\"") || app.contains('"' + "awaken" + '"'), "背包应有觉醒动作");
        assertTrue(app.contains("API.awaken"), "应接 awaken 端点");
        // 称号：鸿蒙主宰必须在混沌主宰之后（压轴）
        int chaos = app.indexOf("混沌主宰");
        int hongmeng = app.indexOf("鸿蒙主宰");
        assertTrue(chaos > 0 && hongmeng > chaos, "鸿蒙主宰应压轴于混沌主宰之后");
        assertTrue(app.contains("boss_hongmeng"), "称号应查鸿蒙 Boss 首杀");
        assertTrue(app.contains("进化宗师"), "应有进化宗师称号");
    }
}
