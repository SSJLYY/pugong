package com.gongpu.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第九批第 64 轮：源码序锁——防第七批同类事故复发：
 * 月常 ensure 必须在 runLocked 落库之前；monthlyTasks/pet 必须挂 fullView 本体而非管家小回执。
 */
class Batch9OrderTest {

    @Test
    void monthlyEnsureRunsBeforePersist() throws Exception {
        String src = Files.readString(Path.of("src/main/java/com/gongpu/web/GameController.java"));
        int ensure = src.indexOf("MonthlyQuests.ensure(s);");
        int persist = src.indexOf("saves.persist(uid, slot, s, null);");
        assertTrue(ensure > 0, "runLocked 须调用 MonthlyQuests.ensure");
        assertTrue(persist > ensure, "ensure 必须在首次 persist 之前（否则月常签发永不落库）");
    }

    @Test
    void batch9FieldsLiveInFullViewBody() throws Exception {
        String src = Files.readString(Path.of("src/main/java/com/gongpu/game/GameService.java"));
        int fv = src.indexOf("public Map<String, Object> fullView(PlayerState s)");
        assertTrue(fv > 0, "fullView 本体须存在");
        String body = src.substring(fv);
        assertTrue(body.contains("m.put(\"monthlyTasks\""), "monthlyTasks 须挂 fullView 本体");
        assertTrue(body.contains("petView(s)") || body.contains("m.put(\"pet\""),
                "宠物视图须挂 fullView 本体");
    }
}
