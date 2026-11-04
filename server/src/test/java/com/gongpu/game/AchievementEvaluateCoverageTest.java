package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 第九批第 77 轮：evaluate 全覆盖锁——每个成就 id 必须在 evaluate 的 switch 里有判定分支。
 * 教训来源：pet_first/pet_25 曾只有 Def+progressOf、evaluate 缺判定，导致永不解锁且测试全绿。
 */
class AchievementEvaluateCoverageTest {

    @Test
    void everyAchievementHasEvaluateCase() throws Exception {
        String src = Files.readString(Path.of("src/main/java/com/gongpu/game/Achievements.java"));
        int start = src.indexOf("public static List<String> evaluate");
        int end = src.indexOf("public static", start + 10);
        String body = src.substring(start, end > start ? end : src.length());
        List<String> missing = new ArrayList<>();
        for (Achievements.Def d : Achievements.ALL) {
            if (!body.contains("case \"" + d.id() + "\"")) {
                missing.add(d.id());
            }
        }
        if (!missing.isEmpty()) {
            fail("以下成就在 evaluate 中无判定分支（永不解锁）: " + missing);
        }
        assertTrue(Achievements.ALL.size() >= 80);
    }
}
