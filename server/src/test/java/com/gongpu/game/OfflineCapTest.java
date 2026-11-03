package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第六批第 75 轮：离线结算上限回归——超长离线只按 8 小时封顶计入（防挂机时长爆炸）。 */
class OfflineCapTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void offlineCappedAtEightHours() {
        PlayerState s = NewState.create();   // 村口安全区，只验计时口径
        long now = 1_700_000_000_000L;
        s.lastTickAt = now - 100L * 3600 * 1000;   // 离线 100 小时
        long before = s.stats.playSeconds;
        GameEngine.advance(s, now, GameConst.OFFLINE_EFF, true);
        assertEquals(GameConst.OFFLINE_CAP_HOURS * 3600L, s.stats.playSeconds - before,
                "离线计时应封顶 8 小时");
        assertTrue(s.stats.punches.signum() > 0, "离线期间仍应有出拳成长（村口木桩）");
    }

    @Test
    void shortOfflineNotCapped() {
        PlayerState s = NewState.create();
        long now = 1_700_000_000_000L;
        s.lastTickAt = now - 600_000L;   // 离线 10 分钟
        long before = s.stats.playSeconds;
        GameEngine.advance(s, now, GameConst.OFFLINE_EFF, true);
        assertEquals(600, s.stats.playSeconds - before, "短离线应按实际时长计入");
    }
}
