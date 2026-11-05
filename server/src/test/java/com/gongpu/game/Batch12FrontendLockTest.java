package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十二批前端接线源码锁：深海主题/成就搜索/最深区域按钮/精通角标/归墟文案/双称号/星辰之心镜像。 */
class Batch12FrontendLockTest {

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
    void tideThemeWired() throws Exception {
        String app = app();
        assertTrue(css().contains("body.theme-tide"), "css 应有深海主题变量块");
        assertTrue(app.contains("tide: '🌊 深海'"), "THEME_LABEL 应有深海");
        assertTrue(app.contains("theme-tide"), "applyThemePref 应切换 theme-tide");
        assertTrue(app.contains("'aurora', 'tide'"), "主题轮换应以 tide 压轴");
    }

    @Test
    void achSearchAndDeepestZoneWired() throws Exception {
        String app = app();
        String html = html();
        assertTrue(html.contains("id=\"achSearch\""), "index 应有成就搜索框");
        assertTrue(app.contains("achSearchKw"), "app 应有成就搜索关键词状态");
        assertTrue(html.contains("id=\"btnDeepestZone\""), "index 应有最深区域按钮");
        assertTrue(app.contains("btnDeepestZone"), "app 应接线最深区域按钮");
    }

    @Test
    void masteryBadgeAndGuixuTextWired() throws Exception {
        String app = app();
        assertTrue(app.contains("zoneKills"), "区域按钮/称号应读 zoneKills");
        assertTrue(app.contains("精通+"), "区域按钮应有精通角标文案");
        assertTrue(app.contains("guixu: '≥5000亿血/双防20000/攻1000000'"), "ZONE_REQ_TEXT 应有归墟");
        assertTrue(app.contains("归墟珠（售 60000）"), "ZONE_LOOT 应有归墟");
        assertTrue(app.contains("heaven_wrath"), "扩展技能/buff 芯片应有天罚");
        assertTrue(app.contains("guixu_pot"), "buff 芯片应有归墟琼浆");
    }

    @Test
    void titlesOrderAndTalentMirrorWired() throws Exception {
        String app = app();
        int mastery = app.indexOf("精通大师");
        int guixuLord = app.indexOf("归墟主宰");
        int hongmengLord = app.indexOf("鸿蒙主宰");
        assertTrue(hongmengLord > 0 && mastery > hongmengLord && guixuLord > mastery,
                "称号顺序应为 鸿蒙主宰 → 精通大师 → 归墟主宰（压轴）");
        assertTrue(app.contains("star_heart"), "天赋双镜像应有星辰之心");
        assertTrue(app.contains("归墟与精通"), "帮助应有归墟与精通行");
    }
}
