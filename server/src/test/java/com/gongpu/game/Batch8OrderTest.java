package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第八批第 69 轮：第八批源码序锁——金币里程碑检查必须在 fullView 之前
 * （事件要随本帧下发，视图构建后 push 就晚一帧且会被 drain 吞掉）；
 * 写限流检查必须在 load 之前（被限流的请求不该再触发读档成本）。
 */
class Batch8OrderTest {

    @Test
    void milestoneBeforeViewAndRateLimitBeforeLoad() throws Exception {
        String src = Files.readString(Path.of("src/main/java/com/gongpu/web/GameController.java"));
        int milestone = src.indexOf("checkGoldMilestone(s)");
        int view = src.indexOf("game.fullView(s)", milestone);
        assertTrue(milestone > 0 && view > milestone, "checkGoldMilestone 必须在 fullView 之前调用");
        int rate = src.indexOf("checkWriteRate(uid");
        int load = src.indexOf("saves.load(uid, slot)", rate);
        assertTrue(rate > 0 && load > rate, "写限流检查必须在 load 之前");
    }
}
