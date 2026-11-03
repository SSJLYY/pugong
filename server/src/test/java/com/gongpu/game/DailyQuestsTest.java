package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import com.gongpu.state.StateCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第七批第 31 轮：每日任务模型——签发 3 个不重复、基线快照、同日不重签、计数口径与编解码。 */
class DailyQuestsTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void ensureIssuesThreeDistinctTasksWithBaselines() {
        PlayerState s = NewState.create();
        s.stats.lifetimeKills = Num.of(500);
        DailyQuests.ensure(s);
        assertEquals(DailyQuests.today(), s.dailyDay);
        assertEquals(3, s.dailyTasks.size(), "每日应签发 3 个任务");
        Set<String> ids = new HashSet<>();
        for (PlayerState.DailyTask t : s.dailyTasks) {
            assertTrue(ids.add(t.id), "任务模板不应重复：" + t.id);
            assertTrue(DailyQuests.templateOf(t.id) != null, "模板应存在");
            // 基线应为签发时计数：进度从 0 起
            assertEquals(0, DailyQuests.progressOf(s, t), "刚签发进度应为 0");
            assertFalse(t.claimed);
        }
        // 同日再 ensure 不重签（同一批任务对象保留）
        List<PlayerState.DailyTask> before = s.dailyTasks;
        DailyQuests.ensure(s);
        assertTrue(before == s.dailyTasks, "同日不应重新签发");
    }

    @Test
    void countersMapToLifetimeStats() {
        PlayerState s = NewState.create();
        s.stats.lifetimeKills = Num.of(11);
        s.stats.bossKillCount = 2;
        s.stats.crafts = 3;
        s.stats.dismantles = 4;
        s.stats.potionsUsed = 5;
        s.stats.lifetimePunches = Num.of(66);
        s.stats.totalDamage = Num.of(777);
        assertEquals(0, DailyQuests.counterOf(s, "hunt").compareTo(Num.of(11)));
        assertEquals(0, DailyQuests.counterOf(s, "boss").compareTo(Num.of(2)));
        assertEquals(0, DailyQuests.counterOf(s, "craft").compareTo(Num.of(3)));
        assertEquals(0, DailyQuests.counterOf(s, "dismantle").compareTo(Num.of(4)));
        assertEquals(0, DailyQuests.counterOf(s, "potion").compareTo(Num.of(5)));
        assertEquals(0, DailyQuests.counterOf(s, "punch").compareTo(Num.of(66)));
        assertEquals(0, DailyQuests.counterOf(s, "damage").compareTo(Num.of(777)));
    }

    @Test
    void progressTracksDeltaAndCaps() {
        PlayerState s = NewState.create();
        s.stats.lifetimeKills = Num.of(100);
        PlayerState.DailyTask t = new PlayerState.DailyTask();
        t.id = "hunt";
        t.baseline = "100";
        t.target = 30;
        s.stats.lifetimeKills = Num.of(115);
        assertEquals(15, DailyQuests.progressOf(s, t));
        assertFalse(DailyQuests.done(s, t));
        s.stats.lifetimeKills = Num.of(999);
        assertEquals(30, DailyQuests.progressOf(s, t), "展示进度应封顶到 target");
        assertTrue(DailyQuests.done(s, t));
    }

    @Test
    void codecRoundTripKeepsDailyState() {
        PlayerState s = NewState.create();
        DailyQuests.ensure(s);
        s.dailyTasks.get(0).claimed = true;
        PlayerState back = StateCodec.decode(StateCodec.encode(s));
        assertEquals(s.dailyDay, back.dailyDay);
        assertEquals(3, back.dailyTasks.size());
        assertEquals(s.dailyTasks.get(0).id, back.dailyTasks.get(0).id);
        assertEquals(s.dailyTasks.get(0).baseline, back.dailyTasks.get(0).baseline);
        assertTrue(back.dailyTasks.get(0).claimed, "领取标记应随存档保留");
    }

    @Test
    void usePotionIncrementsLifetimeCounter() {
        GameService game = new GameService();
        PlayerState s = NewState.create();
        String code = GameConst.POTIONS.keySet().iterator().next();
        s.potions.put(code, 2);
        long before = s.stats.potionsUsed;
        game.usePotion(s, code);
        assertEquals(before + 1, s.stats.potionsUsed, "喝药应计入终身用药次数");
        assertTrue(s.usedPotions.contains(code));
    }
}
