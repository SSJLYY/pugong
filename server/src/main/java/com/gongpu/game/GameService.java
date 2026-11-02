package com.gongpu.game;

import com.gongpu.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.gongpu.common.Num;
import com.gongpu.state.PlayerState;
import com.gongpu.state.StateCodec;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 游戏动作服务：换区 / 施法 / 嗑药 / 装备 / 强化 / 转生 / 逃跑。
 * <p>所有动作都在服务端校验前置条件（区域门槛、蓝量、冷却、金币），
 * 客户端提交的只有"意图"，不提交任何数值。</p>
 */
@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    // ==================== 换区 ====================

    public Map<String, Object> gotoZone(PlayerState s, String zoneId) {
        GameConst.ZoneDef z = GameConst.ZONES.get(zoneId);
        if (z == null) {
            throw ApiException.badRequest("区域不存在");
        }
        if (s.dead) {
            throw ApiException.badRequest("你已经死了，无法行动");
        }
        if (z.req() != null) {
            PanelCalculator.Panel p = PanelCalculator.of(s);
            for (Map.Entry<String, String> e : z.req().entrySet()) {
                BigDecimal need = new BigDecimal(e.getValue());
                BigDecimal have = switch (e.getKey()) {
                    case "maxHP" -> p.maxHP();
                    case "PDEF" -> p.PDEF();
                    case "MDEF" -> p.MDEF();
                    case "REGEN" -> p.REGEN();
                    // ATK = 物攻/魔攻取高:物理/附魔/魔攻三条成长路都算数(矿洞物防怪逼你转型的设计意图)
                    case "ATK" -> p.PATK().max(p.MATK());
                    default -> Num.ZERO;
                };
                if (Num.lt(have, need)) {
                    throw ApiException.badRequest("属性不足：需要 "
                            + GameConst.BLESS_NAME.getOrDefault(e.getKey(), e.getKey())
                            + " ≥ " + need.toPlainString() + "（当前 " + Num.cn(have) + "）");
                }
            }
        }
        s.zone = zoneId;
        s.mob = null;
        GameEngine.spawnMob(s, zoneId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("zone", s.zone);
        m.put("mob", mobView(s));
        return m;
    }

    // ==================== 施法 ====================

    public static Map<String, Object> castSkill(PlayerState s, String skillId) {
        GameConst.SkillDef def = com.gongpu.cfg.GameData.instance().skill(skillId);
        if (def == null) {
            throw ApiException.badRequest("技能不存在");
        }
        if (s.dead) {
            throw ApiException.badRequest("你已经死了");
        }
        if (s.cds.getOrDefault(skillId, 0.0) > 0) {
            throw ApiException.badRequest(def.name() + " 冷却中");
        }
        PanelCalculator.Panel p = PanelCalculator.of(s);

        // 前置校验：任何"扣费/进 CD"生效前先判成败，避免校验失败玩家白扣蓝、白进 CD
        if ("roar".equals(skillId) && s.mob != null && s.mob.SPD > 0 && p.SPD().doubleValue() < s.mob.SPD) {
            throw ApiException.badRequest("移速不足，怒吼无效");
        }

        boolean surge = PanelCalculator.active(s, "surge");
        int mpCost = (surge || def.mp() == 0) ? 0 : def.mp();
        if (mpCost > 0 && Num.lt(s.life.curMP, Num.of(mpCost))) {
            throw ApiException.badRequest("蓝量不足（需要 " + mpCost + "）");
        }
        if (mpCost > 0) {
            s.life.curMP = Num.sub(s.life.curMP, Num.of(mpCost));
        }
        // 熟练度：每次施放 +1；每 20 次熟练度档 CD 再 -3%（上限 -15%，20×5=100 后不再涨）
        // 先钳到 100 再取 int，防止极端量级 intValue() 回绕
        java.math.BigDecimal mastery = s.mastery.getOrDefault(skillId, java.math.BigDecimal.ZERO);
        mastery = mastery.add(java.math.BigDecimal.ONE).min(java.math.BigDecimal.valueOf(100));
        s.mastery.put(skillId, mastery);
        int level = mastery.intValue() / 20;
        double masteryCdCut = 1 - level * 0.03;
        double cdMul = ("master".equals(s.talent) ? 0.85 : 1.0) * masteryCdCut;
        s.cds.put(skillId, def.cd() * cdMul);

        switch (skillId) {
            case "burn" -> {
                // 燃烧当前生命 25%，不伤上限
                BigDecimal cost = Num.mul(s.life.curHP, Num.of(GameConst.BURN_HP_COST));
                s.life.curHP = Num.sub(s.life.curHP, cost);
                if (Num.lte(s.life.curHP, Num.ZERO)) {
                    s.dead = true;
                    s.life.curHP = Num.ZERO;
                    s.deathCause = "燃烧生命烧尽了自己";
                }
                s.buffs.put("burn", (double) def.dur());
            }
            case "heavy" -> s.buffs.put("heavy", 30.0);
            case "block" -> s.buffs.put("block", (double) def.dur());
            case "rapid" -> s.buffs.put("rapid", (double) def.dur());
            case "surge" -> s.buffs.put("surge", (double) def.dur());
            case "roar" -> {
                s.mob = null;
                GameEngine.spawnMob(s, s.zone);
            }
            default -> { }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("skill", skillId);
        m.put("cd", s.cds.get(skillId));
        m.put("curHP", Num.plain(s.life.curHP));
        m.put("curMP", Num.plain(s.life.curMP));
        return m;
    }

    // ==================== 药剂 ====================

    public Map<String, Object> usePotion(PlayerState s, String potionId) {
        GameConst.PotionDef def = com.gongpu.cfg.GameData.instance().potion(potionId);
        if (def == null) {
            throw ApiException.badRequest("药剂不存在");
        }
        int have = s.potions.getOrDefault(potionId, 0);
        if (have <= 0) {
            throw ApiException.badRequest("没有" + def.name() + "了");
        }
        s.potions.put(potionId, have - 1);

        PanelCalculator.Panel p = PanelCalculator.of(s);
        if (def.healHP() > 0) {
            BigDecimal heal = Num.mul(p.maxHP(), Num.of(def.healHP()));
            s.life.curHP = Num.min(Num.add(s.life.curHP, heal), p.maxHP());
        }
        if (def.healMP() > 0) {
            BigDecimal heal = Num.mul(p.MP(), Num.of(def.healMP()));
            s.life.curMP = Num.min(Num.add(s.life.curMP, heal), p.MP());
        }
        if ("cure".equals(potionId)) {
            s.buffs.remove("poison");
            s.buffs.remove("burn_dot");
        }
        if (def.buff() != null) {
            s.buffs.put(def.buff(), def.buffDur());
            // 护盾药剂(v3.14):授予 buffVal×生命上限的护盾,先于生命扣除;
            // 到期/重复嗑按新盾值覆盖(tickBuff 里 buff 消失时清盾)
            if ("shield_pot".equals(def.buff())) {
                PanelCalculator.Panel pp = PanelCalculator.of(s);
                s.shield = Num.mul(pp.maxHP(), Num.of(def.buffVal()));
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("left", s.potions.get(potionId));
        m.put("curHP", Num.plain(s.life.curHP));
        m.put("curMP", Num.plain(s.life.curMP));
        return m;
    }

    public static Map<String, Object> buyPotion(PlayerState s, String potionId, int count) {
        GameConst.PotionDef def = com.gongpu.cfg.GameData.instance().potion(potionId);
        if (def == null) {
            throw ApiException.badRequest("药剂不存在");
        }
        if (count < 1 || count > 99) {
            throw ApiException.badRequest("数量不合法");
        }
        // 动态定价（DESIGN.md §9 防刷）：购买次数涨价 +2%/次，封顶 2 倍
        int bought = s.potionBought.getOrDefault(potionId, 0);
        double priceMul = Math.min(2.0, 1.0 + bought * 0.02);
        BigDecimal cost = Num.mul(Num.mul(Num.of(def.price()), Num.of(priceMul)), Num.of((double) count));
        if (Num.lt(s.gold, cost)) {
            throw ApiException.badRequest("金币不足，需要 " + Num.cn(cost));
        }
        s.gold = Num.sub(s.gold, cost);
        s.potions.merge(potionId, count, Integer::sum);
        s.potionBought.merge(potionId, count, Integer::sum);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("gold", Num.plain(s.gold));
        m.put("left", s.potions.get(potionId));
        m.put("priceMul", Math.round(priceMul * 100) / 100.0);
        return m;
    }

    // ==================== 装备 ====================

    /** 动态背包容量：基础 100 + 装备 BAG_CAP 词条（负重腰带 +20）。 */
    public static int bagCap(PlayerState s) {
        int cap = GameConst.BAG_MAX;
        int bonus = 0;
        for (PlayerState.Item it : s.equipped.values()) {
            if (it == null) {
                continue;
            }
            java.math.BigDecimal v = it.base.get("BAG_CAP");
            if (v != null) {
                bonus += v.intValue();
            }
        }
        // 钳制:v3.12 前吞噬把 BAG_CAP 累加进合并腰带,旧档单件即可虚增数千格
        // (实测 bagCap=33720)——容量词条是装备配置,封顶 BAG_MAX(=100)即每件腰带本义 +20
        bonus = Math.min(bonus, GameConst.BAG_MAX);
        return Math.max(GameConst.BAG_MAX, cap + bonus);
    }

    public Map<String, Object> equip(PlayerState s, int bagIdx) {
        if (bagIdx < 0 || bagIdx >= s.equipBag.size()) {
            throw ApiException.badRequest("背包索引越界");
        }
        PlayerState.Item it = s.equipBag.remove(bagIdx);
        String slot = it.slot;
        // 戒指有两个槽：优先填 ring2 若 ring1 已占用
        if ("ring1".equals(slot) && s.equipped.get("ring1") != null && s.equipped.get("ring2") == null) {
            slot = "ring2";
        }
        PlayerState.Item old = s.equipped.get(slot);
        s.equipped.put(slot, it);
        if (old != null) {
            if (s.equipBag.size() < bagCap(s)) {
                s.equipBag.add(old);
            } else {
                s.gold = Num.add(s.gold, EquipFactory.sellPrice(old));
            }
        }
        return inventoryView(s);
    }

    /**
     * 加穿到额外装备栏（无限装备栏天赋）：放入第一个空的 extra_N 栏。
     * 与 {@link #equip} 的区别：不占原生相位、不替换已穿装备（要换先卸下），
     * 面板照常叠加（PanelCalculator 遍历全部已穿装备）。
     */
    public Map<String, Object> equipExtra(PlayerState s, int bagIdx) {
        if (bagIdx < 0 || bagIdx >= s.equipBag.size()) {
            throw ApiException.badRequest("背包索引越界");
        }
        int open = openExtraSlots(s);
        if (open <= 0) {
            throw ApiException.badRequest("未觉醒「无限装备栏」天赋，没有额外装备栏");
        }
        // 同名吞噬:相同装备只保留已穿的那件,背包件属性累加进去——
        // 已穿件数不随重复掉落无限膨胀(渲染/面板遍历/存档体积的卡顿源)
        PlayerState.Item bagItem = s.equipBag.get(bagIdx);
        PlayerState.Item sameWorn = findEquippedByName(s, bagItem.name);
        if (sameWorn != null) {
            devourInto(sameWorn, bagItem);
            s.equipBag.remove(bagIdx);
            return inventoryView(s);
        }
        String target = null;
        for (int i = 0; i < open; i++) {
            String key = "extra_" + i;
            if (s.equipped.get(key) == null) {
                target = key;
                break;
            }
        }
        if (target == null) {
            throw ApiException.badRequest("额外装备栏状态异常，请刷新后重试");   // 理论不可达:open=已用+1 恒有一个空位
        }
        PlayerState.Item it = s.equipBag.remove(bagIdx);
        s.equipped.put(target, it);
        return inventoryView(s);
    }

    public Map<String, Object> unequip(PlayerState s, String slot) {
        // 先判容量再移除:remove 后才抛"背包已满"会让物品两边都不在,
        // 被 run() 的 finally persist 落库 → 装备永久丢失
        PlayerState.Item it = s.equipped.get(slot);
        if (it == null) {
            throw ApiException.badRequest("该部位没有装备");
        }
        if (s.equipBag.size() >= bagCap(s)) {
            throw ApiException.badRequest("背包已满,先清理背包再卸下");
        }
        s.equipped.remove(slot);
        s.equipBag.add(it);
        return inventoryView(s);
    }

    public Map<String, Object> sell(PlayerState s, int bagIdx) {
        if (bagIdx < 0 || bagIdx >= s.equipBag.size()) {
            throw ApiException.badRequest("背包索引越界");
        }
        PlayerState.Item it = s.equipBag.remove(bagIdx);
        BigDecimal price = EquipFactory.sellPrice(it);
        s.gold = Num.add(s.gold, price);
        Map<String, Object> m = inventoryView(s);
        m.put("soldFor", Num.plain(price));
        return m;
    }

    public Map<String, Object> enhance(PlayerState s, int bagIdx) {
        if (bagIdx < 0 || bagIdx >= s.equipBag.size()) {
            throw ApiException.badRequest("背包索引越界");
        }
        PlayerState.Item it = s.equipBag.get(bagIdx);
        if (it.enhance >= GameConst.ENHANCE_MAX_LV) {
            throw ApiException.badRequest("已达强化上限 +" + GameConst.ENHANCE_MAX_LV);
        }
        BigDecimal cost = EquipFactory.enhanceCost(it);
        if (Num.lt(s.gold, cost)) {
            throw ApiException.badRequest("金币不足，需要 " + Num.cn(cost));
        }
        s.gold = Num.sub(s.gold, cost);
        it.enhance++;
        Map<String, Object> m = inventoryView(s);
        m.put("enhancedTo", it.enhance);
        return m;
    }

    /** 洗炼：花金币重刷词条（底子/强化/品质保留；lockIdx≥0 时锁定该条词条，费用 ×3）。 */
    public Map<String, Object> reroll(PlayerState s, int bagIdx, int lockIdx) {
        if (bagIdx < 0 || bagIdx >= s.equipBag.size()) {
            throw ApiException.badRequest("背包索引越界");
        }
        PlayerState.Item it = s.equipBag.get(bagIdx);
        boolean hasLock = lockIdx >= 0 && lockIdx < it.affixes.size();
        BigDecimal cost = EquipFactory.rerollCost(it);
        if (hasLock) {
            cost = Num.mul(cost, Num.of(3));
        }
        if (Num.lt(s.gold, cost)) {
            throw ApiException.badRequest("金币不足，洗炼需要 " + Num.cn(cost));
        }
        s.gold = Num.sub(s.gold, cost);
        EquipFactory.rerollAffixes(it, hasLock ? lockIdx : -1);
        Map<String, Object> m = inventoryView(s);
        m.put("rerolled", true);
        m.put("locked", hasLock);
        m.put("cost", Num.plain(cost));
        m.put("affixes", it.affixes);
        return m;
    }

    /** 一键卖出：把背包里品质低于 keepQuality 的全部卖掉。 */
    public Map<String, Object> autoSell(PlayerState s, int keepQuality) {
        if (keepQuality < 0 || keepQuality > 3) {
            throw ApiException.badRequest("保留品质不合法");
        }
        BigDecimal total = Num.ZERO;
        int count = 0;
        for (int i = s.equipBag.size() - 1; i >= 0; i--) {
            PlayerState.Item it = s.equipBag.get(i);
            if (it.quality < keepQuality) {
                total = Num.add(total, EquipFactory.sellPrice(it));
                s.equipBag.remove(i);
                count++;
            }
        }
        s.gold = Num.add(s.gold, total);
        Map<String, Object> m = inventoryView(s);
        m.put("soldCount", count);
        m.put("soldFor", Num.plain(total));
        return m;
    }

    /** 一键穿最强：按评分（品质+词条+底子）自动换上更优装备。
     * <p>v3.13 重写为 O(n log n)：旧实现每穿一件全量重扫背包找最优(嵌套循环),
     * 3 万件背包 = 9 亿次评分,持锁数秒把轮询直接拖死。语义不变。 */
    public Map<String, Object> autoEquip(PlayerState s) {
        if (s.dead) {
            throw ApiException.badRequest("你已经死了");
        }
        int changed = fastAutoEquip(s, false);
        Map<String, Object> m = inventoryView(s);
        m.put("equippedCount", changed);
        return m;
    }

    /**
     * 换装核心(O(n log n),无视图开销):autoEquip 端点与挂机自动管护共用。
     *
     * @param sellWhites true=先把背包白色(quality&lt;1)卖掉再穿(挂机管护语义)
     * @return 变更件数
     */
    static int fastAutoEquip(PlayerState s, boolean sellWhites) {
        // 先吞噬历史遗留的同名已穿装备(v3.12 前允许同名多件,性能/体积膨胀源)
        // O(equipped 键数)≈几十,保留在短路前:空背包也要清理历史膨胀
        devourLegacyDuplicates(s);
        // 空转短路(挂机稳态):背包空时卖白装/原生换装/穿入全部无事可做,
        // 每秒轮询触发一次,不短路则 sort+两阶段扫描每秒白跑
        if (s.equipBag.isEmpty()) {
            return 0;
        }
        if (sellWhites) {
            // 白装(quality<1)直接折现。重建列表而非逐个 remove:ArrayList 单点删除 O(n),
            // 3 万件背包卖 1700 件白装就是千万级元素搬移
            BigDecimal total = Num.ZERO;
            List<PlayerState.Item> kept = new ArrayList<>(s.equipBag.size());
            for (PlayerState.Item x : s.equipBag) {
                if (x.quality < 1) {
                    total = Num.add(total, EquipFactory.sellPrice(x));
                } else {
                    kept.add(x);
                }
            }
            s.gold = Num.add(s.gold, total);
            s.equipBag = kept;
        }
        int changed = 0;
        List<PlayerState.Item> bag = s.equipBag;
        // —— 阶段一:原生栏。单遍扫描,每部位只与当前已穿件比一次 ——
        // (mid-list remove 是 O(n),但替换次数受部位数约束,总量小;背包经管护后 ≤ 上限,常态几十件)
        for (int i = 0; i < bag.size(); i++) {
            PlayerState.Item it = bag.get(i);
            String slot = it.slot;
            if ("ring1".equals(slot) && s.equipped.get("ring1") != null && s.equipped.get("ring2") == null) {
                slot = "ring2";
            }
            PlayerState.Item cur = s.equipped.get(slot);
            if (cur == null || equipScore(it) > equipScore(cur)) {
                PlayerState.Item old = s.equipped.put(slot, it);
                bag.remove(i);
                if (old != null) {
                    bag.add(old);
                }
                changed++;
                i--;
            }
        }
        // —— 阶段二:无限装备栏(baglord 专属)——剩余背包按评分从高到低全部穿入 ——
        // 排序一次取代逐件全量扫;同名吞噬按名字哈希索引 O(1);
        // 全部穿入/吞噬后整体 clear,杜绝逐件 remove 的 O(n²)
        if ("baglord".equals(s.talent) && !bag.isEmpty()) {
            Map<String, PlayerState.Item> worn = new LinkedHashMap<>();
            for (PlayerState.Item w : s.equipped.values()) {
                if (w != null && w.name != null) {
                    worn.put(w.name, w);
                }
            }
            bag.sort((a, b) -> Double.compare(equipScore(b), equipScore(a)));
            for (PlayerState.Item it : bag) {
                PlayerState.Item sameWorn = worn.get(it.name);
                if (sameWorn != null) {
                    devourInto(sameWorn, it);
                } else {
                    s.equipped.put(nextFreeExtra(s), it);
                    worn.put(it.name, it);
                }
                changed++;
            }
            bag.clear();
        }
        return changed;
    }

    /** 最靠前的空 extra_N 下标(与 equipExtra 语义一致:优先回填空洞)。 */
    private static String nextFreeExtra(PlayerState s) {
        int i = 0;
        while (s.equipped.containsKey("extra_" + i)) {
            i++;
        }
        return "extra_" + i;
    }

    /**
     * 挂机自动管护(v3.13):卖白装 → 同名吞噬 → 穿最强,单次调用内完成。
     * 由 GameController.run() 在结算后按 autoEquipBest 开关触发;全程持锁、无 IO。
     */
    public static void autoMaintain(PlayerState s) {
        if (s.dead) {
            return;
        }
        try {
            fastAutoEquip(s, true);
        } catch (RuntimeException ex) {
            // 管护失败不影响主流程,下一帧重试;但绝不静默——每秒重复失败必须能在日志里看见
            log.warn("autoMaintain 管护异常(存档 gen={}, zone={}, bag={}): {}",
                    s.gen, s.zone, s.equipBag.size(), ex.getMessage());
        }
    }

    /** 装备评分：品质×1000 + 词条数×100 + 底子数值和，强化 +5%/级。 */
    private static double equipScore(PlayerState.Item it) {
        double score = (it.quality + 1) * 1000 + (it.affixes == null ? 0 : it.affixes.size() * 100);
        if (it.base != null) {
            for (BigDecimal v : it.base.values()) {
                score += v.doubleValue();
            }
        }
        return score * (1 + it.enhance * 0.05);
    }

    public Map<String, Object> discard(PlayerState s, int bagIdx) {
        if (bagIdx < 0 || bagIdx >= s.equipBag.size()) {
            throw ApiException.badRequest("背包索引越界");
        }
        s.equipBag.remove(bagIdx);
        return inventoryView(s);
    }

    // ==================== 逃跑 / Boss ====================

    /** 挂机策略设置：血线/蓝线（0~0.9，0=关闭）+ 解毒/逃跑线 + 燃烧/技能常驻开关。 */
    public Map<String, Object> setAutoLines(PlayerState s, Double hpLine, Double mpLine,
                                            Boolean autoCure, Double fleeLine,
                                            Boolean autoBurn, Boolean autoSkill,
                                            Boolean autoRebirth, Integer autoSellKeep,
                                            Boolean autoEquipBest) {
        double hp = hpLine == null ? s.autoHpLine : Math.max(0, Math.min(0.9, hpLine));
        double mp = mpLine == null ? s.autoMpLine : Math.max(0, Math.min(0.9, mpLine));
        double fl = fleeLine == null ? s.autoFleeLine : Math.max(0, Math.min(0.5, fleeLine));
        s.autoHpLine = hp;
        s.autoMpLine = mp;
        s.autoFleeLine = fl;
        if (autoCure != null) {
            s.autoCure = autoCure;
        }
        if (autoBurn != null) {
            s.autoBurn = autoBurn;
        }
        if (autoSkill != null) {
            s.autoSkill = autoSkill;
        }
        if (autoRebirth != null) {
            s.autoRebirth = autoRebirth;
        }
        if (autoSellKeep != null) {
            s.autoSellKeep = Math.max(0, Math.min(3, autoSellKeep));
        }
        if (autoEquipBest != null) {
            s.autoEquipBest = autoEquipBest;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("autoHpLine", hp);
        m.put("autoMpLine", mp);
        m.put("autoCure", s.autoCure);
        m.put("autoFleeLine", s.autoFleeLine);
        m.put("autoBurn", s.autoBurn);
        m.put("autoSkill", s.autoSkill);
        m.put("autoRebirth", s.autoRebirth);
        m.put("autoSellKeep", s.autoSellKeep);
        m.put("autoEquipBest", s.autoEquipBest);
        return m;
    }

    /** 附魔（DESIGN.md §6.2/§8）：消耗卷轴给武器/弓附加魔伤词条（元素亲和被动加成）。 */
    public Map<String, Object> enchant(PlayerState s, String scrollCode, int bagIdx) {
        Object[] scroll = GameConst.SCROLLS.get(scrollCode);
        if (scroll == null) {
            throw ApiException.badRequest("卷轴不存在：" + scrollCode);
        }
        if (bagIdx < 0 || bagIdx >= s.equipBag.size()) {
            throw ApiException.badRequest("背包索引越界");
        }
        PlayerState.Item it = s.equipBag.get(bagIdx);
        if (!"weapon".equals(it.slot) && !"bow".equals(it.slot)) {
            throw ApiException.badRequest("只能给武器或弓附魔");
        }
        boolean has = it.affixes.stream().anyMatch(a -> "ENCHANT".equals(a.sideKey));
        if (has) {
            throw ApiException.badRequest("该武器已附魔");
        }
        int have = s.materials.getOrDefault(scrollCode, 0);
        if (have < 1) {
            throw ApiException.badRequest("缺少卷轴：" + scroll[0]);
        }
        s.materials.merge(scrollCode, -1, Integer::sum);
        if (s.materials.get(scrollCode) <= 0) {
            s.materials.remove(scrollCode);
        }
        PlayerState.AffixInst inst = new PlayerState.AffixInst();
        inst.name = "附魔·" + scroll[0];
        inst.key = "MATK";
        inst.type = "flat";
        inst.val = Num.of(((Number) scroll[2]).doubleValue());
        inst.rarity = "epic";
        inst.sideKey = "ENCHANT";
        inst.sideVal = null;
        it.affixes.add(inst);
        Map<String, Object> m = inventoryView(s);
        m.put("enchanted", scroll[0]);
        m.put("matkBonus", ((Number) scroll[2]).doubleValue());
        return m;
    }

    public Map<String, Object> flee(PlayerState s) {
        if (s.dead) {
            throw ApiException.badRequest("你已经死了");
        }
        if ("village".equals(s.zone)) {
            return Map.of("ok", true, "msg", "村口无需逃跑");
        }
        PanelCalculator.Panel p = PanelCalculator.of(s);
        double rate = GameConst.FLEE_BASE;
        if (s.mob != null) {
            rate = Math.min(GameConst.FLEE_CAP,
                    GameConst.FLEE_BASE + (p.SPD().doubleValue() - s.mob.SPD) / GameConst.FLEE_SPD_FACTOR);
        }
        boolean ok = Math.random() < rate;
        if (!ok) {
            s.life.curHP = Num.mul(s.life.curHP, Num.of(1 - GameConst.FLEE_FAIL_COST));
            if (Num.lte(s.life.curHP, Num.ZERO)) {
                s.dead = true;
                s.life.curHP = Num.ZERO;
                s.deathCause = "逃跑失败，被" + (s.mob != null ? s.mob.name : "怪物") + "击杀";
            }
        }
        s.zone = "village";
        GameEngine.spawnMob(s, s.zone);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", ok);
        m.put("rate", rate);
        m.put("dead", s.dead);
        m.put("curHP", Num.plain(s.life.curHP));
        return m;
    }

    public Map<String, Object> startBoss(PlayerState s) {
        if (s.dead) {
            throw ApiException.badRequest("你已经死了");
        }
        GameEngine.spawnBoss(s);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("mob", mobView(s));
        return m;
    }

    // ==================== 技能学习 ====================

    public Map<String, Object> learnPassive(PlayerState s, String id) {
        if (!GameConst.PASSIVE_SKILLS.containsKey(id)) {
            throw ApiException.badRequest("被动技能不存在");
        }
        if (s.passives.contains(id)) {
            throw ApiException.badRequest("已经掌握该被动");
        }
        int price = com.gongpu.cfg.GameData.instance().passivePrice(id);
        if (Num.lt(s.gold, Num.of(price))) {
            throw ApiException.badRequest("金币不足，需要 " + price);
        }
        s.gold = Num.sub(s.gold, Num.of(price));
        s.passives.add(id);
        return Map.of("ok", true, "passives", new ArrayList<>(s.passives), "gold", Num.plain(s.gold));
    }

    // ==================== 天赋觉醒 ====================

    /** 觉醒天赋池展示（含已抽结果）。 */
    public Map<String, Object> talentView(PlayerState s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("drawn", s.talent != null);
        m.put("talent", s.talent);
        List<Map<String, Object>> pool = new ArrayList<>();
        for (GameConst.TalentDef t : GameConst.TALENTS.values()) {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("id", t.id());
            tm.put("name", t.name());
            tm.put("emoji", t.emoji());
            tm.put("desc", t.desc());
            tm.put("mine", t.id().equals(s.talent));
            // 抽中概率:两大顶级机缘各 45%,其余平分 10%(各 2%)
            boolean top = "eternal".equals(t.id()) || "baglord".equals(t.id()) || "statlord".equals(t.id());
            tm.put("rate", top ? 0.45 : 0.02);
            tm.put("topTier", top);
            tm.put("choosable", !top);
            pool.add(tm);
        }
        m.put("pool", pool);
        return m;
    }

    /**
     * 觉醒仪式：每个灵魂降生时触摸一次命运（每档一次，转生保留）。
     * <p>两大顶级机缘（被动永续/无限装备栏）各 45% 抽中，抽中即"万中无一的机缘"级仪式；
     * 其余天赋平分 10%，可自选（顶级机缘只能靠命）。</p>
     */
    public Map<String, Object> drawTalent(PlayerState s, String chosen) {
        if (s.talent != null) {
            // 旧档 talent id 可能不在表内(历史配置改动),非法值视为未觉醒并清零重抽,而不是 NPE 500
            GameConst.TalentDef cur = GameConst.TALENTS.get(s.talent);
            if (cur != null) {
                throw ApiException.badRequest("已经觉醒过天赋了：" + cur.name());
            }
            log.warn("非法 talent id 归零重抽: {}", s.talent);
            s.talent = null;
        }
        String got;
        if (chosen != null && !chosen.isBlank()) {
            // 顶级机缘（被动永续/无限装备栏/无限属性点）不许直接指定，只能靠命
            if ("eternal".equals(chosen) || "baglord".equals(chosen) || "statlord".equals(chosen)
                    || !GameConst.TALENTS.containsKey(chosen)) {
                throw ApiException.badRequest("该天赋只能靠命运抽中，无法自选");
            }
            got = chosen;
        } else {
            got = rollTalent();
        }
        boolean topTier = "eternal".equals(got) || "baglord".equals(got) || "statlord".equals(got);
        s.talent = got;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("talent", got);
        m.put("topTier", topTier);
        GameConst.TalentDef def = GameConst.TALENTS.get(got);
        m.put("name", def.name());
        m.put("emoji", def.emoji());
        m.put("desc", def.desc());
        return m;
    }

    /** 抽天赋：三大顶级机缘（被动永续/无限装备栏/无限属性点）各 30%，其余天赋平分剩余 10%。 */
    static String rollTalent() {   // 包内可见:概率分布单测用
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double roll = r.nextDouble();
        if (roll < GameConst.TOP_TALENT_RATE) {
            return "eternal";
        }
        if (roll < GameConst.TOP_TALENT_RATE * 2) {
            return "baglord";
        }
        if (roll < GameConst.TOP_TALENT_RATE * 3) {
            return "statlord";
        }
        List<String> pool = new ArrayList<>(GameConst.TALENTS.keySet());
        pool.remove("eternal");
        pool.remove("baglord");
        pool.remove("statlord");
        return pool.get(r.nextInt(pool.size()));
    }

    /**
     * 无限属性点天赋(statlord 专属):自由属性点加点。
     * 加点值 = STAT_POINT_STEP[stat] × count,累计进 statPoints,PanelCalculator 并入面板 flat 区。
     */
    public Map<String, Object> allocateStatPoint(PlayerState s, String stat, int count) {
        if (!"statlord".equals(s.talent)) {
            throw ApiException.badRequest("未觉醒「无限属性点」天赋，无法加点");
        }
        java.math.BigDecimal step = GameConst.STAT_POINT_STEP.get(stat);
        if (step == null) {
            throw ApiException.badRequest("该属性不可加点：" + stat);
        }
        if (count < 1) {
            throw ApiException.badRequest("加点数量至少 1");
        }
        if (Num.lt(s.freePoints, Num.of(count))) {
            throw ApiException.badRequest("自由属性点不足（当前 " + Num.plain(s.freePoints) + "）");
        }
        s.statPoints.merge(stat, step.multiply(java.math.BigDecimal.valueOf(count)), java.math.BigDecimal::add);
        s.freePoints = Num.sub(s.freePoints, Num.of(count));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("stat", stat);
        m.put("count", count);
        m.put("freePoints", Num.plain(s.freePoints));
        return m;
    }

    /**
     * 当前开放的额外装备栏数（无相位限制，任何装备可穿）。
     * <p>v3.12 改版:baglord 天赋<b>觉醒即无限</b>,不再按天 +1——
     * 实现为「已用无相位栏数 + 1」,永远恰好有一个空位可加穿,加穿后自动再开一格;
     * 旧档积累的 extraSlots 字段保留兼容但不再参与计算。非 baglord 恒为 0。</p>
     */
    public static int openExtraSlots(PlayerState s) {
        return "baglord".equals(s.talent) ? usedExtraCount(s) + 1 : 0;
    }

    /** 按名字找第一件同名已穿装备(原生/无相位都算)。 */
    private static PlayerState.Item findEquippedByName(PlayerState s, String name) {
        if (name == null) {
            return null;
        }
        for (PlayerState.Item w : s.equipped.values()) {
            if (w != null && name.equals(w.name)) {
                return w;
            }
        }
        return null;
    }

    /**
     * 吞噬:把 src 的属性累加进 dst 后即被"消化"(调用方负责把 src 移出背包/装备栏)。
     * base 求和;同名同键同型词条 val 叠加,不同词条追加;强化取较高;品质取较高。
     * 面板层的大数/封顶(BLESS_CAP 等)不变——累加发生在装备词条层。
     */
    private static void devourInto(PlayerState.Item dst, PlayerState.Item src) {
        for (Map.Entry<String, BigDecimal> e : src.base.entrySet()) {
            // BULLET_MP 是「每发耗蓝」成本属性,累加会让合并枪的耗蓝膨胀到超出蓝上限=永久哑火
            // (2026-10-31 玩家档实锤:BULLET_MP 叠到 265 > 蓝上限 80)。保留主件的耗蓝不累加。
            if ("BULLET_MP".equals(e.getKey())) {
                continue;
            }
            // BAG_CAP 是容量属性,累加让吞了几千条腰带的档背包上限爆到 3.3 万格
            // (2026-10-31 玩家档实锤:bagCap 33720=100+33620,背包堆 3.3 万件=卡死根源)。
            // 容量不是增益叠加语义——取两件中的较大值。
            if ("BAG_CAP".equals(e.getKey())) {
                BigDecimal cur = dst.base.get("BAG_CAP");
                if (cur == null || Num.lt(cur, e.getValue())) {
                    dst.base.put("BAG_CAP", e.getValue());
                }
                continue;
            }
            dst.base.merge(e.getKey(), e.getValue(), BigDecimal::add);
        }
        for (PlayerState.AffixInst a : src.affixes) {
            PlayerState.AffixInst same = null;
            for (PlayerState.AffixInst x : dst.affixes) {
                if (x.name != null && x.name.equals(a.name)
                        && x.key != null && x.key.equals(a.key)
                        && x.type != null && x.type.equals(a.type)) {
                    same = x;
                    break;
                }
            }
            if (same != null) {
                same.val = Num.add(same.val, a.val);
            } else {
                dst.affixes.add(a);
            }
        }
        if (src.enhance > dst.enhance) {
            dst.enhance = src.enhance;
        }
        if (dst.quality < src.quality) {
            dst.quality = src.quality;
        }
    }

    /**
     * 历史遗留吞噬清理:v3.12 前允许穿多件同名装备,把它们两两合并成一件。
     * 只处理 extra_N 栏(原生槽位 ring1/ring2 双戒是合法配置,不动)。
     * 由 autoEquip 入口触发——点一次「穿最强」即清理历史膨胀。
     */
    private static void devourLegacyDuplicates(PlayerState s) {
        Map<String, PlayerState.Item> keep = new LinkedHashMap<>();
        List<String> removeKeys = new ArrayList<>();
        for (Map.Entry<String, PlayerState.Item> e : s.equipped.entrySet()) {
            String key = e.getKey();
            PlayerState.Item it = e.getValue();
            if (it == null || !key.startsWith("extra_")) {
                continue;
            }
            PlayerState.Item kept = keep.get(it.name);
            if (kept == null) {
                keep.put(it.name, it);
            } else {
                devourInto(kept, it);
                removeKeys.add(key);
            }
        }
        for (String k : removeKeys) {
            s.equipped.remove(k);
        }
    }

    /** 已穿入的无相位栏数(extra_N 键计数,容忍卸下产生的空洞)。 */
    private static int usedExtraCount(PlayerState s) {
        int used = 0;
        for (String k : s.equipped.keySet()) {
            if (k != null && k.startsWith("extra_")) {
                used++;
            }
        }
        return used;
    }

    // ==================== 转生 ====================

    /**
     * 自动飞升(挂机管家)的赋能选择:全赋能=强化纪元选 HP 永远强化生命;
     * 否则按 BLESS_ORDER 补第一条缺口。静态无副作用,自动飞升回归单测用。
     * 注意:调用方必须先取 chosen 再 validateRebirth——validate(s, null) 两个分支
     * 都会抛"请选择赋能",先 validate 后取 chosen 的顺序会让自动飞升永远不触发。
     */
    public static String autoRebirthChoice(PlayerState s) {
        if (s.blessings.size() >= GameConst.BLESS_ORDER.size()) {
            return "HP";
        }
        for (String k : GameConst.BLESS_ORDER) {
            if (!s.blessings.containsKey(k)) {
                return k;
            }
        }
        return "HP";
    }

    /** 转生前置校验(无副作用):死亡/攻速门槛/赋能选择合法性。立金碑等副作用前必须先过这里。 */
    public void validateRebirth(PlayerState s, String chosenKey) {
        if (s.dead) {
            throw ApiException.badRequest("你已经死了，无法转生");
        }
        PanelCalculator.Panel p = PanelCalculator.of(s);
        if (Num.lt(p.ASPD(), GameConst.CLEAR_ASPD)) {
            throw ApiException.badRequest("攻速未达 1 载次/秒，尚未通关");
        }
        if (chosenKey != null && !GameConst.BLESS_ORDER.contains(chosenKey)) {
            throw ApiException.badRequest("赋能属性不合法");
        }
        boolean allBlessed = s.blessings.size() >= GameConst.BLESS_ORDER.size();
        if (!allBlessed) {
            if (chosenKey == null) {
                throw ApiException.badRequest("请选择本世要新增的赋能属性");
            }
            if (s.blessings.containsKey(chosenKey)) {
                throw ApiException.badRequest("该属性已经赋能过了");
            }
        } else if (chosenKey == null || s.blessings.get(chosenKey) == null) {
            throw ApiException.badRequest("请选择要强化的赋能属性");
        }
    }

    public Map<String, Object> rebirth(PlayerState s, String chosenKey) {
        validateRebirth(s, chosenKey);

        boolean allBlessed = s.blessings.size() >= GameConst.BLESS_ORDER.size();
        if (!allBlessed) {
            if (chosenKey == null) {
                throw ApiException.badRequest("请选择本世要新增的赋能属性");
            }
            if (s.blessings.containsKey(chosenKey)) {
                throw ApiException.badRequest("该属性已经赋能过了");
            }
            s.blessings.put(chosenKey, new PlayerState.Bless(0));
        } else {
            // 强化纪元：自选一条已有赋能，步长 +1 级
            if (chosenKey == null) {
                throw ApiException.badRequest("请选择要强化的赋能属性");
            }
            PlayerState.Bless b = s.blessings.get(chosenKey);
            if (b == null) {
                throw ApiException.badRequest("该属性尚未赋能");
            }
            b.lv++;
        }

        s.gen++;
        s.stats.highestGen = Math.max(s.stats.highestGen, s.gen);
        s.stats.rebirths++;
        // 与死亡重开共用同一份重置语义(守则 7:基线清零时派生数据同步清理)。
        // 此前这里是 90% 重复的手抄副本且漏清 potionBought/recentKills——两份逻辑必然漂移。
        com.gongpu.state.SaveService.resetForNewLife(s);
        s.dead = false;
        s.deathCause = null;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("gen", s.gen);
        m.put("blessings", PanelCalculator.blessLine(s));
        return m;
    }

    // ==================== 视图 ====================

    public Map<String, Object> mobView(PlayerState s) {
        if (s.mob == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.mob.id);
        m.put("name", s.mob.name);
        m.put("emoji", s.mob.emoji);
        m.put("boss", s.mob.boss);
        m.put("curHP", s.mob.maxHP == null ? "Infinity" : Num.plain(s.mob.curHP));
        m.put("maxHP", s.mob.maxHP == null ? "Infinity" : Num.plain(s.mob.maxHP));
        m.put("PATK", Num.plain(s.mob.PATK));
        m.put("MATK", Num.plain(s.mob.MATK));
        m.put("ASPD", s.mob.ASPD);
        m.put("RNG", s.mob.RNG);
        m.put("SPD", s.mob.SPD);
        return m;
    }

    public Map<String, Object> inventoryView(PlayerState s) {
        Map<String, Object> m = new LinkedHashMap<>();
        // 与 fullView 同口径截断(v3.13):操作响应携带背包视图,同样防巨包
        int bagTotal = s.equipBag.size();
        List<PlayerState.Item> bagView = bagTotal > GameConst.BAG_VIEW_LIMIT
                ? new ArrayList<>(s.equipBag.subList(0, GameConst.BAG_VIEW_LIMIT))
                : s.equipBag;
        m.put("bag", bagView);
        m.put("bagTotal", bagTotal);
        m.put("equipped", s.equipped);
        m.put("gold", Num.plain(s.gold));
        m.put("bagMax", bagCap(s));
        return m;
    }

    public Map<String, Object> fullView(PlayerState s) {
        PanelCalculator.Panel p = PanelCalculator.of(s);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gen", s.gen);
        m.put("zone", s.zone);
        m.put("dead", s.dead);
        m.put("deathCause", s.deathCause);
        m.put("talent", s.talent);

        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("maxHP", Num.plain(p.maxHP()));
        panel.put("curHP", Num.plain(p.curHP()));
        panel.put("MP", Num.plain(p.MP()));
        panel.put("curMP", Num.plain(p.curMP()));
        panel.put("PATK", Num.plain(p.PATK()));
        panel.put("MATK", Num.plain(p.MATK()));
        panel.put("PDEF", Num.plain(p.PDEF()));
        panel.put("MDEF", Num.plain(p.MDEF()));
        panel.put("REGEN", Num.plain(p.REGEN()));
        // 「每秒回复 X% 生命上限」类词条(回春戒生命泉涌)的实际每秒回复量——
        // 前端回血行显示 REGEN+此值,否则 1%上限的大头在面板里完全不可见
        panel.put("regenHpPerSec", Num.plain(p.regenHpPerSec()));
        panel.put("MREGEN", Num.plain(p.MREGEN()));
        panel.put("ASPD", Num.plain(p.ASPD()));
        panel.put("RNG", Num.plain(p.RNG()));
        panel.put("SPD", Num.plain(p.SPD()));
        panel.put("CRIT", Num.plain(p.CRIT()));
        panel.put("CRITD", Num.plain(p.CRITD()));
        panel.put("ACC", Num.plain(p.ACC()));
        panel.put("EVA", Num.plain(p.EVA()));
        panel.put("LIFESTEAL", Num.plain(p.LIFESTEAL()));
        // 护盾(v3.14 护盾药剂):前端血条上方显示吸收盾余量
        panel.put("shield", Num.plain(s.shield == null ? Num.ZERO : s.shield));
        // 哑火提示用:前端判断 BULLETMP > MP 时显示「弹药哑火」换枪警示
        panel.put("BULLETMP", Num.plain(p.BULLETMP()));
        panel.put("silenced", p.silenced());
        m.put("panel", panel);

        m.put("gold", Num.plain(s.gold));
        m.put("blessings", PanelCalculator.blessLine(s));
        m.put("blessLevels", blessLevels(s));
        m.put("mob", mobView(s));
        m.put("buffs", s.buffs);
        m.put("cds", s.cds);
        m.put("passives", s.passives);
        m.put("potions", s.potions);
        m.put("equipped", s.equipped);
        // v3.13 背包截断防复发:此前全量下发 3.3 万件=13.9MB JSON,前端每秒解析+渲染
        // 直接卡死。视图只带前 200 件(强化/洗炼/卖出按索引操作,截断段不可及,
        // 服务端管护会先把背包清到安全规模),bagTotal 让前端如实显示总数。
        int bagTotal = s.equipBag.size();
        List<PlayerState.Item> bagView = bagTotal > GameConst.BAG_VIEW_LIMIT
                ? new ArrayList<>(s.equipBag.subList(0, GameConst.BAG_VIEW_LIMIT))
                : s.equipBag;
        m.put("bag", bagView);
        m.put("bagTotal", bagTotal);
        m.put("bagMax", bagCap(s));
        // 无限装备栏天赋:当前开放的额外栏数(前端渲染 extra_N 格)
        m.put("extraSlots", openExtraSlots(s));

        Map<String, Object> st = new LinkedHashMap<>();
        st.put("punches", Num.plain(s.stats.punches));
        st.put("lifetimePunches", Num.plain(s.stats.lifetimePunches));
        st.put("kills", Num.plain(s.stats.kills));
        st.put("deaths", s.stats.deaths);
        st.put("aliveSeconds", StateCodec.aliveSeconds(s));
        st.put("playSeconds", s.stats.playSeconds);
        st.put("rebirths", s.stats.rebirths);
        st.put("bestMaxHP", s.stats.bestMaxHP);
        m.put("stats", st);
        // 无限属性点天赋:当前自由属性点(非 statlord 恒 0)
        m.put("freePoints", Num.plain(s.freePoints));

        m.put("autoHpLine", s.autoHpLine);
        m.put("autoMpLine", s.autoMpLine);
        m.put("autoCure", s.autoCure);
        m.put("autoFleeLine", s.autoFleeLine);
        m.put("autoBurn", s.autoBurn);
        m.put("autoSkill", s.autoSkill);
        m.put("autoRebirth", s.autoRebirth);
        m.put("autoSellKeep", s.autoSellKeep);
        m.put("autoEquipBest", s.autoEquipBest);
        m.put("recentKills", s.recentKills == null ? new ArrayList<>() : new ArrayList<>(s.recentKills));
        m.put("bossKills", new ArrayList<>(s.bossKills));
        m.put("codex", s.codex);
        m.put("achievements", new ArrayList<>(s.achievements));
        // 技能熟练度：casts=累计施放(封顶100),lv=熟练档位(每20次+1,最高5,每档 CD-3%)
        Map<String, Object> masteryView = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> e : s.mastery.entrySet()) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("casts", Num.plain(e.getValue()));
            mm.put("lv", e.getValue().min(BigDecimal.valueOf(100)).intValue() / 20);
            masteryView.put(e.getKey(), mm);
        }
        m.put("mastery", masteryView);
        m.put("clearAspd", GameConst.CLEAR_ASPD.toPlainString());
        m.put("clearReady", Num.gte(p.ASPD(), GameConst.CLEAR_ASPD));
        m.put("allBlessed", s.blessings.size() >= GameConst.BLESS_ORDER.size());
        m.put("lastSaveAt", s.lastSaveAt);
        return m;
    }

    private Map<String, Object> blessLevels(PlayerState s) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, PlayerState.Bless> e : s.blessings.entrySet()) {
            m.put(e.getKey(), e.getValue().lv);
        }
        return m;
    }

    /** 可赋能属性列表（转生界面用）。 */
    public List<Map<String, Object>> blessOptions(PlayerState s) {
        List<Map<String, Object>> out = new ArrayList<>();
        boolean all = s.blessings.size() >= GameConst.BLESS_ORDER.size();
        PanelCalculator.Panel panel = PanelCalculator.of(s);   // 整面板算一次,逐 key 复用
        for (String key : GameConst.BLESS_ORDER) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", key);
            m.put("name", GameConst.BLESS_NAME.get(key));
            m.put("emoji", GameConst.BLESS_EMOJI.get(key));
            m.put("step", Num.plain(GameConst.BLESS_STEP.get(key)));
            boolean owned = s.blessings.containsKey(key);
            m.put("owned", owned);
            m.put("lv", owned ? s.blessings.get(key).lv : 0);
            boolean capped = panelCapped(panel, key);   // 百分比属性到上限(暴击/命中/闪避)
            m.put("capped", capped);
            m.put("disabled", all ? !owned || capped : owned || capped);
            out.add(m);
        }
        return out;
    }

    /**
     * 赋能封顶判定:看面板最终值(装备词条计入后再 min(cap)),而不是 s.fixed 裸值。
     * 旧实现只看 fixed——装备把面板命中顶满 100% 时仍显示可选,玩家选了会被
     * Fixed.add 静默钳在 cap,一次转生机会被浪费。
     */
    private static boolean panelCapped(PanelCalculator.Panel p, String key) {
        java.math.BigDecimal cap = GameConst.BLESS_CAP.get(key);
        if (cap == null) {
            return false;
        }
        java.math.BigDecimal v = switch (key) {
            case "CRIT" -> p.CRIT();
            case "ACC" -> p.ACC();
            case "EVA" -> p.EVA();
            default -> null;
        };
        return v != null && Num.gte(v, cap);
    }
}
