package com.gongpu.game;

import com.gongpu.account.AccountService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第六批第 39 轮：连签奖励——streak 预测与原子领取 CASE 同口径，
 * 加成金币封顶 7 天、每 7 天周奖。
 */
class DailyStreakTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

    @Test
    void streakContinuesFromYesterday() {
        assertEquals(4, AccountService.nextStreak(TODAY.minusDays(1), 3, TODAY), "昨日签过应 +1");
        assertEquals(1, AccountService.nextStreak(TODAY.minusDays(2), 3, TODAY), "断一天应重置为 1");
        assertEquals(1, AccountService.nextStreak(null, 0, TODAY), "从未签到从 1 起");
    }

    @Test
    void bonusGoldCapsAtSevenDays() {
        assertEquals(50L, AccountService.streakBonusGold(1));
        assertEquals(350L, AccountService.streakBonusGold(7));
        assertEquals(350L, AccountService.streakBonusGold(30), "封顶 7 天");
    }

    @Test
    void weeklyBonusEverySeven() {
        assertTrue(AccountService.streakWeeklyBonus(7));
        assertTrue(AccountService.streakWeeklyBonus(14));
        assertFalse(AccountService.streakWeeklyBonus(6));
        assertFalse(AccountService.streakWeeklyBonus(0));
    }
}
