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
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    // ---- 第 32 轮：领取规则与跨日重签 ----

    @Test
    void claimRequiresDoneAndPaysOnce() {
        GameService game = new GameService();
        PlayerState s = NewState.create();
        PlayerState.DailyTask t = new PlayerState.DailyTask();
        t.id = "hunt";
        t.baseline = "0";
        t.target = 30;
        t.rewardGold = 1800;
        s.dailyDay = DailyQuests.today();
        // 另挂两个未完成任务：否则单任务列表领完即触发「全清奖励」，金币断言会混入 +5000
        PlayerState.DailyTask filler1 = new PlayerState.DailyTask();
        filler1.id = "boss";
        filler1.baseline = "0";
        filler1.target = 999;
        filler1.rewardGold = 1;
        PlayerState.DailyTask filler2 = new PlayerState.DailyTask();
        filler2.id = "craft";
        filler2.baseline = "0";
        filler2.target = 999;
        filler2.rewardGold = 1;
        s.dailyTasks = new java.util.ArrayList<>(List.of(t, filler1, filler2));
        assertThrows(com.gongpu.common.ApiException.class, () -> game.claimDaily(s, "hunt"),
                "未完成应拒绝领取");
        s.stats.lifetimeKills = Num.of(30);
        java.util.Map<String, Object> r = game.claimDaily(s, "hunt");
        assertEquals(1800L, r.get("reward"));
        assertEquals(0, s.gold.compareTo(Num.of(1800)), "奖励金币应入账");
        assertEquals(1, s.stats.dailyClaims);
        assertThrows(com.gongpu.common.ApiException.class, () -> game.claimDaily(s, "hunt"),
                "重复领取应拒绝");
        assertThrows(com.gongpu.common.ApiException.class, () -> game.claimDaily(s, "nope"),
                "未知任务应拒绝");
    }

    @Test
    void crossDayReissuesFreshTasks() {
        PlayerState s = NewState.create();
        DailyQuests.ensure(s);
        s.dailyTasks.get(0).claimed = true;
        s.dailyDay = "2000-01-01";   // 模拟跨日
        DailyQuests.ensure(s);
        assertEquals(DailyQuests.today(), s.dailyDay, "跨日应重签");
        assertEquals(3, s.dailyTasks.size());
        assertTrue(s.dailyTasks.stream().noneMatch(t -> t.claimed), "新日任务应全部未领取");
        assertFalse(s.dailyBonusGiven, "全清奖励标记应随新日重置");
    }

    // ---- 第 34 轮：模板池完整性 + 大数伤害基线 + 视图形状 ----

    @Test
    void templatePoolIntegrity() {
        assertEquals(11, DailyQuests.TEMPLATES.size(), "模板池应为 9 个");
        Set<String> ids = new HashSet<>();
        for (DailyQuests.Template t : DailyQuests.TEMPLATES) {
            assertTrue(ids.add(t.id()), "模板 id 不应重复");
            assertEquals(3, t.targets().length, "每模板三档目标");
            assertEquals(3, t.golds().length, "每模板三档奖励");
            assertTrue(t.targets()[0] < t.targets()[1] && t.targets()[1] < t.targets()[2],
                    "目标应递增：" + t.id());
            for (long g : t.golds()) {
                assertTrue(g > 0, "奖励应为正：" + t.id());
            }
        }
    }

    @Test
    void damageBaselineHandlesHugeNumbers() {
        PlayerState s = NewState.create();
        String base = "123456789012345678901234567890";
        s.stats.totalDamage = new java.math.BigDecimal(base);
        PlayerState.DailyTask t = new PlayerState.DailyTask();
        t.id = "damage";
        t.baseline = base;
        t.target = 1_000_000;
        assertEquals(0, DailyQuests.progressOf(s, t));
        s.stats.totalDamage = new java.math.BigDecimal(base).add(new java.math.BigDecimal("1000000"));
        assertEquals(1_000_000, DailyQuests.progressOf(s, t), "大数基线作差应精确到个位");
        assertTrue(DailyQuests.done(s, t));
    }

    @Test
    void viewShapeIsComplete() {
        PlayerState s = NewState.create();
        java.util.List<java.util.Map<String, Object>> view = DailyQuests.viewOf(s);
        assertEquals(3, view.size());
        for (java.util.Map<String, Object> m : view) {
            for (String key : new String[]{"id", "name", "emoji", "desc", "progress", "target",
                    "rewardGold", "claimed", "done"}) {
                assertTrue(m.containsKey(key), "视图应含字段 " + key);
            }
            assertTrue(String.valueOf(m.get("desc")).contains(String.valueOf(m.get("target"))),
                    "描述应写明目标数量");
        }
    }

    /** 第 35 轮：转生不吞每日进度（计数终身口径 + resetForNewLife 不动每日字段）。 */
    @Test
    void rebirthKeepsDailyProgress() {
        PlayerState s = NewState.create();
        PlayerState.DailyTask t = new PlayerState.DailyTask();
        t.id = "hunt";
        t.baseline = "10";
        t.target = 30;
        t.rewardGold = 1800;
        s.dailyDay = DailyQuests.today();
        s.dailyTasks = new java.util.ArrayList<>(List.of(t));
        s.stats.lifetimeKills = Num.of(25);
        assertEquals(15, DailyQuests.progressOf(s, t));
        com.gongpu.state.SaveService.resetForNewLife(s);
        assertEquals(DailyQuests.today(), s.dailyDay, "转生不应换掉当日任务");
        assertEquals(1, s.dailyTasks.size(), "转生不应清空任务列表");
        assertEquals(15, DailyQuests.progressOf(s, s.dailyTasks.get(0)),
                "终身计数不随转生重置，每日进度应原样保留");
    }

    /** 第 36 轮：全清奖励——第三个任务领取时额外 +5000，每日只发一次。 */
    @Test
    void allClearBonusPaysOnce() {
        GameService game = new GameService();
        PlayerState s = NewState.create();
        s.dailyDay = DailyQuests.today();
        java.util.List<PlayerState.DailyTask> tasks = new java.util.ArrayList<>();
        for (String id : new String[]{"hunt", "boss", "craft"}) {
            PlayerState.DailyTask t = new PlayerState.DailyTask();
            t.id = id;
            t.baseline = "0";
            t.target = 1;
            t.rewardGold = 100;
            tasks.add(t);
        }
        s.dailyTasks = tasks;
        s.stats.lifetimeKills = Num.of(5);
        s.stats.bossKillCount = 5;
        s.stats.crafts = 5;
        assertEquals(0L, game.claimDaily(s, "hunt").get("allClearBonus"), "未全清不应发全清奖励");
        assertEquals(0L, game.claimDaily(s, "boss").get("allClearBonus"));
        java.util.Map<String, Object> third = game.claimDaily(s, "craft");
        assertEquals(DailyQuests.ALL_CLEAR_BONUS, third.get("allClearBonus"), "全清应发额外奖励");
        assertTrue(s.dailyBonusGiven);
        assertEquals(0, s.gold.compareTo(Num.of(300 + DailyQuests.ALL_CLEAR_BONUS)),
                "总入账 = 三任务奖励 + 全清奖励");
    }

    @Test
    void usePotionIncrementsLifetimeCounter() {
        GameService game = new GameService();
        PlayerState s = NewState.create();
        // 用确定性的普通药剂（Map.ofEntries 迭代序不稳定，首项可能是 pet_treat 这类需前置条件的特殊道具）
        String code = "ironpot";
        s.potions.put(code, 2);
        long before = s.stats.potionsUsed;
        game.usePotion(s, code);
        assertEquals(before + 1, s.stats.potionsUsed, "喝药应计入终身用药次数");
        assertTrue(s.usedPotions.contains(code));
    }
}
