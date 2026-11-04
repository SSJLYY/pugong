package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 第九批·月常跨月重签（第 38 轮）：旧月标记下 ensure 必须重签且重置全清标记。 */
class MonthlyBoundaryTest {

    @Test
    void staleMonthReissues() {
        PlayerState s = NewState.create();
        MonthlyQuests.ensure(s);
        // 伪造上月状态：已全清且奖已发
        s.monthlyMonth = "2020-01";
        s.monthlyBonusGiven = true;
        s.monthlyTasks.forEach(t -> t.claimed = true);
        MonthlyQuests.ensure(s);
        assertEquals(MonthlyQuests.thisMonth(), s.monthlyMonth);
        assertFalse(s.monthlyBonusGiven, "跨月应重置全清标记");
        assertTrue(s.monthlyTasks.stream().noneMatch(t -> t.claimed), "跨月任务应全新未领");
        assertEquals(3, s.monthlyTasks.size());
    }
}
