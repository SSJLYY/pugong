package com.gongpu.game;

import com.gongpu.common.ApiException;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** 第九批·月常任务（第 31 轮）：签发/基线/领取口径与周常同构、量级四倍。 */
class MonthlyQuestsTest {

    @Test
    void ensureIssuesThreeTasksWithBaselines() {
        PlayerState s = NewState.create();
        s.stats.lifetimeKills = new BigDecimal("777");
        MonthlyQuests.ensure(s);
        assertEquals(MonthlyQuests.thisMonth(), s.monthlyMonth);
        assertEquals(3, s.monthlyTasks.size());
        assertFalse(s.monthlyBonusGiven);
        // 基线 = 签发时终身计数：刚签发进度为 0
        for (PlayerState.DailyTask t : s.monthlyTasks) {
            assertEquals(0, MonthlyQuests.progressOf(s, t));
        }
        // 重复 ensure 不重签
        String first = s.monthlyTasks.get(0).id;
        MonthlyQuests.ensure(s);
        assertEquals(first, s.monthlyTasks.get(0).id);
    }

    @Test
    void claimRequiresDoneAndPays() {
        PlayerState s = NewState.create();
        MonthlyQuests.ensure(s);
        PlayerState.DailyTask t = s.monthlyTasks.get(0);
        assertThrows(ApiException.class, () -> MonthlyQuests.claim(s, t.id));
        // 把基线压到负数制造已达标（counter − baseline ≥ target）
        t.baseline = "-" + (t.target + 1);
        BigDecimal before = s.gold;
        long reward = MonthlyQuests.claim(s, t.id);
        assertEquals(t.rewardGold, reward);
        assertEquals(0, before.add(BigDecimal.valueOf(reward)).compareTo(s.gold));
        assertEquals(1, s.stats.monthlyClaims);
        assertThrows(ApiException.class, () -> MonthlyQuests.claim(s, t.id));
    }

    @Test
    void monthKeyShape() {
        assertTrue(MonthlyQuests.thisMonth().matches("\\d{4}-\\d{2}"));
        assertEquals(300_000, MonthlyQuests.ALL_CLEAR_BONUS);
        assertEquals(3, MonthlyQuests.ALL_CLEAR_GEMS);
    }
}
