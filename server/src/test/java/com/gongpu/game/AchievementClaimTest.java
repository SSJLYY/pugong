package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.ApiException;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 第五批第 7 轮：成就奖励领取——已解锁可领一次（分档金额）、未解锁/重复领/未知 id 拒绝。
 */
class AchievementClaimTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void rewardTiers() {
        assertEquals(100L, Achievements.rewardOf("first_punch"), "入门档");
        assertEquals(500L, Achievements.rewardOf("kill_100"), "进阶档（默认）");
        assertEquals(2000L, Achievements.rewardOf("codex_all"), "高阶档");
        assertEquals(5000L, Achievements.rewardOf("best_hit_1m"), "巅峰档");
    }

    @Test
    void claimFlow() {
        PlayerState s = NewState.create();
        GameService g = new GameService();
        assertThrows(ApiException.class, () -> g.claimAchievement(s, "first_punch"), "未解锁不可领");
        s.achievements.add("first_punch");
        Map<String, Object> r = g.claimAchievement(s, "first_punch");
        assertEquals(100L, r.get("reward"));
        assertEquals(0, s.gold.compareTo(Num.of(100)), "金币应入账 100");
        assertThrows(ApiException.class, () -> g.claimAchievement(s, "first_punch"), "不可重复领取");
        assertThrows(ApiException.class, () -> g.claimAchievement(s, "no_such"), "未知成就应拒绝");
    }
}
