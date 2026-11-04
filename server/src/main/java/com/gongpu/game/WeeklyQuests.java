package com.gongpu.game;

import com.gongpu.common.ApiException;
import com.gongpu.common.Num;
import com.gongpu.state.PlayerState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 周常任务（第八批）：每周一（ISO 周）自动签发 3 个长线任务，与每日任务同为
 * 「终身计数 − 签发基线」口径（转生不清、跨周重签）。目标与奖励约为日任务的十倍量级，
 * 三个全清额外 +50,000 金与随机宝石 ×2（GameService.claimWeekly 叠加发放）。
 */
public final class WeeklyQuests {

    private WeeklyQuests() {
    }

    public record Template(String id, String name, String emoji, String action, String unit,
                           long[] targets, long[] golds) {
    }

    public static final List<Template> TEMPLATES = List.of(
            new Template("hunt", "周猎令", "⚔️", "击杀怪物", "只",
                    new long[]{500, 1000, 2000}, new long[]{20000, 40000, 70000}),
            new Template("boss", "周伐令", "👑", "击败 Boss", "次",
                    new long[]{10, 20, 35}, new long[]{25000, 50000, 80000}),
            new Template("craft", "周锻令", "⚒️", "合成装备", "件",
                    new long[]{15, 30, 50}, new long[]{20000, 40000, 60000}),
            new Template("punch", "周拳令", "👊", "打出普攻", "拳",
                    new long[]{50000, 150000, 300000}, new long[]{20000, 40000, 70000}),
            new Template("damage", "周山令", "💥", "造成伤害", "点",
                    new long[]{100_000_000, 1_000_000_000, 10_000_000_000L},
                    new long[]{25000, 50000, 90000}),
            new Template("star", "周星令", "⭐", "装备升星", "次",
                    new long[]{3, 8, 15}, new long[]{20000, 40000, 70000}),
            new Template("treat", "周零令", "🍬", "喂宠物零食", "次",
                    new long[]{5, 12, 25}, new long[]{20000, 40000, 70000}));

    /** 每周全清奖励：金币部分（另加随机宝石 ×2，见 GameService.claimWeekly）。 */
    public static final long ALL_CLEAR_BONUS = 50_000;
    public static final int ALL_CLEAR_GEMS = 2;

    public static Template templateOf(String id) {
        for (Template t : TEMPLATES) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }

    /** 本周键：ISO 周（周一起始），形如 2026-W45。 */
    public static String thisWeek() {
        LocalDate d = LocalDate.now(GameConst.DAILY_ZONE);
        int week = d.get(WeekFields.ISO.weekOfWeekBasedYear());
        int year = d.get(WeekFields.ISO.weekBasedYear());
        return year + "-W" + (week < 10 ? "0" : "") + week;
    }

    public static void ensure(PlayerState s) {
        String week = thisWeek();
        if (week.equals(s.weeklyWeek) && s.weeklyTasks != null && !s.weeklyTasks.isEmpty()) {
            return;
        }
        Random rnd = new Random(week.hashCode() * 31L + (s.stats == null ? 0 : s.stats.born));
        List<Template> pool = new ArrayList<>(TEMPLATES);
        Collections.shuffle(pool, rnd);
        List<PlayerState.DailyTask> tasks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Template t = pool.get(i);
            int tier = rnd.nextInt(t.targets().length);
            PlayerState.DailyTask dt = new PlayerState.DailyTask();
            dt.id = t.id();
            dt.baseline = Num.plain(DailyQuests.counterOf(s, t.id()));
            dt.target = t.targets()[tier];
            dt.rewardGold = t.golds()[tier];
            dt.claimed = false;
            tasks.add(dt);
        }
        s.weeklyTasks = tasks;
        s.weeklyWeek = week;
        s.weeklyBonusGiven = false;
    }

    public static long progressOf(PlayerState s, PlayerState.DailyTask t) {
        BigDecimal delta = Num.sub(DailyQuests.counterOf(s, t.id),
                new BigDecimal(t.baseline == null ? "0" : t.baseline));
        if (Num.lte(delta, BigDecimal.ZERO)) {
            return 0;
        }
        return delta.min(BigDecimal.valueOf(t.target)).longValue();
    }

    public static boolean done(PlayerState s, PlayerState.DailyTask t) {
        return progressOf(s, t) >= t.target;
    }

    public static List<Map<String, Object>> viewOf(PlayerState s) {
        ensure(s);
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlayerState.DailyTask t : s.weeklyTasks) {
            Template tpl = templateOf(t.id);
            if (tpl == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.id);
            m.put("name", tpl.name());
            m.put("emoji", tpl.emoji());
            m.put("desc", tpl.action() + " " + t.target + " " + tpl.unit());
            m.put("progress", progressOf(s, t));
            m.put("target", t.target);
            m.put("rewardGold", t.rewardGold);
            m.put("claimed", t.claimed);
            m.put("done", done(s, t));
            out.add(m);
        }
        return out;
    }

    public static long claim(PlayerState s, String taskId) {
        ensure(s);
        PlayerState.DailyTask found = null;
        for (PlayerState.DailyTask t : s.weeklyTasks) {
            if (t.id.equals(taskId)) {
                found = t;
                break;
            }
        }
        if (found == null) {
            throw ApiException.badRequest("周常任务不存在：" + taskId);
        }
        if (found.claimed) {
            throw ApiException.badRequest("该任务奖励已领取");
        }
        if (!done(s, found)) {
            throw ApiException.badRequest("任务尚未完成（" + progressOf(s, found) + "/" + found.target + "）");
        }
        found.claimed = true;
        s.stats.weeklyClaims++;
        s.gold = Num.add(s.gold, Num.of(found.rewardGold));
        return found.rewardGold;
    }
}
