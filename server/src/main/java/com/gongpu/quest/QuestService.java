package com.gongpu.quest;

import com.gongpu.cfg.QuestTemplate;
import com.gongpu.cfg.QuestTemplateRepository;
import com.gongpu.common.ApiException;
import com.gongpu.common.Num;
import com.gongpu.game.GameConst;
import com.gongpu.game.PanelCalculator;
import com.gongpu.state.PlayerState;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 委托系统（DESIGN.md §5）：冒险公会——属性门槛接取，击杀交付。
 * <p>进度模型：接取时快照当前 kills 为基线（activeQuests 存 BigDecimal 基线），
 * 交付时用 kills - 基线 与 targetKills 比较——与结算引擎解耦，无需在引擎里逐击埋点。</p>
 */
@Service
public class QuestService {

    private final QuestTemplateRepository quests;

    public QuestService(QuestTemplateRepository quests) {
        this.quests = quests;
    }

    /** 委托列表（含接取资格与进度视图，前端渲染用）。 */
    public Map<String, Object> questBoard(PlayerState s) {
        PanelCalculator.Panel p = PanelCalculator.of(s);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (QuestTemplate q : quests.findByEnabledTrueOrderBySortNoAscIdAsc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", q.getCode());
            m.put("name", q.getName());
            m.put("emoji", q.getEmoji() == null ? "📜" : q.getEmoji());
            m.put("desc", q.getDesc());
            m.put("targetZone", q.getTargetZone());
            m.put("zoneName", zoneName(q.getTargetZone()));
            m.put("targetKills", q.getTargetKills());
            m.put("rewardGold", q.getRewardGold());
            m.put("rewardItem", q.getRewardItem());
            m.put("rewardItemCount", q.getRewardItemCount());
            m.put("repeatable", q.getRepeatable());
            // 接取资格：属性门槛 + 区域门槛 + 非重复委托未完成过
            String reject = checkReq(s, p, q);
            m.put("eligible", reject == null);
            m.put("rejectReason", reject);
            // 进度
            BigDecimal base = s.activeQuests.get(q.getCode());
            if (base != null) {
                BigDecimal kills = Num.sub(s.stats.kills, base);
                int done = kills.setScale(0, java.math.RoundingMode.DOWN)
                        .min(BigDecimal.valueOf(q.getTargetKills())).intValue();
                m.put("active", true);
                m.put("progress", Math.max(0, done));
                m.put("ready", Num.gte(kills, Num.of(q.getTargetKills())));
            } else {
                m.put("active", false);
                m.put("progress", 0);
                m.put("ready", false);
            }
            m.put("done", s.doneQuests.contains(q.getCode()));
            rows.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        return out;
    }

    private String checkReq(PlayerState s, PanelCalculator.Panel p, QuestTemplate q) {
        // 非重复委托已完成 → 不可再接
        if (!q.getRepeatable() && s.doneQuests.contains(q.getCode())) {
            return "已完成，不可重复";
        }
        // 已在进行的委托不可重复接取
        if (s.activeQuests.containsKey(q.getCode())) {
            return "进行中";
        }
        // 属性门槛（复用区域 req 校验语义）
        Map<String, Object> req = parseJson(q.getReqJson());
        for (Map.Entry<String, Object> e : req.entrySet()) {
            BigDecimal need = Num.parse(String.valueOf(e.getValue()), Num.ZERO);
            BigDecimal have = switch (e.getKey()) {
                case "maxHP" -> p.maxHP();
                case "PDEF" -> p.PDEF();
                case "MDEF" -> p.MDEF();
                case "REGEN" -> p.REGEN();
                default -> null;
            };
            if (have != null && Num.lt(have, need)) {
                return "属性不足：" + GameConst.BLESS_NAME.getOrDefault(e.getKey(), e.getKey())
                        + " ≥ " + Num.cn(need);
            }
        }
        return null;
    }

    /** 接取委托。 */
    public Map<String, Object> accept(PlayerState s, String code) {
        QuestTemplate q = quests.findByCode(code)
                .orElseThrow(() -> ApiException.notFound("委托不存在"));
        if (!q.getEnabled()) {
            throw ApiException.badRequest("委托已下架");
        }
        String reject = checkReq(s, PanelCalculator.of(s), q);
        if (reject != null) {
            throw ApiException.badRequest(reject);
        }
        if (s.activeQuests.size() >= 3) {
            throw ApiException.badRequest("最多同时进行 3 个委托");
        }
        s.activeQuests.put(code, s.stats.kills);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("code", code);
        m.put("note", "击杀任意怪物 " + q.getTargetKills() + " 只后回来交付");
        return m;
    }

    /** 交付委托：进度达标 → 发奖。 */
    public Map<String, Object> deliver(PlayerState s, String code) {
        QuestTemplate q = quests.findByCode(code)
                .orElseThrow(() -> ApiException.notFound("委托不存在"));
        BigDecimal base = s.activeQuests.get(code);
        if (base == null) {
            throw ApiException.badRequest("该委托不在进行中");
        }
        // 防御：若 kills 因任何路径回退(死亡重开/转生已清空 activeQuests,此处兜底其余异常),
        // 基线钳到当前 kills,避免进度变巨负数永久卡死
        if (Num.gt(base, s.stats.kills)) {
            base = s.stats.kills;
        }
        BigDecimal kills = Num.sub(s.stats.kills, base);
        if (Num.lt(kills, Num.of(q.getTargetKills()))) {
            int done = kills.setScale(0, java.math.RoundingMode.DOWN).intValue();
            done = Math.max(0, done);
            throw ApiException.badRequest("进度不足：" + done + "/" + q.getTargetKills()
                    + "，继续杀怪去吧");
        }
        // 发奖
        s.activeQuests.remove(code);
        BigDecimal gold = Num.of(q.getRewardGold());
        s.gold = Num.add(s.gold, gold);
        Map<String, Object> reward = new LinkedHashMap<>();
        reward.put("gold", Num.plain(gold));
        if (q.getRewardItem() != null && q.getRewardItemCount() != null && q.getRewardItemCount() > 0) {
            s.potions.merge(q.getRewardItem(), q.getRewardItemCount(), Integer::sum);
            reward.put("item", q.getRewardItem());
            reward.put("count", q.getRewardItemCount());
        }
        // 完成数统一记录(重复委托也记,统计用;可重复接取由 repeatable 控制)
        s.doneQuests.add(code);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("code", code);
        m.put("reward", reward);
        return m;
    }

    /** 放弃委托。 */
    public Map<String, Object> abandon(PlayerState s, String code) {
        if (s.activeQuests.remove(code) == null) {
            throw ApiException.badRequest("该委托不在进行中");
        }
        return Map.of("ok", true);
    }

    private static String zoneName(String zone) {
        GameConst.ZoneDef z = GameConst.ZONES.get(zone);
        return z != null ? z.name() : zone;
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();
    /** reqJson 解析缓存：委托板每次打开对每个模板都解析一遍，配置不变时结果不变（按原文键缓存）。 */
    private static final java.util.concurrent.ConcurrentHashMap<String, Map<String, Object>> REQ_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    static Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return REQ_CACHE.computeIfAbsent(json, j -> {
            try {
                Map<String, Object> m = JSON.readValue(j,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                return m == null ? Map.of() : m;
            } catch (Exception e) {
                return Map.of();
            }
        });
    }
}
