package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 第八批第 74 轮：成就里程碑奖金——累计已领达 15/30/45/60 档各发一次奖金
 * （10k/30k/80k/200k），掩码防重发；单领与全领两条路径都结算。
 */
class AchMilestoneRewardTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState stateWithUnlocked(int n) {
        PlayerState s = NewState.create();
        Achievements.ALL.stream().limit(n).forEach(d -> s.achievements.add(d.id()));
        return s;
    }

    @Test
    void claimAllPaysMilestoneOnce() {
        PlayerState s = stateWithUnlocked(31);
        GameService g = new GameService();
        Map<String, Object> r = g.claimAllAchievements(s);
        // 跨 15 与 30 两档：10000 + 30000
        assertEquals(40000L, r.get("milestoneBonus"));
        // 再领剩余也不重发已付档位
        Achievements.ALL.stream().skip(31).limit(14).forEach(d -> s.achievements.add(d.id()));
        Map<String, Object> r2 = g.claimAllAchievements(s);
        assertEquals(80000L, r2.get("milestoneBonus"), "达 45 档应只发第三档 80000");
        Map<String, Object> r3 = g.claimAllAchievements(s);
        assertEquals(0L, r3.get("milestoneBonus"), "无新跨档不应再发");
    }

    /** 第九批第 78 轮：75 档（第五档）结算——从 60 档已付状态直达 75 已领，只发 500,000 且不重发旧档。 */
    @Test
    void top75TierPaysOnce() {
        PlayerState s = stateWithUnlocked(75);
        s.achClaimed.addAll(s.achievements); // 已领口径（settle 按 achClaimed 计数）
        s.stats.achMilestonePaid = 0b01111; // 前四档已发
        long bonus = GameService.settleAchMilestone(s);
        assertEquals(500_000L, bonus, "跨 75 档应发 500,000");
        assertEquals(0L, GameService.settleAchMilestone(s), "75 档只发一次");
    }

    @Test
    void singleClaimAlsoSettles() {
        PlayerState s = stateWithUnlocked(15);
        GameService g = new GameService();
        // 先手动领 14 个（不跨档）
        Achievements.ALL.stream().limit(14).forEach(d -> g.claimAchievement(s, d.id()));
        assertEquals(0, s.stats.achMilestonePaid);
        // 第 15 个跨档
        String fifteenth = Achievements.ALL.get(14).id();
        Map<String, Object> r = g.claimAchievement(s, fifteenth);
        assertEquals(10000L, r.get("milestoneBonus"));
        assertEquals(1, s.stats.achMilestonePaid);
        // 金币 = 15 项奖励 + 10000 里程碑（只验里程碑部分已入账的下限）
        long rewards = Achievements.ALL.stream().limit(15)
                .mapToLong(d -> Achievements.rewardOf(d.id())).sum();
        assertEquals(0, s.gold.compareTo(Num.of(rewards + 10000)));
    }
}
