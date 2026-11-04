package com.gongpu.game;

import com.gongpu.common.ApiException;
import com.gongpu.common.Num;
import com.gongpu.state.PlayerState;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 月常任务（第九批）：每月 1 日自动签发 3 个超长线任务，与每日/周常同为
 * 「终身计数 − 签发基线」口径（转生不清、跨月重签）。目标与奖励约为周常的四倍量级，
 * 三个全清额外 +300,000 金与随机宝石 ×3（GameService.claimMonthly 叠加发放）。
 */
public final class MonthlyQuests {

    private MonthlyQuests() {
    }

    public record Template(String id, String name, String emoji, String action, String unit,
                           long[] targets, long[] golds) {
    }

    public static final List<Template> TEMPLATES = List.of(
            new Template("hunt", "月猎令", "⚔️", "击杀怪物", "只",
                    new long[]{3000, 8000, 15000}, new long[]{120000, 250000, 400000}),
            new Template("boss", "月伐令", "👑", "击败 Boss", "次",
                    new long[]{50, 100, 180}, new long[]{150000, 280000, 450000}),
            new Template("craft", "月锻令", "⚒️", "合成装备", "件",
                    new long[]{80, 160, 300}, new long[]{120000, 240000, 380000}),
            new Template("punch", "月拳令", "👊", "打出普攻", "拳",
                    new long[]{500000, 1500000, 4000000}, new long[]{120000, 250000, 420000}),
            new Template("damage", "月山令", "💥", "造成伤害", "点",
                    new long[]{1_000_000_000L, 10_000_000_000L, 100_000_000_000L},
                    new long[]{150000, 300000, 500000}),
            new Template("enchant", "月铭令", "🖋️", "附魔装备", "次",
                    new long[]{100, 250, 500}, new long[]{120000, 240000, 380000}),
            new Template("rebirth", "月轮回令", "♻️", "转生轮回", "世",
                    new long[]{1, 2, 3}, new long[]{150000, 300000, 500000}));

    /** 每月全清奖励：金币部分（另加随机宝石 ×3，见 GameService.claimMonthly）。 */
    public static final long ALL_CLEAR_BONUS = 300_000;
    public static final int ALL_CLEAR_GEMS = 3;

    public static Template templateOf(String id) {
        for (Template t : TEMPLATES) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }

    /** 本月键：形如 2026-11（Asia/Shanghai）。 */
    public static String thisMonth() {
        return YearMonth.now(GameConst.DAILY_ZONE).toString();
    }

    public static void ensure(PlayerState s) {
        String month = thisMonth();
        if (month.equals(s.monthlyMonth) && s.monthlyTasks != null && !s.monthlyTasks.isEmpty()) {
            return;
        }
        Random rnd = new Random(month.hashCode() * 31L + (s.stats == null ? 0 : s.stats.born));
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
        s.monthlyTasks = tasks;
        s.monthlyMonth = month;
        s.monthlyBonusGiven = false;
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
        for (PlayerState.DailyTask t : s.monthlyTasks) {
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
        for (PlayerState.DailyTask t : s.monthlyTasks) {
            if (t.id.equals(taskId)) {
                found = t;
                break;
            }
        }
        if (found == null) {
            throw ApiException.badRequest("月常任务不存在：" + taskId);
        }
        if (found.claimed) {
            throw ApiException.badRequest("该任务奖励已领取");
        }
        if (!done(s, found)) {
            throw ApiException.badRequest("任务尚未完成（" + progressOf(s, found) + "/" + found.target + "）");
        }
        found.claimed = true;
        s.stats.monthlyClaims++;
        s.gold = Num.add(s.gold, Num.of(found.rewardGold));
        return found.rewardGold;
    }
}
