package com.gongpu.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 双窗工作台与多抽屉布局源码锁。 */
class DrawerLayoutTest {

    private String html() throws Exception {
        return Files.readString(Path.of("../web/index.html"));
    }

    private String css() throws Exception {
        return Files.readString(Path.of("../web/css/drawer.css"));
    }

    private String drawer() throws Exception {
        return Files.readString(Path.of("../web/js/drawer.js"));
    }

    private String app() throws Exception {
        return Files.readString(Path.of("../web/js/app.js"));
    }

    @Test
    void mainKeepsOnlyCombatAndCurrentWorkspace() throws Exception {
        String s = html();
        String main = s.substring(s.indexOf("<main"), s.indexOf("</main>"));
        assertTrue(main.contains("class=\"panel combat\""));
        assertTrue(main.contains("id=\"utilityPanel\""));
        assertFalse(main.contains("map-panel"), "地图必须移出主屏");
        assertTrue(s.indexOf("id=\"mapDrawer\"") > s.indexOf("</main>"));
        assertTrue(css().contains("grid-template-areas: \"combat utility\""));
    }

    @Test
    void threeIndependentDrawersExist() throws Exception {
        String s = html();
        assertTrue(s.contains("id=\"navDrawer\""));
        assertTrue(s.contains("id=\"mapDrawer\""));
        assertTrue(s.contains("id=\"settingsDrawer\""));
        assertTrue(s.contains("id=\"drawerBackdrop\""));
        assertTrue(drawer().contains("const DRAWERS = { nav: 'navDrawer', map: 'mapDrawer', settings: 'settingsDrawer' }"));
    }

    @Test
    void originalControlsAreRetainedExactlyOnce() throws Exception {
        String s = html();
        List<String> ids = List.of("tabBtnEquip", "tabBtnSkills", "tabBtnQuest", "tabBtnCraft",
                "tabBtnRole", "tabBtnSave", "tabBtnSocial", "tabBtnRank", "zoneBtns", "btnBoss",
                "btnSweep10", "btnSweep100", "btnTheme", "btnFont", "btnFx", "btnMotion", "btnHelp");
        for (String id : ids) {
            String needle = "id=\"" + id + "\"";
            assertEquals(1, (s.length() - s.replace(needle, "").length()) / needle.length(),
                    id + " 必须保留且不能重复");
        }
    }

    @Test
    void mobileUsesBottomDockAndWorkspaceDrawer() throws Exception {
        assertTrue(html().contains("class=\"mobile-dock\""));
        assertTrue(html().contains("data-dock-tab=\"equip\""));
        assertTrue(css().contains("body.utility-open .utility-panel"));
        assertTrue(css().contains("height: min(82dvh, 780px)"));
        assertTrue(css().contains("env(safe-area-inset-bottom)"));
        assertTrue(drawer().contains("function openUtility()"));
        assertTrue(drawer().contains("function closeUtility("));
    }

    @Test
    void drawerAccessibilityIsManaged() throws Exception {
        String s = drawer();
        assertTrue(s.contains("function trapFocus(e)"));
        assertTrue(s.contains("e.key === 'Escape'"));
        assertTrue(s.contains("setAttribute('aria-modal', 'true')"));
        assertTrue(s.contains("setInert(el, !open)"));
        assertTrue(s.contains("safeFocus(drawerReturnFocus)"));
        assertTrue(html().contains("aria-live=\"polite\""));
    }

    @Test
    void workspaceSupportsPersistenceAndKeyboards() throws Exception {
        String s = drawer();
        assertTrue(s.contains("localStorage.setItem('gongpu_workspace', tab)"));
        assertTrue(s.contains("localStorage.getItem('gongpu_workspace')"));
        assertTrue(s.contains("e.code === 'KeyM'"));
        assertTrue(s.contains("e.code === 'KeyN'"));
        assertTrue(s.contains("'ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'"));
        assertTrue(s.contains("gongpu:workspacechange"));
    }

    @Test
    void mapActionsReturnToCombat() throws Exception {
        String s = drawer();
        assertTrue(s.contains("#mapDrawer .zone-btn:not([disabled])"));
        assertTrue(s.contains("#mapDrawer #btnDeepestZone"));
        assertTrue(s.contains("#mapDrawer #btnBoss"));
        assertTrue(s.contains("if (activeDrawer === 'map') closeDrawer()"));
    }

    @Test
    void themesAndReducedMotionRemainAvailable() throws Exception {
        String css = css();
        assertTrue(css.contains("body.theme-light .side-drawer"));
        assertTrue(css.contains("body.theme-ink .side-drawer"));
        assertTrue(css.contains("body.theme-neon .side-drawer"));
        assertTrue(css.contains("body.theme-aurora .side-drawer"));
        assertTrue(css.contains("body.theme-tide .side-drawer"));
        assertTrue(css.contains("body.theme-ember .side-drawer"));
        assertTrue(css.contains("prefers-reduced-motion: reduce"));
        assertTrue(css.contains("forced-colors: active"));
        assertTrue(app().contains("document.documentElement.style.colorScheme"));
    }

    @Test
    void floatingControlsAvoidMobileDock() throws Exception {
        String css = css();
        assertTrue(html().contains("id=\"audioSettingsSlot\""));
        assertTrue(drawer().contains("audioSlot.appendChild(audioButton)"));
        assertTrue(css.contains(".audio-settings-slot button#gp-audio-btn"));
        assertTrue(css.contains("position: static !important"));
        assertTrue(css.contains("#butlerFab { bottom: calc(var(--dock-height) + 64px"));
    }

    @Test
    void newAssetsJoinTheSameCacheVersion() throws Exception {
        String s = html();
        assertTrue(s.contains("css/drawer.css?v=250"));
        assertTrue(s.contains("js/drawer.js?v=250"));
        assertFalse(s.contains("v=234"), "旧静态版本必须全部升级");
        assertTrue(s.indexOf("js/drawer.js?v=250") < s.indexOf("js/app.js?v=250"),
                "抽屉控制器必须先于原游戏逻辑初始化");
    }
}
