package com.gongpu.game;

import com.gongpu.common.ApiException;
import com.gongpu.common.Num;
import com.gongpu.state.PlayerState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 每日任务（第七批）：每天自动签发 3 个，从 7 个模板里抽（击杀/Boss/合成/分解/喝药/出拳/伤害）。
 * 进度 = 终身计数 − 签发基线：计数全是终身口径，转生不清、跨日重签；完成逐个领金币。
 * 与公会委托的区别：委托要接取且按区域计杀，每日任务自动签发、全球计进度。
 */
public final class DailyQuests {

    private DailyQuests() {
    }

    /** 模板：targets 与 golds 平行三档，签发时随机抽一档。 */
    public record Template(String id, String name, String emoji, String action, String unit,
                           long[] targets, long[] golds) {
    }

    public static final List<Template> TEMPLATES = List.of(
            new Template("hunt", "屠猎令", "⚔️", "击杀怪物", "只",
                    new long[]{30, 60, 100}, new long[]{1800, 3600, 6000}),
            new Template("boss", "讨伐令", "👑", "击败 Boss", "次",
                    new long[]{1, 2, 3}, new long[]{2500, 5000, 7500}),
            new Template("craft", "锻造令", "⚒️", "合成装备", "件",
                    new long[]{1, 2, 3}, new long[]{1500, 3000, 4500}),
            new Template("dismantle", "回收令", "♻️", "分解装备", "件",
                    new long[]{3, 6, 10}, new long[]{1200, 2400, 4000}),
            new Template("potion", "品药令", "🧪", "使用药剂", "瓶",
                    new long[]{3, 5, 8}, new long[]{900, 1500, 2400}),
            new Template("punch", "出拳令", "👊", "打出普攻", "拳",
                    new long[]{1000, 3000, 6000}, new long[]{1000, 3000, 6000}),
            new Template("damage", "摧山令", "💥", "造成伤害", "点",
                    new long[]{1_000_000, 10_000_000, 100_000_000}, new long[]{2000, 4000, 8000}),
            new Template("feed", "喂宠令", "🐾", "喂食宠物", "次",
                    new long[]{5, 10, 20}, new long[]{900, 1500, 2400}),
            new Template("fuse", "融晶令", "💎", "融合宝石", "次",
                    new long[]{1, 2, 3}, new long[]{1200, 2400, 4000}));

    /** 每日全清奖励：3 个任务全领后额外发放（每日一次）。 */
    public static final long ALL_CLEAR_BONUS = 5000;

    public static Template templateOf(String id) {
        for (Template t : TEMPLATES) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }

    /** 模板对应的终身计数（全部终身口径：转生不清零，基线法才成立）。 */
    public static BigDecimal counterOf(PlayerState s, String templateId) {
        return switch (templateId) {
            case "hunt" -> s.stats.lifetimeKills == null ? BigDecimal.ZERO : s.stats.lifetimeKills;
            case "boss" -> BigDecimal.valueOf(s.stats.bossKillCount);
            case "craft" -> BigDecimal.valueOf(s.stats.crafts);
            case "dismantle" -> BigDecimal.valueOf(s.stats.dismantles);
            case "potion" -> BigDecimal.valueOf(s.stats.potionsUsed);
            case "punch" -> s.stats.lifetimePunches == null ? BigDecimal.ZERO : s.stats.lifetimePunches;
            case "damage" -> s.stats.totalDamage == null ? BigDecimal.ZERO : s.stats.totalDamage;
            case "enchant" -> BigDecimal.valueOf(s.stats.enchants);
            case "rebirth" -> BigDecimal.valueOf(s.gen);
            case "feed" -> BigDecimal.valueOf(s.stats.petFeeds);
            case "fuse" -> BigDecimal.valueOf(s.stats.gemsFused);
            default -> BigDecimal.ZERO;
        };
    }

    public static String today() {
        return LocalDate.now(GameConst.DAILY_ZONE).toString();
    }

    /**
     * 确保当日任务已签发：日期变了（或从未签发）就重签 3 个。
     * 随机种子 = 日期 + 存档出生时刻：同日同档稳定（列表落盘后不再重掷，仅作生成多样性）。
     */
    public static void ensure(PlayerState s) {
        String today = today();
        if (today.equals(s.dailyDay) && s.dailyTasks != null && !s.dailyTasks.isEmpty()) {
            return;
        }
        Random rnd = new Random(today.hashCode() * 31L + (s.stats == null ? 0 : s.stats.born));
        List<Template> pool = new ArrayList<>(TEMPLATES);
        Collections.shuffle(pool, rnd);
        List<PlayerState.DailyTask> tasks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Template t = pool.get(i);
            int tier = rnd.nextInt(t.targets().length);
            PlayerState.DailyTask dt = new PlayerState.DailyTask();
            dt.id = t.id();
            dt.baseline = Num.plain(counterOf(s, t.id()));
            dt.target = t.targets()[tier];
            dt.rewardGold = t.golds()[tier];
            dt.claimed = false;
            tasks.add(dt);
        }
        s.dailyTasks = tasks;
        s.dailyDay = today;
        s.dailyBonusGiven = false;
    }

    /** 当前进度（增量，已封顶到 target 供展示；判定完成用 >= target）。 */
    public static long progressOf(PlayerState s, PlayerState.DailyTask t) {
        BigDecimal delta = Num.sub(counterOf(s, t.id), new BigDecimal(t.baseline == null ? "0" : t.baseline));
        if (Num.lte(delta, BigDecimal.ZERO)) {
            return 0;
        }
        return delta.min(BigDecimal.valueOf(t.target)).longValue();
    }

    public static boolean done(PlayerState s, PlayerState.DailyTask t) {
        return progressOf(s, t) >= t.target;
    }

    /** 前端视图（先 ensure 再组装）。 */
    public static List<Map<String, Object>> viewOf(PlayerState s) {
        ensure(s);
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlayerState.DailyTask t : s.dailyTasks) {
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

    /** 领取单个任务奖励；返回实发金币。全清奖励在 GameService 侧叠加（第 36 轮）。 */
    public static long claim(PlayerState s, String taskId) {
        ensure(s);
        PlayerState.DailyTask found = null;
        for (PlayerState.DailyTask t : s.dailyTasks) {
            if (t.id.equals(taskId)) {
                found = t;
                break;
            }
        }
        if (found == null) {
            throw ApiException.badRequest("每日任务不存在：" + taskId);
        }
        if (found.claimed) {
            throw ApiException.badRequest("该任务奖励已领取");
        }
        if (!done(s, found)) {
            throw ApiException.badRequest("任务尚未完成（" + progressOf(s, found) + "/" + found.target + "）");
        }
        found.claimed = true;
        s.stats.dailyClaims++;
        s.gold = Num.add(s.gold, Num.of(found.rewardGold));
        return found.rewardGold;
    }
}
