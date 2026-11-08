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
        assertTrue(s.contains("css/drawer.css?v=270"));
        assertTrue(s.contains("js/drawer.js?v=270"));
        assertFalse(s.contains("v=250"), "旧静态版本必须全部升级（第十七批升 270）");
        assertFalse(s.contains("v=261"), "旧静态版本必须全部升级（第十七批升 270）");
        assertTrue(s.indexOf("js/drawer.js?v=270") < s.indexOf("js/app.js?v=270"),
                "抽屉控制器必须先于原游戏逻辑初始化");
    }

    // ---------------- 第十七批：功能页内子分组（单屏单模块） ----------------

    @Test
    void questPageSplitIntoFiveSubPanes() throws Exception {
        String s = html();
        String quest = s.substring(s.indexOf("id=\"tabQuest\""), s.indexOf("id=\"tabCraft\""));
        for (String sub : List.of("daily", "weekly", "monthly", "pets", "guild")) {
            assertTrue(quest.contains("data-sub=\"" + sub + "\""), "委托页缺子页签 " + sub);
            assertTrue(quest.contains("data-subpane=\"" + sub + "\""), "委托页缺子面板 " + sub);
        }
        // 除默认每日外，其余子面板初始必须隐藏（单屏只展示一个子模块）
        assertTrue(quest.contains("data-subpane=\"weekly\" hidden"));
        assertTrue(quest.contains("data-subpane=\"monthly\" hidden"));
        assertTrue(quest.contains("data-subpane=\"pets\" hidden"));
        assertTrue(quest.contains("data-subpane=\"guild\" hidden"));
    }

    @Test
    void roleAndSkillsPagesSplitIntoSubPanes() throws Exception {
        String s = html();
        String role = s.substring(s.indexOf("id=\"tabRole\""), s.indexOf("id=\"tabSave\""));
        for (String sub : List.of("ach", "titles", "codex")) {
            assertTrue(role.contains("data-sub=\"" + sub + "\""), "角色页缺子页签 " + sub);
        }
        assertTrue(role.contains("data-subpane=\"titles\" hidden"));
        assertTrue(role.contains("data-subpane=\"codex\" hidden"));
        String skills = s.substring(s.indexOf("id=\"tabSkills\""), s.indexOf("id=\"tabQuest\""));
        for (String sub : List.of("potions", "passives", "market")) {
            assertTrue(skills.contains("data-sub=\"" + sub + "\""), "技能药剂页缺子页签 " + sub);
        }
        assertTrue(skills.contains("data-subpane=\"passives\" hidden"));
        assertTrue(skills.contains("data-subpane=\"market\" hidden"));
    }

    @Test
    void subTabControllerKeepsStateAndKeyboardAccess() throws Exception {
        String s = app();
        assertTrue(s.contains("function activateSubTab("));
        assertTrue(s.contains("function restoreSubTabs("));
        assertTrue(s.contains("gongpu_sub_"));
        assertTrue(s.contains("e.target.closest('.sub-tab')"));
        assertTrue(s.contains("restoreSubTabs();"), "boot 必须恢复子页签记忆");
        // 宠物芯片直达必须先激活 pets 子页签，否则目标在隐藏子面板里
        assertTrue(s.contains("activateSubTab(questPane, 'pets', false)"));
    }

    @Test
    void navDrawerGroupedWithSearch() throws Exception {
        String s = html();
        assertTrue(s.contains("id=\"navSearch\""), "功能目录缺搜索框");
        for (String g : List.of("⚔️ 成长", "📜 冒险", "🏮 社交", "💾 系统")) {
            assertTrue(s.contains(g), "功能目录缺分组 " + g);
        }
        assertTrue(s.contains("class=\"nav-group-title\""));
    }

    @Test
    void combatHotkeysCollapsedAndMapGrouped() throws Exception {
        String s = html();
        assertTrue(s.contains("class=\"hotkey-details\""), "快捷键说明应默认折叠");
        String map = s.substring(s.indexOf("id=\"mapDrawer\""), s.indexOf("id=\"settingsDrawer\""));
        for (String g : List.of("🗺️ 区域选择", "⚔️ 战斗行动", "📚 图鉴参考")) {
            assertTrue(map.contains(g), "地图抽屉缺分组 " + g);
        }
    }
}
