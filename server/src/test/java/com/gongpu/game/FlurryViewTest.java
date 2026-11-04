package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第八批第 99 轮收官实测修复锁：flurryHits 必须挂 fullView 本体——
 * 源码序断言 fullView 方法体内含该 put（误挂管家回执时前端永远读不到）。
 */
class FlurryViewTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void flurryHitsHangsOnFullView() throws Exception {
        String src = Files.readString(Path.of("src/main/java/com/gongpu/game/GameService.java"));
        int start = src.indexOf("public Map<String, Object> fullView");
        int next = src.indexOf("\n    public ", start + 10);
        String body = src.substring(start, next > 0 ? next : src.length());
        assertTrue(body.contains("m.put(\"flurryHits\", s.flurryHits)"),
                "fullView 本体必须下发 flurryHits");
        // 运行时同样核对：fullView 输出含该键
        PlayerState s = NewState.create();
        s.flurryHits = 7;
        assertTrue(new GameService().fullView(s).containsKey("flurryHits"));
    }
}
