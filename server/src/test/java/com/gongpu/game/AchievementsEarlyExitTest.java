package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第二十批·轮1：成就评估早退——全解锁直接返回空（跳过面板/套装计数构建），
 * 未解锁时判定结果不变。
 */
class AchievementsEarlyExitTest {

    @Test
    void allUnlockedReturnsEmptyImmediately() {
        PlayerState s = NewState.create();
        for (Achievements.Def d : Achievements.ALL) {
            s.achievements.add(d.id());
        }
        assertTrue(Achievements.evaluate(s).isEmpty(), "全解锁必须零结果零重活");
    }

    @Test
    void partialUnlockStillEvaluates() {
        PlayerState s = NewState.create();
        // 先解锁全部，再摘掉 first_punch——新档一帧即重新达标
        for (Achievements.Def d : Achievements.ALL) {
            s.achievements.add(d.id());
        }
        s.achievements.remove("first_punch");
        s.stats.lifetimePunches = java.math.BigDecimal.ONE;
        assertEquals(java.util.List.of("first_punch"), Achievements.evaluate(s));
    }

    @Test
    void freshStateFirstPunchStillWorks() {
        PlayerState s = NewState.create();
        s.stats.lifetimePunches = java.math.BigDecimal.ONE;
        assertTrue(Achievements.evaluate(s).contains("first_punch"));
    }

    /** 第二十批·轮5：静态奖励表与逐查 rewardOf 口径一致且不可变。 */
    @Test
    void rewardsTableMatchesRewardOf() {
        assertEquals(Achievements.ALL.size(), Achievements.REWARDS.size());
        for (Achievements.Def d : Achievements.ALL) {
            assertEquals(Achievements.rewardOf(d.id()), Achievements.REWARDS.get(d.id()));
        }
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> Achievements.REWARDS.put("x", 1L), "奖励表必须不可变");
    }
}
