package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第六批第 67 轮：时钟回拨重锚——回拨后不再冻结推进。 */
class ClockSkewTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void reanchorsOnClockSkew() {
        PlayerState s = NewState.create();
        long now = 1_700_000_000_000L;
        s.lastTickAt = now + 3_600_000L;   // 锚点在未来 1 小时（时钟曾快过又被校回）
        GameEngine.advance(s, now, 1.0, false);
        assertEquals(now, s.lastTickAt, "回拨应重锚 lastTickAt 到当前时间");
        long playedBefore = s.stats.playSeconds;
        GameEngine.advance(s, now + 10_000L, 1.0, false);
        assertTrue(s.stats.playSeconds > playedBefore, "重锚后推进应恢复计时");
    }
}
