package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第七批第 72 轮：fullView 必带 serverTime 且单调不减——前端 applyView 的过期帧守卫靠它丢弃乱序旧帧。
 */
class ServerTimeViewTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void fullViewCarriesMonotonicServerTime() {
        PlayerState s = NewState.create();
        GameService game = new GameService();
        Map<String, Object> v1 = game.fullView(s);
        Object t1 = v1.get("serverTime");
        assertNotNull(t1, "fullView 应下发 serverTime");
        long first = ((Number) t1).longValue();
        assertTrue(first > 0, "serverTime 应为毫秒时间戳");
        Map<String, Object> v2 = game.fullView(s);
        long second = ((Number) v2.get("serverTime")).longValue();
        assertTrue(second >= first, "后构建的视图 serverTime 不得更小");
    }
}
