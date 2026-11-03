package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第六批第 38 轮：成就奖励一键全领——只领已解锁未领的，重复领 0 入账不报错。 */
class ClaimAllTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void claimsAllPendingOnce() {
        PlayerState s = NewState.create();
        s.achievements.add("first_kill");    // 100 档
        s.achievements.add("kill_100");      // 500 档
        s.achievements.add("gold_1m");       // 5000 档
        s.achClaimed.add("kill_100");        // 已领，不重复
        s.gold = Num.ZERO;
        GameService gs = new GameService();
        Map<String, Object> r = gs.claimAllAchievements(s);
        assertEquals(2, r.get("claimedCount"), "应领 2 项");
        assertEquals(5100L, r.get("totalReward"), "100 + 5000 = 5100");
        assertEquals(0, s.gold.compareTo(Num.of(5100)), "金币应入账 5100");
        Map<String, Object> again = gs.claimAllAchievements(s);
        assertEquals(0, again.get("claimedCount"), "再领应为 0 项");
        assertEquals(0L, again.get("totalReward"));
    }
}
