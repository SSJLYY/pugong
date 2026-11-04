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

        int cleansed = 0;
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
            case "charge" -> s.buffs.put("charge", 30.0);
            case "void_slash" -> s.buffs.put("void_slash", 30.0);
            case "flurry" -> s.flurryHits += GameConst.FLURRY_HITS;
            case "hex_slow" -> {
                // 迟缓咒（第七批）：作用于当前怪物（无目标时空放，仅进 CD 不报错——与怒吼的强校验不同，容错优先）
                if (s.mob != null) {
                    s.mob.slowT = def.dur();
                }
            }
            case "block" -> s.buffs.put("block", (double) def.dur());
            case "rapid" -> s.buffs.put("rapid", (double) def.dur());
            case "bloodlust" -> s.buffs.put("bloodlust", (double) def.dur());
            case "iron_wall" -> s.buffs.put("iron_wall", (double) def.dur());
            case "heal" -> {
                // 治愈术：即时回复（首个非 buff 型主动）——按施法前已算好的面板上限结算，封顶不溢出
                BigDecimal healAmt = Num.mul(p.maxHP(), Num.of(GameConst.HEAL_SKILL_RATIO));
                s.life.curHP = Num.min(Num.add(s.life.curHP, healAmt), p.maxHP());
            }
            case "cleanse" -> {
                // 净化：清除全部负面状态（毒/灼烧/沉默/时滞/寒霜）——解毒剂的技能版，不消耗库存
                for (String debuff : new String[]{"poison", "burn_dot", "silence", "slow", "frost"}) {
                    if (s.buffs.remove(debuff) != null) {
                        cleansed++;
                    }
                }
            }
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
        if ("cleanse".equals(skillId)) {
            m.put("cleansed", cleansed);
        }
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
        s.usedPotions.add(potionId);   // 品药成就：记录用过的种类
        s.stats.potionsUsed++;         // 终身用药次数（每日任务「品药令」计数口径）

        PanelCalculator.Panel p = PanelCalculator.of(s);
        if (def.healHP() > 0) {
            // 诅咒在场时喝药回血同样减半（与回复/吸血同口径）
            BigDecimal heal = Num.mul(Num.mul(p.maxHP(), Num.of(def.healHP())), GameEngine.healMul(s));
            s.life.curHP = Num.min(Num.add(s.life.curHP, heal), p.maxHP());
        }
        if (def.healMP() > 0) {
            BigDecimal heal = Num.mul(p.MP(), Num.of(def.healMP()));
            s.life.curMP = Num.min(Num.add(s.life.curMP, heal), p.MP());
        }
        if ("cure".equals(potionId)) {
            s.buffs.remove("poison");
            s.buffs.remove("burn_dot");
            // 兼清沉默：Boss 沉默是与毒/灼烧同类的负面状态，一剂全清（解毒剂定位扩为净化剂）
            s.buffs.remove("silence");
            // 兼清寒霜（第七批）：同为怪物词条减益，一剂全清
            s.buffs.remove("frost");
        }
        if (def.buff() != null) {
            // 铁胃（第七批）：食物类 buff 时长 ×1.5（与自动进食同口径 GameEngine.foodDur）
            double dur = "regen_food".equals(def.buff())
                    ? GameEngine.foodDur(s, def.buffDur()) : def.buffDur();
            s.buffs.put(def.buff(), dur);
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

    /**
     * 补给到目标库存：逐瓶买入直到达到 target 或金币不足（动态涨价与手动逐瓶买完全同口径，
     * 买不起剩余部分静默停手并回报实际买入数；药剂不存在/目标非法直接报错）。
     */
    public static Map<String, Object> buyFill(PlayerState s, String potionId, int target) {
        GameConst.PotionDef def = com.gongpu.cfg.GameData.instance().potion(potionId);
        if (def == null) {
            throw ApiException.badRequest("药剂不存在");
        }
        if (target < 1 || target > 99) {
            throw ApiException.badRequest("目标库存不合法");
        }
        int bought = 0;
        while (s.potions.getOrDefault(potionId, 0) < target) {
            try {
                buyPotion(s, potionId, 1);
                bought++;
            } catch (RuntimeException e) {
                break;   // 金币不足：停在当前库存
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("bought", bought);
        m.put("left", s.potions.getOrDefault(potionId, 0));
        m.put("gold", Num.plain(s.gold));
        return m;
    }

    /** 一键补给包构成（第七批）：出门四件套的目标库存。 */
    public static final Map<String, Integer> FILL_KIT = Map.of(
            "hp_s", 10, "mp", 5, "cure", 3, "food", 3);

    /**
     * 一键补给包（第七批）：把出门四件套逐件补到目标库存（单件走 buyFill 同口径，
     * 金币不足时该件停在当前库存、不影响其余件）。返回逐件实际买入数。
     */
    public static Map<String, Object> buyFillKit(PlayerState s) {
        Map<String, Object> perItem = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<String, Integer> e : FILL_KIT.entrySet()) {
            int bought = 0;
            try {
                Map<String, Object> r = buyFill(s, e.getKey(), e.getValue());
                bought = ((Number) r.get("bought")).intValue();
            } catch (RuntimeException ignored) {
                // 药剂缺失等单件异常不拖垮整包
            }
            perItem.put(e.getKey(), bought);
            total += bought;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("bought", total);
        m.put("perItem", perItem);
        m.put("gold", Num.plain(s.gold));
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
        if (s.equipBag.get(bagIdx).locked) {
            throw ApiException.badRequest("装备已锁定，先解锁再卖出");
        }
        PlayerState.Item it = s.equipBag.remove(bagIdx);
        BigDecimal price = EquipFactory.sellPrice(it);
        s.gold = Num.add(s.gold, price);
        Map<String, Object> m = inventoryView(s);
        m.put("soldFor", Num.plain(price));
        return m;
    }

    /** 分解产出：材料按装备来源区域（ZONE_MAT 同口径），数量 = 品质+1，强化 ≥5 额外 +1。 */
    static Object[] dismantleYield(PlayerState.Item it) {
        String zone = it.zone == null ? "plain" : it.zone;
        String mat = GameConst.ZONE_MAT.getOrDefault(zone, "pelt");
        int count = it.quality + 1 + (it.enhance >= 5 ? 1 : 0);
        return new Object[]{mat, count};
    }

    /** 分解入账（材料合并 + 终身计数），手动分解与掉落自动分解共用。返回 [材料 code, 数量]。 */
    static Object[] grantDismantle(PlayerState s, PlayerState.Item it) {
        Object[] y = dismantleYield(it);
        s.materials.merge((String) y[0], (Integer) y[1], Integer::sum);
        s.stats.dismantles++;
        return y;
    }

    /**
     * 掉落拦截决策：自动分解（smelt）优先于自动售卖（sell），都不命中才入包（bag）。
     * 档位语义与 autoSellKeep 一致：品质 < 档位才拦截（4 = 全部）。
     */
    static String dropIntercept(PlayerState s, PlayerState.Item it) {
        if (s.autoSmeltKeep > 0 && it.quality < s.autoSmeltKeep) {
            return "smelt";
        }
        if (s.autoSellKeep > 0 && it.quality < s.autoSellKeep) {
            return "sell";
        }
        return "bag";
    }

    /** 分解：装备 → 区域材料（与卖金二选一的回收通道，喂合成经济）；锁定件拒绝。 */
    public Map<String, Object> dismantle(PlayerState s, int bagIdx) {
        if (bagIdx < 0 || bagIdx >= s.equipBag.size()) {
            throw ApiException.badRequest("背包索引越界");
        }
        if (s.equipBag.get(bagIdx).locked) {
            throw ApiException.badRequest("装备已锁定，先解锁再分解");
        }
        PlayerState.Item it = s.equipBag.remove(bagIdx);
        Object[] y = grantDismantle(s, it);
        String mat = (String) y[0];
        int count = (Integer) y[1];
        Map<String, Object> m = inventoryView(s);
        String[] info = GameConst.MAT_INFO.get(mat);
        m.put("dismantled", Map.of("mat", mat,
                "matName", info == null ? mat : info[0],
                "count", count, "itemName", it.name == null ? "" : it.name));
        return m;
    }

    /**
     * 一键分解：把背包里品质低于 keep 的全部装备分解成区域材料（档位语义与一键卖出一致，
     * 1=白及以下…4=全部；锁定件跳过）。与一键卖出并列的第二条回收通道，喂合成经济。
     */
    public Map<String, Object> dismantleAll(PlayerState s, int keep) {
        if (keep < 0 || keep > 4) {
            throw ApiException.badRequest("保留品质不合法");
        }
        java.util.Map<String, Integer> yields = new java.util.LinkedHashMap<>();
        int count = 0;
        for (int i = s.equipBag.size() - 1; i >= 0; i--) {
            PlayerState.Item it = s.equipBag.get(i);
            if (it.locked) {
                continue;   // 锁定件永不被一键处置（与一键卖出口径一致）
            }
            if (it.quality < keep) {
                Object[] y = grantDismantle(s, it);
                yields.merge((String) y[0], (Integer) y[1], Integer::sum);
                s.equipBag.remove(i);
                count++;
            }
        }
        Map<String, Object> m = inventoryView(s);
        m.put("dismantledCount", count);
        m.put("yields", yields);
        StringBuilder text = new StringBuilder();
        yields.forEach((mat, c) -> {
            String[] info = GameConst.MAT_INFO.get(mat);
            if (text.length() > 0) {
                text.append("、");
            }
            text.append(info == null ? mat : info[0]).append("×").append(c);
        });
        m.put("yieldsText", text.toString());
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

    /** 洗炼（单锁兼容入口）。 */
    public Map<String, Object> reroll(PlayerState s, int bagIdx, int lockIdx) {
        return reroll(s, bagIdx, lockIdx, -1);
    }

    /**
     * 洗炼：花金币重刷词条（底子/强化/品质保留）。
     * 锁定一条费用 ×3；双锁（第七批，须 ≥3 条词条且两条不同）费用 ×5。
     */
    public Map<String, Object> reroll(PlayerState s, int bagIdx, int lockIdx, int lockIdx2) {
        if (bagIdx < 0 || bagIdx >= s.equipBag.size()) {
            throw ApiException.badRequest("背包索引越界");
        }
        PlayerState.Item it = s.equipBag.get(bagIdx);
        java.util.Set<Integer> locks = new java.util.TreeSet<>();
        for (int v : new int[]{lockIdx, lockIdx2}) {
            if (v >= 0) {
                if (v >= it.affixes.size()) {
                    throw ApiException.badRequest("锁定的词条不存在");
                }
                locks.add(v);
            }
        }
        if (lockIdx2 >= 0) {
            if (locks.size() < 2) {
                throw ApiException.badRequest("双锁需要两条不同的词条");
            }
            if (it.affixes.size() < 3) {
                throw ApiException.badRequest("双锁需要至少 3 条词条（总要留一条重刷）");
            }
        }
        BigDecimal cost = EquipFactory.rerollCost(it);
        if (locks.size() == 1) {
            cost = Num.mul(cost, Num.of(3));
        } else if (locks.size() >= 2) {
            cost = Num.mul(cost, Num.of(5));
        }
        if (Num.lt(s.gold, cost)) {
            throw ApiException.badRequest("金币不足，洗炼需要 " + Num.cn(cost));
        }
        s.gold = Num.sub(s.gold, cost);
        // 洗炼保底（第七批）：连续 REROLL_PITY_MAX 次未出史诗，本次强制出一条；出史诗（含保底）清零计数
        boolean pityArmed = s.rerollPity >= GameConst.REROLL_PITY_MAX - 1;
        EquipFactory.rerollAffixes(it, locks, pityArmed);
        boolean gotEpic = it.affixes.stream().anyMatch(a -> "epic".equals(a.rarity));
        s.rerollPity = gotEpic ? 0 : s.rerollPity + 1;
        s.stats.rerolls++;
        Map<String, Object> m = inventoryView(s);
        m.put("rerolled", true);
        m.put("locked", !locks.isEmpty());
        m.put("lockedCount", locks.size());
        m.put("cost", Num.plain(cost));
        m.put("affixes", it.affixes);
        m.put("pity", s.rerollPity);
        m.put("pityMax", GameConst.REROLL_PITY_MAX);
        m.put("pityTriggered", pityArmed && gotEpic);
        return m;
    }

    /** 淬炼消耗的区域材料 key（按装备来源区域，旧档无 zone 按平原兽皮）。 */
    public static String temperMatKey(PlayerState.Item it) {
        return GameConst.ZONE_MAT.getOrDefault(it.zone != null ? it.zone : "plain", "pelt");
    }

    /** 淬炼材料成本：第 n 级需 2×n 个来源区域材料。 */
    public static int temperMatCost(PlayerState.Item it) {
        return 2 * (it.temper + 1);
    }

    /** 淬炼金币成本：300 × 下一级 ×（品质+1）。 */
    public static java.math.BigDecimal temperGoldCost(PlayerState.Item it) {
        return Num.of(300L * (it.temper + 1) * (it.quality + 1));
    }

    /**
     * 淬炼（第七批）：给装备底子叠第二成长线（+4%/级，满 5 级，与强化独立）。
     * 耗来源区域材料 + 金币；锁定件可淬炼（纯增益不毁物，锁定只防处置类操作）。
     */
    public Map<String, Object> temper(PlayerState s, int bagIdx) {
        if (bagIdx < 0 || bagIdx >= s.equipBag.size()) {
            throw ApiException.badRequest("背包索引越界");
        }
        PlayerState.Item it = s.equipBag.get(bagIdx);
        if (it.temper >= GameConst.TEMPER_MAX_LV) {
            throw ApiException.badRequest("已淬炼满级");
        }
        String mat = temperMatKey(it);
        int matCost = temperMatCost(it);
        java.math.BigDecimal goldCost = temperGoldCost(it);
        if (s.materials.getOrDefault(mat, 0) < matCost) {
            String[] info = GameConst.MAT_INFO.get(mat);
            throw ApiException.badRequest("材料不足：淬炼需要 " + matCost + " 个"
                    + (info == null ? mat : info[0]) + "（分解同区域装备可得）");
        }
        if (Num.lt(s.gold, goldCost)) {
            throw ApiException.badRequest("金币不足，淬炼需要 " + Num.cn(goldCost));
        }
        s.materials.merge(mat, -matCost, Integer::sum);
        s.gold = Num.sub(s.gold, goldCost);
        it.temper++;
        s.stats.tempers++;
        Map<String, Object> m = inventoryView(s);
        m.put("temperedTo", it.temper);
        m.put("matKey", mat);
        m.put("matCost", matCost);
        m.put("goldCost", Num.plain(goldCost));
        return m;
    }

    /** 一键卖出：把背包里品质低于 keepQuality 的全部卖掉（1=白及以下…4=紫及以下即全卖）。 */
    public Map<String, Object> autoSell(PlayerState s, int keepQuality) {
        if (keepQuality < 0 || keepQuality > 4) {
            throw ApiException.badRequest("保留品质不合法");
        }
        BigDecimal total = Num.ZERO;
        int count = 0;
        for (int i = s.equipBag.size() - 1; i >= 0; i--) {
            PlayerState.Item it = s.equipBag.get(i);
            if (it.locked) {
                continue;   // 锁定件永不被一键卖处置
            }
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
                if (x.quality < 1 && !x.locked) {
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
        // 锁定件不参与本阶段(不吞噬不穿入,原样留在背包):先摘出、阶段后按原序回填
        List<PlayerState.Item> lockedHold = null;
        if ("baglord".equals(s.talent) && !bag.isEmpty()) {
            for (PlayerState.Item x : bag) {
                if (x.locked) {
                    if (lockedHold == null) {
                        lockedHold = new ArrayList<>();
                    }
                    lockedHold.add(x);
                }
            }
            if (lockedHold != null) {
                bag.removeAll(lockedHold);
            }
        }
        if ("baglord".equals(s.talent) && !bag.isEmpty()) {
            Map<String, PlayerState.Item> worn = new LinkedHashMap<>();
            for (PlayerState.Item w : s.equipped.values()) {
                if (w != null && w.name != null) {
                    worn.put(w.name, w);
                }
            }
            bag.sort((a, b) -> Double.compare(equipScore(b), equipScore(a)));
            // 空位下标只扫一遍并单调递增:逐件调 nextFreeExtra 从 0 重扫是 O(n²),
            // 无限栏穿满几百件时每帧管护都白烧 CPU(下标语义不变:仍优先回填最靠前的空洞)
            int extraIdx = 0;
            while (s.equipped.containsKey("extra_" + extraIdx)) {
                extraIdx++;
            }
            for (PlayerState.Item it : bag) {
                PlayerState.Item sameWorn = worn.get(it.name);
                if (sameWorn != null) {
                    devourInto(sameWorn, it);
                } else {
                    s.equipped.put("extra_" + extraIdx, it);
                    worn.put(it.name, it);
                    extraIdx++;
                    while (s.equipped.containsKey("extra_" + extraIdx)) {
                        extraIdx++;
                    }
                }
                changed++;
            }
            bag.clear();
        }
        if (lockedHold != null) {
            bag.addAll(lockedHold);
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
    /**
     * 管护签名：击杀数（掉落来源）+ 背包/已穿件数 + 全部装备的强化与词条数总和
     * （洗炼/附魔/强化这类只改评分不变件数的操作也能触发重扫）。构成不变则跳过全量管护扫描。
     */
    static long maintainSig(PlayerState s) {
        long kills = s.stats == null || s.stats.kills == null ? 0 : s.stats.kills.longValue();
        long equipped = s.equipped == null ? 0
                : s.equipped.values().stream().filter(it -> it != null).count();
        long gearMark = 0;
        if (s.equipBag != null) {
            for (PlayerState.Item it : s.equipBag) {
                gearMark += it.enhance * 7L + (it.affixes == null ? 0 : it.affixes.size());
            }
        }
        if (s.equipped != null) {
            for (PlayerState.Item it : s.equipped.values()) {
                if (it != null) {
                    gearMark += it.enhance * 7L + (it.affixes == null ? 0 : it.affixes.size());
                }
            }
        }
        return kills * 1_000_003L + (s.equipBag == null ? 0 : s.equipBag.size()) * 131L + equipped * 17L + gearMark;
    }

    public static void autoMaintain(PlayerState s) {
        if (s.dead) {
            return;
        }
        // 每秒轮询都会走到这里：签名未变（没新掉落、没手动换装）就跳过整包扫描，省 CPU
        if (maintainSig(s) == s.lastMaintainSig) {
            return;
        }
        try {
            fastAutoEquip(s, true);
            s.lastMaintainSig = maintainSig(s);   // 存执行后签名：本帧处置引起的变化不再触发下一帧重扫
        } catch (RuntimeException ex) {
            // 管护失败不影响主流程,下一帧重试;但绝不静默——每秒重复失败必须能在日志里看见
            log.warn("autoMaintain 管护异常(存档 gen={}, zone={}, bag={}): {}",
                    s.gen, s.zone, s.equipBag.size(), ex.getMessage());
        }
    }

    /** 装备评分：品质×1000 + 词条数×100 + 底子数值和，强化 +5%/级。 */
    static double equipScore(PlayerState.Item it) {
        double score = (it.quality + 1) * 1000 + (it.affixes == null ? 0 : it.affixes.size() * 100);
        if (it.base != null) {
            for (BigDecimal v : it.base.values()) {
                score += v.doubleValue();
            }
        }
        // 镶嵌宝石计分（第八批）：一阶 +80/颗、二阶 +200/颗——自动换装/整理/管护不再把镶钻装备当裸装换掉
        if (it.gems != null) {
            for (String g : it.gems) {
                score += g != null && g.endsWith("_2") ? 200 : 80;
            }
        }
        return score * (1 + it.enhance * 0.05) * (1 + it.temper * 0.04);
    }

    public Map<String, Object> discard(PlayerState s, int bagIdx) {
        if (bagIdx < 0 || bagIdx >= s.equipBag.size()) {
            throw ApiException.badRequest("背包索引越界");
        }
        if (s.equipBag.get(bagIdx).locked) {
            throw ApiException.badRequest("装备已锁定，先解锁再丢弃");
        }
        s.equipBag.remove(bagIdx);
        return inventoryView(s);
    }

    /** 背包整理：按评分从高到低稳定排序（同分保持原序），好装备顶到前面方便翻找。 */
    /** 每日任务领取（第七批）：完成才可领，金币由 DailyQuests.claim 入账；三任务全领触发全清奖励（每日一次）。 */
    public Map<String, Object> claimDaily(PlayerState s, String taskId) {
        long reward = DailyQuests.claim(s, taskId);
        long bonus = 0;
        if (!s.dailyBonusGiven && s.dailyTasks.stream().allMatch(t -> t.claimed)) {
            bonus = DailyQuests.ALL_CLEAR_BONUS;
            s.dailyBonusGiven = true;
            s.gold = Num.add(s.gold, Num.of(bonus));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("id", taskId);
        m.put("reward", reward);
        m.put("allClearBonus", bonus);
        return m;
    }

    /**
     * 一键穿套装（第八批）：按成员 code 在背包里找评分最高的一件，逐件走 equip 换装
     * （被换下的回背包，满包折现与手动换装同口径）。缺件跳过，回执带实穿件数。
     */
    public Map<String, Object> equipSet(PlayerState s, String setId) {
        Sets.SetDef def = null;
        for (Sets.SetDef d : Sets.ALL) {
            if (d.id().equals(setId)) {
                def = d;
                break;
            }
        }
        if (def == null) {
            throw ApiException.badRequest("套装不存在：" + setId);
        }
        int equipped = 0;
        List<String> missing = new ArrayList<>();
        for (String code : def.members()) {
            // 已穿着该成员则跳过（无相位栏里的也算）
            boolean worn = s.equipped.values().stream()
                    .anyMatch(it -> code.equals(Sets.codeOf(it)));
            if (worn) {
                equipped++;
                continue;
            }
            int bestIdx = -1;
            double bestScore = -1;
            for (int i = 0; i < s.equipBag.size(); i++) {
                PlayerState.Item it = s.equipBag.get(i);
                if (code.equals(Sets.codeOf(it))) {
                    double sc = equipScore(it);
                    if (sc > bestScore) {
                        bestScore = sc;
                        bestIdx = i;
                    }
                }
            }
            if (bestIdx < 0) {
                missing.add(code);
                continue;
            }
            equip(s, bestIdx);
            equipped++;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("set", setId);
        m.put("equipped", equipped);
        m.put("missing", missing);
        return m;
    }

    /** 周常任务领取（第八批）：三任务全领触发全清奖励——50,000 金 + 随机宝石 ×2（每周一次）。 */
    public Map<String, Object> claimWeekly(PlayerState s, String taskId) {
        long reward = WeeklyQuests.claim(s, taskId);
        long bonus = 0;
        List<String> bonusGems = new ArrayList<>();
        if (!s.weeklyBonusGiven && s.weeklyTasks.stream().allMatch(t -> t.claimed)) {
            bonus = WeeklyQuests.ALL_CLEAR_BONUS;
            s.weeklyBonusGiven = true;
            s.gold = Num.add(s.gold, Num.of(bonus));
            for (int i = 0; i < WeeklyQuests.ALL_CLEAR_GEMS; i++) {
                String gem = GameEngine.randomGem();
                if (gem != null) {
                    s.materials.merge(gem, 1, Integer::sum);
                    bonusGems.add(gem);
                }
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("id", taskId);
        m.put("reward", reward);
        m.put("allClearBonus", bonus);
        m.put("allClearGems", bonusGems);
        return m;
    }

    /** 月常任务领取（第九批）：与周常同构，全清叠加 +300,000 金与随机宝石 ×3。 */
    public Map<String, Object> claimMonthly(PlayerState s, String taskId) {
        long reward = MonthlyQuests.claim(s, taskId);
        long bonus = 0;
        List<String> bonusGems = new ArrayList<>();
        if (!s.monthlyBonusGiven && s.monthlyTasks.stream().allMatch(t -> t.claimed)) {
            bonus = MonthlyQuests.ALL_CLEAR_BONUS;
            s.monthlyBonusGiven = true;
            s.gold = Num.add(s.gold, Num.of(bonus));
            for (int i = 0; i < MonthlyQuests.ALL_CLEAR_GEMS; i++) {
                String gem = GameEngine.randomGem();
                if (gem != null) {
                    s.materials.merge(gem, 1, Integer::sum);
                    bonusGems.add(gem);
                }
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("id", taskId);
        m.put("reward", reward);
        m.put("allClearBonus", bonus);
        m.put("allClearGems", bonusGems);
        return m;
    }

    /** 每日黑市购买（第七批）：货单/限购/折扣价规则在 BlackMarket，返回小回执。 */
    public Map<String, Object> buyBlackMarket(PlayerState s, String id) {
        long paid = BlackMarket.buy(s, id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("id", id);
        m.put("paid", paid);
        return m;
    }

    /** 成就里程碑档位（第八批）：累计已领成就数达标线与一次性奖金（每档只发一次，掩码落档）。 */
    static final long[] ACH_MILESTONE_TIERS = {15, 30, 45, 60};
    static final long[] ACH_MILESTONE_BONUS = {10_000, 30_000, 80_000, 200_000};

    /** 领取后结算里程碑：返回本次新发的里程碑奖金（0 = 未跨新档），奖金已入账。 */
    static long settleAchMilestone(PlayerState s) {
        long bonus = 0;
        int claimed = s.achClaimed.size();
        for (int i = 0; i < ACH_MILESTONE_TIERS.length; i++) {
            if (claimed >= ACH_MILESTONE_TIERS[i] && (s.stats.achMilestonePaid & (1 << i)) == 0) {
                s.stats.achMilestonePaid |= (1 << i);
                bonus += ACH_MILESTONE_BONUS[i];
            }
        }
        if (bonus > 0) {
            s.gold = Num.add(s.gold, Num.of(bonus));
        }
        return bonus;
    }

    /** 成就奖励领取：已解锁未领取才发金币（一成就一次），金额 Achievements.rewardOf 权威。 */
    public Map<String, Object> claimAchievement(PlayerState s, String id) {
        boolean known = Achievements.ALL.stream().anyMatch(d -> d.id().equals(id));
        if (!known) {
            throw ApiException.badRequest("未知成就：" + id);
        }
        if (!s.achievements.contains(id)) {
            throw ApiException.badRequest("成就尚未解锁");
        }
        if (!s.achClaimed.add(id)) {
            throw ApiException.badRequest("奖励已领取过");
        }
        long reward = Achievements.rewardOf(id);
        s.gold = Num.add(s.gold, Num.of(reward));
        long milestone = settleAchMilestone(s);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("id", id);
        m.put("reward", reward);
        m.put("milestoneBonus", milestone);
        return m;
    }

    /** 成就奖励一键全领：遍历全部成就，已解锁未领取的逐个入账（无可领时回报 0，不报错）。 */
    public Map<String, Object> claimAllAchievements(PlayerState s) {
        long total = 0;
        java.util.List<String> claimed = new java.util.ArrayList<>();
        for (Achievements.Def d : Achievements.ALL) {
            if (s.achievements.contains(d.id()) && s.achClaimed.add(d.id())) {
                long reward = Achievements.rewardOf(d.id());
                total += reward;
                claimed.add(d.id());
            }
        }
        s.gold = Num.add(s.gold, Num.of(total));
        long milestone = settleAchMilestone(s);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("claimedCount", claimed.size());
        m.put("claimedIds", claimed);
        m.put("totalReward", total);
        m.put("milestoneBonus", milestone);
        return m;
    }

    public Map<String, Object> sortBag(PlayerState s) {
        s.equipBag.sort((a, b) -> Double.compare(equipScore(b), equipScore(a)));
        return inventoryView(s);
    }

    /** 切换装备锁定：锁定件受保护，不被一键卖/丢弃/管护吞噬处置。 */
    public Map<String, Object> toggleLock(PlayerState s, int bagIdx) {
        if (bagIdx < 0 || bagIdx >= s.equipBag.size()) {
            throw ApiException.badRequest("背包索引越界");
        }
        PlayerState.Item it = s.equipBag.get(bagIdx);
        it.locked = !it.locked;
        Map<String, Object> m = inventoryView(s);
        m.put("locked", it.locked);
        return m;
    }

    // ==================== 逃跑 / Boss ====================

    /** 挂机策略设置：血线/蓝线（0~0.9，0=关闭）+ 解毒/逃跑线 + 燃烧/技能常驻开关。 */
    public Map<String, Object> setAutoLines(PlayerState s, Double hpLine, Double mpLine,
                                            Boolean autoCure, Double fleeLine,
                                            Boolean autoBurn, Boolean autoSkill,
                                            Boolean autoRebirth, Integer autoSellKeep,
                                            Boolean autoEquipBest, Boolean autoEat, Integer autoSmeltKeep,
                                            Boolean autoAdvance, Boolean autoBoss) {
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
            s.autoSellKeep = Math.max(0, Math.min(4, autoSellKeep));
        }
        if (autoEquipBest != null) {
            s.autoEquipBest = autoEquipBest;
        }
        if (autoEat != null) {
            s.autoEat = autoEat;
        }
        if (autoSmeltKeep != null) {
            s.autoSmeltKeep = Math.max(0, Math.min(4, autoSmeltKeep));
        }
        if (autoAdvance != null) {
            s.autoAdvance = autoAdvance;
        }
        if (autoBoss != null) {
            s.autoBoss = autoBoss;
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
        m.put("autoSmeltKeep", s.autoSmeltKeep);
        m.put("autoEquipBest", s.autoEquipBest);
        m.put("autoEat", s.autoEat);
        m.put("autoAdvance", s.autoAdvance);
        m.put("autoBoss", s.autoBoss);
        m.put("rerollPity", s.rerollPity);
        m.put("flurryHits", s.flurryHits);
        m.put("dailyTasks", DailyQuests.viewOf(s));
        m.put("blackMarket", BlackMarket.viewOf(s));
        return m;
    }

    /** 宝石镶嵌（第八批）：消耗背包材料里的宝石 1 颗，镶入背包装备（孔上限 GEM_SOCKET_MAX）。 */
    public Map<String, Object> socketGem(PlayerState s, int bagIdx, String gemCode) {
        if (bagIdx < 0 || bagIdx >= s.equipBag.size()) {
            throw ApiException.badRequest("背包索引越界");
        }
        GameConst.GemDef def = GameConst.GEMS.get(gemCode);
        if (def == null) {
            throw ApiException.badRequest("宝石不存在");
        }
        PlayerState.Item it = s.equipBag.get(bagIdx);
        if (it.gems == null) {
            it.gems = new java.util.ArrayList<>();
        }
        if (it.gems.size() >= GameConst.GEM_SOCKET_MAX) {
            throw ApiException.badRequest("镶嵌孔已满（" + GameConst.GEM_SOCKET_MAX + "）");
        }
        int have = s.materials.getOrDefault(gemCode, 0);
        if (have <= 0) {
            throw ApiException.badRequest("没有" + def.name() + "了");
        }
        s.materials.put(gemCode, have - 1);
        it.gems.add(gemCode);
        s.stats.gemsSocketed++;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("gem", gemCode);
        m.put("sockets", it.gems.size());
        return m;
    }

    /** 宝石拆卸（第八批）：花 200 金把一颗已镶宝石完好取回材料仓。 */
    public static final int UNSOCKET_GOLD = 200;

    public Map<String, Object> unsocketGem(PlayerState s, int bagIdx, int gemIdx) {
        if (bagIdx < 0 || bagIdx >= s.equipBag.size()) {
            throw ApiException.badRequest("背包索引越界");
        }
        PlayerState.Item it = s.equipBag.get(bagIdx);
        if (it.gems == null || gemIdx < 0 || gemIdx >= it.gems.size()) {
            throw ApiException.badRequest("宝石位越界");
        }
        if (Num.lt(s.gold, Num.of(UNSOCKET_GOLD))) {
            throw ApiException.badRequest("金币不足，需要 " + UNSOCKET_GOLD);
        }
        s.gold = Num.sub(s.gold, Num.of(UNSOCKET_GOLD));
        String code = it.gems.remove(gemIdx);
        s.materials.merge(code, 1, Integer::sum);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("gem", code);
        m.put("sockets", it.gems.size());
        return m;
    }

    /** 宝石融合（第八批）：3 颗同种一阶 → 1 颗璀璨二阶（属性 ×2.5）。 */
    public Map<String, Object> fuseGem(PlayerState s, String gemCode) {
        GameConst.GemDef def = GameConst.GEMS.get(gemCode);
        if (def == null || gemCode.endsWith("_2")) {
            throw ApiException.badRequest("只能融合一阶宝石");
        }
        int have = s.materials.getOrDefault(gemCode, 0);
        if (have < 3) {
            throw ApiException.badRequest("融合需要 3 颗" + def.name() + "（当前 " + have + "）");
        }
        s.materials.put(gemCode, have - 3);
        s.stats.gemsFused++;
        String t2 = gemCode + "_2";
        s.materials.merge(t2, 1, Integer::sum);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("gem", t2);
        m.put("left", have - 3);
        return m;
    }

    /** 领养宠物（第九批）：金币领养，首次领养自动出战。 */
    public Map<String, Object> adoptPet(PlayerState s, String petCode) {
        GameConst.PetDef def = GameConst.PETS.get(petCode);
        if (def == null) {
            throw ApiException.badRequest("宠物不存在：" + petCode);
        }
        if (s.petsOwned.contains(petCode)) {
            throw ApiException.badRequest("已经领养了" + def.name());
        }
        if (Num.lt(s.gold, Num.of(def.price()))) {
            throw ApiException.badRequest("金币不足，领养" + def.name() + "需要 " + def.price() + " 金币");
        }
        s.gold = Num.sub(s.gold, Num.of(def.price()));
        s.petsOwned.add(petCode);
        s.petLevels.putIfAbsent(petCode, 1);
        s.petExps.putIfAbsent(petCode, 0.0);
        if (s.pet == null) {
            s.pet = petCode;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("pet", petCode);
        m.put("gold", Num.plain(s.gold));
        return m;
    }

    /** 切换出战宠物（第九批）：只能切换到已领养的宠物。 */
    public Map<String, Object> switchPet(PlayerState s, String petCode) {
        if (!GameConst.PETS.containsKey(petCode)) {
            throw ApiException.badRequest("宠物不存在：" + petCode);
        }
        if (!s.petsOwned.contains(petCode)) {
            throw ApiException.badRequest("尚未领养这只宠物");
        }
        s.pet = petCode;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("pet", petCode);
        return m;
    }

    /** 喂食出战宠物（第九批）：金币换经验，连升多级一次结算；满级拒绝。 */
    public Map<String, Object> feedPet(PlayerState s) {
        if (s.pet == null) {
            throw ApiException.badRequest("还没有出战宠物");
        }
        int lv = s.petLevels.getOrDefault(s.pet, 1);
        if (lv >= GameConst.PET_MAX_LEVEL) {
            throw ApiException.badRequest("宠物已满级");
        }
        if (Num.lt(s.gold, Num.of(GameConst.PET_FEED_COST))) {
            throw ApiException.badRequest("金币不足，喂食需要 " + GameConst.PET_FEED_COST + " 金币");
        }
        s.gold = Num.sub(s.gold, Num.of(GameConst.PET_FEED_COST));
        s.stats.petFeeds++;
        int[] r = gainPetExp(s, GameConst.PET_FEED_EXP);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("pet", s.pet);
        m.put("level", r[0]);
        m.put("leveledUp", r[1]);
        m.put("gold", Num.plain(s.gold));
        return m;
    }

    /** 宠物获得经验并连升（第九批）：返回 {新等级, 本次升了几级}；满级经验不再累积。 */
    public static int[] gainPetExp(PlayerState s, double exp) {
        if (s.pet == null || exp <= 0) {
            return new int[]{s.pet == null ? 0 : s.petLevels.getOrDefault(s.pet, 1), 0};
        }
        int lv = s.petLevels.getOrDefault(s.pet, 1);
        if (lv >= GameConst.PET_MAX_LEVEL) {
            return new int[]{lv, 0};
        }
        double cur = s.petExps.getOrDefault(s.pet, 0.0) + exp;
        int ups = 0;
        while (lv < GameConst.PET_MAX_LEVEL && cur >= GameConst.petExpNeed(lv)) {
            cur -= GameConst.petExpNeed(lv);
            lv++;
            ups++;
        }
        if (lv >= GameConst.PET_MAX_LEVEL) {
            cur = 0;
        }
        s.petLevels.put(s.pet, lv);
        s.petExps.put(s.pet, cur);
        return new int[]{lv, ups};
    }

    /** 批量融合（第九批）：把所有一阶宝石按 3:1 全部融合成璀璨二阶，口径与逐颗融合一致。 */
    public Map<String, Object> fuseAllGems(PlayerState s) {
        int fused = 0;
        for (String id : GameConst.gemTier1Ids()) {
            int have = s.materials.getOrDefault(id, 0);
            int n = have / 3;
            if (n <= 0) {
                continue;
            }
            s.materials.put(id, have - n * 3);
            s.materials.merge(id + "_2", n, Integer::sum);
            fused += n;
            s.stats.gemsFused += n;
        }
        if (fused == 0) {
            throw ApiException.badRequest("没有可融合的宝石（每 3 颗同种一阶融 1 颗二阶）");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("fused", fused);
        return m;
    }

    /** 一键拆卸全部宝石（第九批）：背包内所有装备的宝石逐颗拆回（每颗 200 金，金币不足时停在当前颗）。 */
    public Map<String, Object> unsocketAll(PlayerState s) {
        int removed = 0;
        for (PlayerState.Item it : s.equipBag) {
            if (it.gems == null) {
                continue;
            }
            while (!it.gems.isEmpty()) {
                if (Num.lt(s.gold, Num.of(UNSOCKET_GOLD))) {
                    break;
                }
                s.gold = Num.sub(s.gold, Num.of(UNSOCKET_GOLD));
                String code = it.gems.remove(it.gems.size() - 1);
                s.materials.merge(code, 1, Integer::sum);
                removed++;
            }
        }
        if (removed == 0) {
            throw ApiException.badRequest("没有可拆卸的宝石（或金币不足）");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("removed", removed);
        m.put("gold", Num.plain(s.gold));
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
        s.stats.enchants++;
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
    /**
     * 加点（大数口径）：count 为任意大整数（字符串传参，无单次上限——
     * 旧 int 口径把「全部」卡在 20 亿，玩家余额更高时梭哈不完，2026-11-04 按反馈改）。
     * 小数部分向下取整（自由点只有整数来源），取整后 <1 拒绝。
     */
    public Map<String, Object> allocateStatPoint(PlayerState s, String stat, java.math.BigDecimal count) {
        if (!"statlord".equals(s.talent)) {
            throw ApiException.badRequest("未觉醒「无限属性点」天赋，无法加点");
        }
        java.math.BigDecimal step = GameConst.STAT_POINT_STEP.get(stat);
        if (step == null) {
            throw ApiException.badRequest("该属性不可加点：" + stat);
        }
        if (count == null) {
            throw ApiException.badRequest("加点数量至少 1");
        }
        java.math.BigDecimal n = count.setScale(0, java.math.RoundingMode.FLOOR);
        if (n.signum() < 1) {
            throw ApiException.badRequest("加点数量至少 1");
        }
        if (Num.lt(s.freePoints, n)) {
            throw ApiException.badRequest("自由属性点不足（当前 " + Num.plain(s.freePoints) + "）");
        }
        s.statPoints.merge(stat, step.multiply(n), java.math.BigDecimal::add);
        s.freePoints = Num.sub(s.freePoints, n);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("stat", stat);
        m.put("count", Num.plain(n));
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
        // Boss 二阶段（第七批）：前端血条狂怒标识
        if (s.mob.boss && s.mob.phase2) {
            m.put("phase2", true);
        }
        // 迟缓咒（第七批）：怪物减益剩余秒数下发，前端名字后缀 🐌
        if (s.mob.slowT > 0) {
            m.put("slowed", Math.round(s.mob.slowT * 10) / 10.0);
        }
        // 怪物词条（v3.15）：下发完整展示信息，前端只渲染徽章不查表
        if (s.mob.trait != null) {
            GameConst.TraitDef td = GameConst.MOB_TRAITS.get(s.mob.trait);
            if (td != null) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("id", td.id());
                t.put("name", td.name());
                t.put("emoji", td.emoji());
                t.put("desc", td.desc());
                m.put("trait", t);
            }
        }
        return m;
    }

    /** 视图评分附件：与 bag 数组同序的评分表 + 已穿装备按部位评分（前端做强弱对比，不污染 Item 持久化）。 */
    static void attachScores(Map<String, Object> m, PlayerState s, List<PlayerState.Item> bagView) {
        List<Double> scores = new ArrayList<>();
        for (PlayerState.Item it : bagView) {
            scores.add(equipScore(it));
        }
        m.put("bagScores", scores);
        // 背包可售总价预览（第七批）：未锁定件按 sellPrice 同口径汇总（锁定件卖不掉，不计）
        java.math.BigDecimal sellTotal = java.math.BigDecimal.ZERO;
        for (PlayerState.Item it : bagView) {
            if (!it.locked) {
                sellTotal = sellTotal.add(EquipFactory.sellPrice(it));
            }
        }
        m.put("bagSellTotal", Num.plain(sellTotal));
        Map<String, Double> eq = new LinkedHashMap<>();
        for (Map.Entry<String, PlayerState.Item> e : s.equipped.entrySet()) {
            if (e.getValue() != null) {
                eq.put(e.getKey(), equipScore(e.getValue()));
            }
        }
        m.put("equippedScores", eq);
    }

    /** 套装进度视图（第七批）：每套已穿件数、两档加成文案与激活状态，前端套装面板直接渲染。 */
    static java.util.List<Map<String, Object>> setsView(PlayerState s) {
        Map<String, Integer> counts = Sets.wornCounts(s);
        java.util.List<Map<String, Object>> out = new ArrayList<>();
        for (Sets.SetDef d : Sets.ALL) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.id());
            m.put("name", d.name());
            m.put("emoji", d.emoji());
            m.put("members", d.members());
            int worn = counts.getOrDefault(d.id(), 0);
            m.put("worn", worn);
            m.put("total", d.members().size());
            m.put("bonus2", Sets.bonusText(d.bonus2()));
            m.put("bonus4", Sets.bonusText(d.bonus4()));
            m.put("active2", worn >= 2);
            m.put("active4", worn >= 4);
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> inventoryView(PlayerState s) {
        Map<String, Object> m = new LinkedHashMap<>();
        // 与 fullView 同口径截断(v3.13):操作响应携带背包视图,同样防巨包
        int bagTotal = s.equipBag.size();
        List<PlayerState.Item> bagView = bagTotal > GameConst.BAG_VIEW_LIMIT
                ? new ArrayList<>(s.equipBag.subList(0, GameConst.BAG_VIEW_LIMIT))
                : s.equipBag;
        m.put("bag", bagView);
        attachScores(m, s, bagView);
        m.put("bagTotal", bagTotal);
        m.put("equipped", s.equipped);
        m.put("gold", Num.plain(s.gold));
        m.put("bagMax", bagCap(s));
        return m;
    }

    /** 宠物视图（第九批）：出战宠物 + 等级/经验/升级所需 + 已领养列表（挂 fullView 本体，轮询可见）。 */
    public static Map<String, Object> petView(PlayerState s) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("active", s.pet);
        v.put("owned", new ArrayList<>(s.petsOwned));
        if (s.pet != null && GameConst.PETS.containsKey(s.pet)) {
            int lv = s.petLevels.getOrDefault(s.pet, 1);
            v.put("level", lv);
            v.put("exp", s.petExps.getOrDefault(s.pet, 0.0));
            v.put("expNeed", lv >= GameConst.PET_MAX_LEVEL ? 0 : GameConst.petExpNeed(lv));
            v.put("maxLevel", GameConst.PET_MAX_LEVEL);
        }
        return v;
    }

    public Map<String, Object> fullView(PlayerState s) {
        PanelCalculator.Panel p = PanelCalculator.of(s);
        Map<String, Object> m = new LinkedHashMap<>();
        // 过期响应守卫（第七批）：视图构建时刻（服务端单时钟），前端 applyView 丢弃比已应用更旧的帧
        m.put("serverTime", System.currentTimeMillis());
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
        Map<String, Object> mobV = mobView(s);
        // 击杀预估：按当前面板每拳期望伤害推算剩余拳数/秒数（木桩无限血不估）
        if (mobV != null && s.mob != null && s.mob.maxHP != null) {
            BigDecimal perHit = GameEngine.expectedPerHit(s, p, s.mob);
            if (Num.gt(perHit, Num.ZERO)) {
                long est = s.mob.curHP.divide(perHit, 0, java.math.RoundingMode.CEILING).longValue();
                mobV.put("estPunches", est);
                double aspd = p.ASPD().doubleValue();
                if (aspd > 0) {
                    mobV.put("estSeconds", Math.round(est / aspd * 10) / 10.0);
                }
            }
        }
        m.put("mob", mobV);
        m.put("buffs", s.buffs);
        m.put("cds", s.cds);
        m.put("passives", s.passives);
        m.put("potions", s.potions);
        m.put("equipped", s.equipped);
        m.put("sets", setsView(s));
        // 每日任务/黑市/洗炼保底（第七批实测修复）：此前误挂在 autoconfig 回执上，
        // fullView 轮询帧拿不到——前端每日卡片与黑市永远刷不出来，现归位到主视图
        DailyQuests.ensure(s);
        WeeklyQuests.ensure(s);
        m.put("dailyTasks", DailyQuests.viewOf(s));
        m.put("weeklyTasks", WeeklyQuests.viewOf(s));
        m.put("monthlyTasks", MonthlyQuests.viewOf(s));
        // 连环掌剩余次数（第八批收官实测修复）：此前误挂在 setAutoLines 回执上，
        // fullView 没有——前端 buff 条的 🤲 计数芯片永远读不到（与第七批 dailyTasks 同类事故）
        m.put("flurryHits", s.flurryHits);
        m.put("pet", petView(s));
        m.put("blackMarket", BlackMarket.viewOf(s));
        m.put("rerollPity", s.rerollPity);
        // v3.13 背包截断防复发:此前全量下发 3.3 万件=13.9MB JSON,前端每秒解析+渲染
        // 直接卡死。视图只带前 200 件(强化/洗炼/卖出按索引操作,截断段不可及,
        // 服务端管护会先把背包清到安全规模),bagTotal 让前端如实显示总数。
        int bagTotal = s.equipBag.size();
        List<PlayerState.Item> bagView = bagTotal > GameConst.BAG_VIEW_LIMIT
                ? new ArrayList<>(s.equipBag.subList(0, GameConst.BAG_VIEW_LIMIT))
                : s.equipBag;
        m.put("bag", bagView);
        attachScores(m, s, bagView);
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
        st.put("totalDamage", Num.plain(s.stats.totalDamage));
        st.put("bestHit", Num.plain(s.stats.bestHit));
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
        m.put("autoSmeltKeep", s.autoSmeltKeep);
        m.put("autoEquipBest", s.autoEquipBest);
        m.put("autoEat", s.autoEat);
        m.put("autoAdvance", s.autoAdvance);
        m.put("autoBoss", s.autoBoss);
        m.put("recentKills", s.recentKills == null ? new ArrayList<>() : new ArrayList<>(s.recentKills));
        m.put("events", s.events == null ? new ArrayList<>() : new ArrayList<>(s.events));
        m.put("bossKills", new ArrayList<>(s.bossKills));
        m.put("codex", s.codex);
        m.put("achievements", new ArrayList<>(s.achievements));
        m.put("achClaimed", new ArrayList<>(s.achClaimed));
        Map<String, Object> achRewards = new LinkedHashMap<>();
        for (Achievements.Def d : Achievements.ALL) {
            achRewards.put(d.id(), Achievements.rewardOf(d.id()));
        }
        m.put("achRewards", achRewards);
        // 成就进度（未解锁的可量化成就 → [当前, 目标]，前端画进度条）
        Map<String, Object> achProgress = new LinkedHashMap<>();
        for (Achievements.Def d : Achievements.ALL) {
            if (s.achievements.contains(d.id())) {
                continue;
            }
            long[] pr = Achievements.progressOf(s, d.id(), p);
            if (pr != null) {
                achProgress.put(d.id(), List.of(pr[0], pr[1]));
            }
        }
        m.put("achProgress", achProgress);
        // 材料库存（管家面板总览用：六区材料 + 图纸计数，原始 code 由前端映射名称）
        m.put("materials", s.materials == null ? Map.of() : new LinkedHashMap<>(s.materials));
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
