package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.WeekFields;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 第八批第 38 轮：周界行为——过期周键触发重签（基线按当时计数重拍），
 * 周键与 ISO 周定义一致（WeekFields.ISO，周一起始）。
 */
class WeeklyBoundaryTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void weekKeyMatchesIsoDefinition() {
        LocalDate d = LocalDate.now(GameConst.DAILY_ZONE);
        String expect = d.get(WeekFields.ISO.weekBasedYear()) + "-W"
                + String.format("%02d", d.get(WeekFields.ISO.weekOfWeekBasedYear()));
        assertEquals(expect, WeeklyQuests.thisWeek());
    }

    @Test
    void staleWeekTriggersReissueWithFreshBaseline() {
        PlayerState s = NewState.create();
        WeeklyQuests.ensure(s);
        String issuedWeek = s.weeklyWeek;
        // 伪造上周状态：周键过期 + 标记已全清
        s.weeklyWeek = "2000-W01";
        s.weeklyBonusGiven = true;
        s.stats.lifetimeKills = com.gongpu.common.Num.of(777);
        WeeklyQuests.ensure(s);
        assertEquals(issuedWeek, s.weeklyWeek, "过期周键应被重签为本周");
        assertNotEquals("2000-W01", s.weeklyWeek);
        assertEquals(false, s.weeklyBonusGiven, "新周全清标记应重置");
        // hunt 任务若在签发列表里，其基线应按重签时计数（777 或当时的 lifetimeKills）
        s.weeklyTasks.stream().filter(t -> t.id.equals("hunt")).findFirst()
                .ifPresent(t -> assertEquals("777", t.baseline, "重签基线应按当前终身计数"));
    }
}
