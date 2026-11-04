package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.ApiException;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第八批第 31–34 轮：周常任务——签发（ISO 周键/3 个/基线口径）、进度与领取、
 * 全清奖励（5 万金 + 宝石 ×2）、视图挂 fullView（防第七批「误挂管家回执」复发）。
 */
class WeeklyQuestsTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void ensureIssuesThreeTasksWithBaselines() {
        PlayerState s = NewState.create();
        s.stats.lifetimeKills = Num.of(12345);
        WeeklyQuests.ensure(s);
        assertEquals(3, s.weeklyTasks.size());
        assertEquals(WeeklyQuests.thisWeek(), s.weeklyWeek);
        assertTrue(s.weeklyWeek.matches("\\d{4}-W\\d{2}"), "周键应为 ISO 格式：" + s.weeklyWeek);
        // 同周重复 ensure 不重签
        List<PlayerState.DailyTask> first = s.weeklyTasks;
        WeeklyQuests.ensure(s);
        assertTrue(first == s.weeklyTasks || first.equals(s.weeklyTasks), "同周不应重签");
    }

    @Test
    void progressAndClaimFlow() {
        PlayerState s = NewState.create();
        WeeklyQuests.ensure(s);
        PlayerState.DailyTask hunt = s.weeklyTasks.stream()
                .filter(t -> t.id.equals("hunt")).findFirst().orElse(null);
        if (hunt == null) {
            // 本周没抽到 hunt：直接构造一个同口径任务验证领取链
            hunt = new PlayerState.DailyTask();
            hunt.id = "hunt";
            hunt.baseline = "0";
            hunt.target = 10;
            hunt.rewardGold = 20000;
            s.weeklyTasks.set(0, hunt);
        }
        hunt.target = 10;
        s.stats.lifetimeKills = Num.of(9);
        assertThrows(ApiException.class, () -> WeeklyQuests.claim(s, "hunt"), "未完成应拒绝");
        s.stats.lifetimeKills = Num.of(10);
        long goldBefore = s.gold.longValue();
        long got = WeeklyQuests.claim(s, "hunt");
        assertEquals(hunt.rewardGold, got);
        assertEquals(goldBefore + got, s.gold.longValue());
        assertEquals(1, s.stats.weeklyClaims);
        assertThrows(ApiException.class, () -> WeeklyQuests.claim(s, "hunt"), "重复领取应拒绝");
    }

    @Test
    void allClearGrantsBonusAndGems() {
        PlayerState s = NewState.create();
        GameService g = new GameService();
        WeeklyQuests.ensure(s);
        for (PlayerState.DailyTask t : s.weeklyTasks) {
            t.target = 0; // 直接置完成口径：progressOf 封顶后 done 判定 >= 0 恒真？
        }
        // target=0 时 progressOf 返回 0 >= 0 为真——逐个领完应触发全清
        long gemsBefore = s.materials.entrySet().stream()
                .filter(e -> e.getKey().startsWith("gem_") && !e.getKey().endsWith("_2"))
                .mapToInt(e -> e.getValue()).sum();
        Map<String, Object> last = null;
        for (PlayerState.DailyTask t : s.weeklyTasks) {
            last = g.claimWeekly(s, t.id);
        }
        assertEquals(WeeklyQuests.ALL_CLEAR_BONUS, ((Number) last.get("allClearBonus")).longValue(),
                "全清应发 5 万金奖励");
        @SuppressWarnings("unchecked")
        List<String> gems = (List<String>) last.get("allClearGems");
        assertEquals(WeeklyQuests.ALL_CLEAR_GEMS, gems.size(), "全清应发 2 颗宝石");
        long gemsAfter = s.materials.entrySet().stream()
                .filter(e -> e.getKey().startsWith("gem_") && !e.getKey().endsWith("_2"))
                .mapToInt(e -> e.getValue()).sum();
        assertEquals(gemsBefore + 2, gemsAfter);
        assertTrue(s.weeklyBonusGiven);
    }

    @Test
    void viewHangsOnFullView() throws Exception {
        // 源码序锁（同 ControllerOrderTest 教训）：fullView 必须 put weeklyTasks，
        // 且不能只出现在 setAutoLines 回执里
        String src = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                "src/main/java/com/gongpu/game/GameService.java")), java.nio.charset.StandardCharsets.UTF_8);
        int fullViewAt = src.indexOf("DailyQuests.ensure(s);\n        WeeklyQuests.ensure(s);");
        assertTrue(fullViewAt > 0, "fullView 应同时 ensure 每日与周常");
        assertTrue(src.indexOf("m.put(\"weeklyTasks\"", fullViewAt) > fullViewAt,
                "weeklyTasks 应挂在 fullView 上");
    }
}
