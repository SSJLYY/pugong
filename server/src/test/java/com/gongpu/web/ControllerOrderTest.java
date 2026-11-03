package com.gongpu.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第七批实测修复锁：GameController.runLocked 中每日签发（DailyQuests/BlackMarket.ensure）
 * 必须出现在落库（saves.persist）之前。runLocked 的顺序是「动作 → finally 落库 → 建 fullView」，
 * 签发若只在 fullView 里调用，签发结果永不落库：每帧重签、基线贴当前计数、每日任务进度恒 0
 * （内网实测 tal25879 喝两瓶药进度仍为 0 时定位）。
 */
class ControllerOrderTest {

    @Test
    void dailyEnsureRunsBeforePersist() throws Exception {
        String src = Files.readString(
                Path.of("src/main/java/com/gongpu/web/GameController.java"), StandardCharsets.UTF_8);
        int ensureDaily = src.indexOf("DailyQuests.ensure(s)");
        int ensureMarket = src.indexOf("BlackMarket.ensure(s)");
        int persist = src.indexOf("saves.persist(uid, slot, s, null)");
        assertTrue(ensureDaily > 0, "runLocked 应显式调用 DailyQuests.ensure");
        assertTrue(ensureMarket > 0, "runLocked 应显式调用 BlackMarket.ensure");
        assertTrue(ensureDaily < persist, "每日任务签发必须在落库之前");
        assertTrue(ensureMarket < persist, "黑市签发必须在落库之前");
    }
}
