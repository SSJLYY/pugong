package com.gongpu.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第十五批前端百轮增强源码锁：主战斗（A）/ 外壳动效（B）/ 网络与运维（C）三线接线不退化。
 * <p>本轮刻意只锁「字符串存在」而不锁具体实现，允许后续等价重构。</p>
 */
class Batch15FrontendLockTest {

    private static String read(String p) throws Exception {
        return Files.readString(Path.of("../" + p), StandardCharsets.UTF_8);
    }

    private String app() throws Exception {
        return read("web/js/app.js");
    }

    private String html() throws Exception {
        return read("web/index.html");
    }

    private String style() throws Exception {
        return read("web/css/style.css");
    }

    // ==================== A 线：主战斗与信息呈现 ====================

    @Test
    void stealthTitleAndAscendProgress() throws Exception {
        // R1 低调英文页签（公司环境不暴露游戏内容）
        assertTrue(html().contains("<title>Gongpu</title>"), "页签应为低调英文 Gongpu");
        assertTrue(app().contains("| Gongpu"), "document.title 动态赋值应英文低调化");
        // R2 飞升进度条：攻速→1e44 的对数进度
        assertTrue(html().contains("id=\"ascendProg\""), "index 应有飞升进度条容器");
        assertTrue(app().contains("Math.log10(Math.max(aspd, 1)) / 44"), "进度应按 log10(ASPD)/44 计算");
        assertTrue(style().contains(".ascend-prog-fill"), "style.css 应有进度条填充样式");
    }

    @Test
    void genTierAndRateHud() throws Exception {
        // R3 世代徽章四档
        assertTrue(app().contains("gen-t4"), "世代应挂四档位类");
        assertTrue(style().contains("#gen.gen-t4"), "style.css 应有虹光档样式");
        // R4 HUD 双速率
        assertTrue(html().contains("id=\"hudPps\""), "index 应有 HUD 双速率位");
        assertTrue(app().contains("S._ratePrev"), "双速率应走会话内差分");
        // R5 HP 趋势迷你图
        assertTrue(html().contains("id=\"hpSpark\""), "index 应有 HP 火花线画布");
        assertTrue(app().contains("S._hpHist"), "火花线应维护 120 帧历史");
    }

    @Test
    void networkStatesAndLogEnhancements() throws Exception {
        // R6 断线三态
        assertTrue(app().contains("S._netFails"), "断线应计数连败次数");
        assertTrue(style().contains("#netState.net-bad"), "style.css 应有红态样式");
        // R7 后台标签页不飘字
        assertTrue(app().indexOf("if (document.hidden) return;") > 0, "floatDmg 应在后台直接返回");
        // R9 日志时间戳 / 回底按钮 / 新行高亮
        assertTrue(app().contains("evBottomBtn"), "日志区应有回底按钮");
        assertTrue(app().contains("ev-ts"), "日志行应带时间戳");
        assertTrue(style().contains(".event-line.event-fresh"), "style.css 应有新行高亮");
    }

    @Test
    void combatInfoRefinements() throws Exception {
        // R8 尸检折叠
        assertTrue(html().contains("id=\"autopsyToggle\""), "index 应有尸检折叠按钮");
        // R10 药剂低库存角标
        assertTrue(app().contains("potion-low"), "药剂条目应挂低库存角标");
        assertTrue(style().contains(".potion-item.potion-low::after"), "style.css 应有「低」角标");
        // R11 ? 键帮助
        assertTrue(app().contains("case 'Slash'"), "bindHotkeys 应支持 ? 键呼出帮助");
        // R12 Boss 就绪态 / R13 下一站推荐
        assertTrue(app().contains("boss-fighting"), "Boss 按钮应有交战中态");
        assertTrue(style().contains(".zone-btn.next-zone"), "未达标首区应有脉冲推荐样式");
        // R14 词条悬停 / R15 击杀预估
        assertTrue(app().contains("has-trait"), "词条应可悬停查看完整说明");
        assertTrue(app().contains("estPunches"), "应按 estPunches 渲染击杀预估");
    }

    @Test
    void infoPanelsAndToastQueue() throws Exception {
        // R16 赋能行详情卡
        assertTrue(app().contains("本世普攻赋能"), "赋能行应带详情 title");
        // R17 属性卡每秒增量
        assertTrue(app().contains("dataset.rate"), "属性卡应标注每秒增量");
        assertTrue(style().contains("td[data-rate]::after"), "style.css 应渲染增量小字");
        // R18 称号弹层 / R19 宠物直达
        assertTrue(app().contains("我的称号（"), "称号芯片应可弹出全量称号");
        assertTrue(app().contains("pet-focus"), "宠物芯片应直达委托页并高亮");
        // R20 toast 堆叠
        assertTrue(app().contains("pushToastStack"), "toast 应有堆叠层");
        assertTrue(style().contains(".toast-stack-item"), "style.css 应有堆叠样式");
        // R21 飘字分档 / R22 金币大额闪
        assertTrue(app().contains("navigator.deviceMemory"), "飘字预算应按设备内存分档");
        assertTrue(app().contains("gold-up"), "金币大额进出应闪 HUD");
        // R23 大数口径补强
        assertTrue(app().contains("Math.round(n * 100) / 100"), "fmt 小于 1 万应保留小数");
    }

    @Test
    void safetyAndTaskRefinements() throws Exception {
        // R24 尘归尘二次确认
        assertTrue(app().contains("这是本世成果将永久失去的最后确认"), "重开档应二次确认");
        // R25 签到红点
        assertTrue(app().contains("daily-todo"), "当日未签到应有红点");
        assertTrue(style().contains("#btnDaily.daily-todo"), "style.css 应有红点样式");
        // R26 增益减益分色 / R27 沉默置灰 / R28 村口隐藏逃跑
        assertTrue(app().contains("data-grp="), "buff 芯片应标增益/减益分组");
        assertTrue(app().contains("classList.toggle('silenced'"), "沉默时技能应置灰");
        assertTrue(app().contains("fb.hidden = inVillage"), "村口应隐藏逃跑按钮");
        // R29 冷却角标 / R30 连环掌强调
        assertTrue(app().contains("cd-badge"), "技能冷却应有角标");
        assertTrue(style().contains(".buff-chip.flurry-chip"), "连环掌芯片应强调");
        // R31 自动挂机状态色 / R32 扫荡成本预览
        assertTrue(app().contains("auto-on"), "自动挂机应区分开关状态");
        assertTrue(app().contains("50 + 深度"), "扫荡悬停应预览成本");
        // R33 同步四态 / R34 摘要补字段
        assertTrue(app().contains("同步失败，重试中"), "存档状态应有失败态");
        assertTrue(app().contains("转生 ' + fmt((v.stats || {}).rebirths"), "战绩摘要应含转生次数");
        // R38 榜单我的名次 / R41 黑市售罄 / R42 补给成本 / R43 可交付 / R45 空状态指引
        assertTrue(app().contains("rank-me"), "榜单应高亮我的名次");
        assertTrue(app().contains("market-sold"), "黑市应标售罄");
        assertTrue(app().contains("按当前标价约需"), "补给应预览成本合计");
        assertTrue(app().contains("deliver-badge"), "任务应标可交付");
        assertTrue(app().contains("data-goto"), "空状态应带行动指引链接");
    }

    // ==================== B 线：外壳 / 动效 / 引导 ====================

    @Test
    void drawerPolishLanded() throws Exception {
        String css = read("web/css/drawer.css");
        assertTrue(css.contains("transform: translateX(-100%)"), "左抽屉应方向化滑入");
        assertTrue(css.contains("--focus-ring"), "应有统一焦点圈变量");
        assertTrue(css.contains(".dock-btn:active"), "dock 应有按压反馈");
        assertTrue(css.contains(".drawer-skeleton"), "应有抽屉骨架屏");
        assertTrue(css.contains("@media print"), "应有打印样式");

        String drawer = read("web/js/drawer.js");
        assertTrue(drawer.contains("function syncMobileDock(tab)"), "dock 高亮应随工作区同步");
        assertTrue(drawer.contains("showDrawerSkeleton"), "抽屉打开应挂骨架屏");
        assertTrue(drawer.contains("initSettingsFold"), "设置分组应可折叠");
        assertTrue(drawer.contains("initThemeFlashGuard"), "主题切换应防闪白");
        assertTrue(drawer.contains("initFontXlBootstrap"), "超大字号应启动挂类");
    }

    @Test
    void themesAndTutorialLanded() throws Exception {
        String v2 = read("web/css/v2.css");
        assertTrue(v2.contains(".tut-dots"), "v2.css 应有教学进度点样式");
        assertTrue(v2.contains("--tide-accent"), "深海主题应有统一强调色");

        String tut = read("web/js/tutorial.js");
        assertTrue(tut.contains("gongpu_tut_done"), "教程应记忆已完成步骤");
        assertTrue(tut.contains("markStepDone"), "教程应逐步骤标记完成");
        assertTrue(tut.contains("tut-dots"), "教程应渲染进度点");
        assertTrue(tut.contains("点左上 ☰ 打开功能目录"), "教程应含抽屉导航步");
        assertTrue(tut.contains("飞升转世按钮"), "教程应含飞升就绪步");

        String audio = read("web/js/audio.js");
        // 第十五批：音量与分类开关、告警去重、新音效入口
        assertTrue(audio.contains("gongpu_vol"), "音效应支持音量记忆");
        assertTrue(audio.contains("gongpu_sfx_cats"), "音效应支持分类开关");
        assertTrue(audio.contains("warnOnce"), "音效告警应去重");
        assertTrue(audio.contains("'coin'") || audio.contains("coin"), "音效应含金币音效入口");
    }

    // ==================== C 线：网络层 / 成就 / 运维端 ====================

    @Test
    void apiResilienceLanded() throws Exception {
        String api = read("web/js/api.js");
        assertTrue(api.contains("AbortController"), "请求应有超时中断");
        assertTrue(api.contains("ERR_TIMEOUT"), "超时应抛统一错误码");
        assertTrue(api.contains("gongpu:unauthorized"), "401 应派发统一事件");
        assertTrue(api.contains("gongpu_token_v2"), "token 键应版本化");
        assertTrue(api.contains("LEGACY_TOKEN_KEYS"), "旧 token 键应迁移");
        assertTrue(api.contains("getQueue"), "GET 应有并发节流队列");
        assertTrue(api.contains("__gp_last_latency"), "应有响应耗时埋点");
        assertTrue(api.contains("/sweep") && api.contains("/inherit"), "既有端点不得丢失");
    }

    @Test
    void achievementsAndAdminLanded() throws Exception {
        String ach = read("web/js/achievements.js");
        assertTrue(ach.contains("ach-group-title"), "成就应分组渲染");
        assertTrue(ach.contains("function pctOf("), "进度应统一百分比口径");
        assertTrue(ach.contains("ach-stat-head"), "应有完成率统计头");
        assertTrue(ach.contains("setAchFilter"), "应暴露搜索过滤入口");
        assertTrue(ach.contains("window.__achievements = { check: check, render: render, drain: drain"),
                "既有三键导出签名不得变更");

        String admin = read("web/js/admin.js");
        assertTrue(admin.contains("onTableSortClick"), "表格列头应可排序");
        assertTrue(admin.contains("gongpu_admin_dense"), "表格密度应记忆");
        assertTrue(admin.contains("gongpu_admin_refresh"), "自动刷新间隔应记忆");
        assertTrue(admin.contains("health-dot"), "健康状态应有徽章");
        assertTrue(admin.contains("auditLevel"), "审计日志应按级别过滤");
        assertTrue(read("web/admin.html").contains("<title>Gongpu Ops</title>"), "运维端页签保持英文低调");
    }
}
