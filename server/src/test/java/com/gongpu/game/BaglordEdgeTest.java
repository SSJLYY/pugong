package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.ApiException;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import com.gongpu.state.StateCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 无限装备栏天赋(baglord)边界审查补充测试:
 * 未开放下标卸下、背满卸下、背包索引操作够不到已穿 extra 装备、
 * 无相位重复部位叠加、BAG_CAP 生效、跨天/回档发放语义、autoEquip 行为、转生保留。
 * <p>只读审查产出:不修改 src/main,仅固化当前行为并标注已发现缺陷。</p>
 */
class BaglordEdgeTest {

    private static int SEQ = 0;

    private final GameService game = new GameService();

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState.Item item(String slot, String statKey, String statVal) {
        PlayerState.Item it = new PlayerState.Item();
        it.uid = 1;
        it.name = "测试" + slot + "_" + (++SEQ);   // 唯一名:吞噬逻辑按名字合并,测试需显式控制同名
        it.emoji = "🗡️";
        it.slot = slot;
        it.quality = 1;
        it.base.put(statKey, new BigDecimal(statVal));
        return it;
    }

    private PlayerState baglord(int slots) {
        PlayerState s = NewState.create();
        s.talent = "baglord";
        s.extraSlots = slots;
        s.lastSlotDay = LocalDate.now().toString();
        return s;
    }

    // ==================== 卸下边界 ====================

    @Test
    void unequipUnknownExtraSlotRejected() {
        PlayerState s = baglord(3);
        s.equipBag.add(item("weapon", "PATK", "10"));
        game.equipExtra(s, 0);
        // 未开放/不存在的 extra 下标:必须 400,且不能误删任何东西
        ApiException ex = assertThrows(ApiException.class, () -> game.unequip(s, "extra_99"),
                "未开放的下标应拒绝");
        assertTrue(ex.getMessage().contains("没有装备"));
        assertNotNull(s.equipped.get("extra_0"), "已有 extra 装备不受影响");
        // 非法格式 slot 同样只走"部位为空"分支,不会 NPE
        assertThrows(ApiException.class, () -> game.unequip(s, "extra_-1"));
        assertThrows(ApiException.class, () -> game.unequip(s, "weaponX"));
    }

    /**
     * 缺陷修复回归(审查发现 #1,严重):unequip 曾先 remove 后判背满,
     * 背包满时物品两边都不在,被 run() 的 finally persist 落库 → 装备永久丢失。
     * 已修复为"先判容量再移除":拒绝时物品必须原样留在装备栏。
     */
    @Test
    void unequipWithFullBagKeepsItemSafe() {
        PlayerState s = baglord(1);
        // extra_0 穿入一件,然后把背包塞满(40/40)
        s.equipBag.add(item("weapon", "PATK", "10"));
        game.equipExtra(s, 0);
        while (s.equipBag.size() < GameConst.BAG_MAX) {
            s.equipBag.add(item("boots", "SPD", "1"));
        }
        assertEquals(GameConst.BAG_MAX, s.equipBag.size());
        ApiException ex = assertThrows(ApiException.class, () -> game.unequip(s, "extra_0"),
                "背包满时卸下应被拒绝");
        assertTrue(ex.getMessage().contains("背包已满"));
        // 修复断言:拒绝后物品必须原样还在装备栏(不丢)
        assertNotNull(s.equipped.get("extra_0"), "拒绝卸下后物品必须还在装备栏");
        assertEquals(GameConst.BAG_MAX, s.equipBag.size(), "背包不应变化");
        assertTrue(s.equipBag.stream().noneMatch(it -> "测试weapon".equals(it.name)),
                "武器不应进背包(背包满,它应留在 extra_0)");
    }

    // ==================== 背包索引操作够不到已穿装备 ====================

    @Test
    void bagIndexOpsCannotReachEquippedExtraItems() {
        PlayerState s = baglord(1);
        s.equipBag.add(item("weapon", "PATK", "10"));
        game.equipExtra(s, 0);   // 背包清空,武器在 extra_0
        assertTrue(s.equipBag.isEmpty());

        // 卖出/强化/丢弃/洗炼/附魔全部按背包索引走:空背包下任何索引都越界,
        // 因此不存在绕过 unequip 直接对已穿 extra 装备操作/变现的路径
        assertThrows(ApiException.class, () -> game.sell(s, 0));
        assertThrows(ApiException.class, () -> game.enhance(s, 0));
        assertThrows(ApiException.class, () -> game.discard(s, 0));
        assertThrows(ApiException.class, () -> game.reroll(s, 0, -1));
        assertThrows(ApiException.class, () -> game.enchant(s, "scroll_fire", 0));

        PlayerState.Item it = s.equipped.get("extra_0");
        assertNotNull(it, "extra 装备仍在栏上");
        assertEquals(0, it.enhance, "未被强化改动");
        assertTrue(it.affixes.isEmpty(), "未被附魔/洗炼改动");
        // 负索引同样越界,无下标回绕
        assertThrows(ApiException.class, () -> game.sell(s, -1));
    }

    // ==================== 无相位:重复部位/词条/BAG_CAP ====================

    @Test
    void extraSlotStacksDuplicateWeapon() {
        PlayerState s = baglord(2);
        s.equipBag.add(item("weapon", "PATK", "30"));
        game.equip(s, 0);        // 原生 weapon
        double patkNative = PanelCalculator.of(s).PATK().doubleValue();
        s.equipBag.add(item("weapon", "PATK", "20"));
        game.equipExtra(s, 0);   // 第二把武器进 extra_0
        double patk = PanelCalculator.of(s).PATK().doubleValue();
        assertEquals(patkNative + 20, patk, 1e-9, "原生+extra 两把武器 PATK 应全部叠加");
    }

    @Test
    void extraSlotBeltExpandsBagCap() {
        PlayerState s = baglord(1);
        assertEquals(GameConst.BAG_MAX, GameService.bagCap(s));
        PlayerState.Item belt = new PlayerState.Item();
        belt.uid = 9;
        belt.name = "负重腰带";
        belt.slot = "offhand";   // it.slot 不参与 bagCap 计算
        belt.base.put("BAG_CAP", new BigDecimal("20"));
        s.equipped.put("extra_0", belt);
        assertEquals(GameConst.BAG_MAX + 20, GameService.bagCap(s),
                "extra 栏装备的 BAG_CAP 也应生效(全部生效语义)");
    }

    @Test
    void equipExtraRefillsFirstEmptySlot() {
        PlayerState s = baglord(3);
        s.equipBag.add(item("weapon", "PATK", "10"));
        game.equipExtra(s, 0);   // extra_0
        s.equipBag.add(item("chest", "PDEF", "5"));
        game.equipExtra(s, 0);   // extra_1
        game.unequip(s, "extra_0");   // 中间空出,背包=[weapon]
        s.equipBag.add(item("boots", "SPD", "3"));   // 背包=[weapon, boots]
        game.equipExtra(s, 0);   // 先回填最靠前的空栏 extra_0
        game.equipExtra(s, 0);   // 再填 extra_2
        assertNotNull(s.equipped.get("extra_0"), "应优先填最靠前的空栏");
        assertNotNull(s.equipped.get("extra_1"));
        assertNotNull(s.equipped.get("extra_2"));
        assertTrue(s.equipBag.isEmpty());
    }

    // ==================== 每日发放:跨天/回档语义 ====================

    /**
     * 自然日发放语义:23:59:50 结算(+1, 日 D)后 00:00:01 再结算(+1, 日 D+1)
     * = 两个自然日各 +1,共 +2,不算重复;同一自然日内反复 persist 绝不重复发。
     */
    // v3.12:baglord 觉醒即无限(openExtraSlots=已用无相位栏数+1),
    // 旧的"每日发放/跨天补发/回档补发"语义随 grantDailySlot 一并移除,无对应场景可测。

    /** [legacy] extraSlots/lastSlotDay 字段仍随存档 JSON 持久化(旧档兼容),但不再参与任何计算。 */
    @Test
    void stateRoundTripPreservesLegacySlotFields() {
        PlayerState s = baglord(7);
        String json = StateCodec.encode(s);
        PlayerState back = StateCodec.decode(json);
        assertEquals(7, back.extraSlots, "legacy extraSlots 字段应持久化");
        assertEquals(1, GameService.openExtraSlots(back), "无限模型:开放数=已用 0+1,与旧积累值无关");
    }

    @Test
    void legacyNegativeExtraSlotsIgnored() {
        PlayerState s = NewState.create();
        s.talent = "baglord";
        s.extraSlots = -3;   // 只可能来自损坏档;无限模型直接忽略该字段
        assertEquals(1, GameService.openExtraSlots(s), "旧字段负值不影响无限模型(已用 0+1)");
        s.equipBag.add(item("weapon", "PATK", "1"));
        game.equipExtra(s, 0);   // 加穿正常成功
        assertNotNull(s.equipped.get("extra_0").name);
    }

    // ==================== autoEquip 行为 ====================

    /**
     * 行为变化(审查发现 #2,建议加评分下限):autoEquip 第二阶段把背包剩余
     * 全部塞进空 extra 栏——低分垃圾也会被穿上,玩家想留件卖钱的意图被打破。
     * 本测试固化当前行为;若加评分下限/开关,应改断言垃圾留在背包。
     */
    @Test
    void autoEquipWearsAllJunkIntoExtras_DocumentBehavior() {
        PlayerState s = baglord(1);
        s.equipBag.add(item("weapon", "PATK", "50"));   // 强装备 → 原生 weapon
        PlayerState.Item junk = item("weapon", "PATK", "1");
        junk.quality = 0;
        s.equipBag.add(junk);                            // 留着卖钱的垃圾
        game.autoEquip(s);
        assertNotNull(s.equipped.get("weapon"));
        assertNotNull(s.equipped.get("extra_0"), "当前行为:垃圾也被自动穿进 extra 栏");
        assertEquals(1.0, s.equipped.get("extra_0").base.get("PATK").doubleValue());
        assertTrue(s.equipBag.isEmpty(), "当前行为:背包被清空,垃圾无法直接一键卖出");
    }

    /** 无限模型:换下的旧装备自动穿入无相位栏(属性叠加保留,不占背包),extra 已穿件不被扰动。 */
    @Test
    void autoEquipSwappedOutGoesToExtras() {
        PlayerState s = baglord(1);
        s.equipped.put("weapon", item("weapon", "PATK", "10"));   // 原生旧武器
        PlayerState.Item junk = item("necklace", "MDEF", "1");
        s.equipped.put("extra_0", junk);
        s.equipBag.add(item("weapon", "PATK", "30"));              // 更强新武器
        s.equipBag.add(item("offhand", "PDEF", "5"));
        Map<String, Object> r = game.autoEquip(s);
        assertEquals(30.0, s.equipped.get("weapon").base.get("PATK").doubleValue(), "原生栏应换新");
        assertSame(junk, s.equipped.get("extra_0"), "已穿的 extra 件不被扰动");
        assertNotNull(s.equipped.get("offhand"), "空原生栏照常穿上 offhand");
        assertTrue(s.equipBag.isEmpty(), "换下的旧武器应穿入新开的 extra 栏(无限),不占背包");
        assertEquals(10.0, s.equipped.get("extra_1").base.get("PATK").doubleValue(),
                "被换下的旧武器在 extra_1(属性仍叠加,想卖先卸下)");
        assertEquals(3, ((Number) r.get("equippedCount")).intValue(),
                "weapon 换新 + offhand 新穿 + 旧武器入 extra 各计 1 次");
    }

    // ==================== 同名吞噬合并(v3.12 玩家反馈) ====================

    @Test
    void equipExtraSameNameDevoursAndStacks() {
        // 相同装备只保留一件,其余被吞噬:base 求和,同名词条 val 叠加,不新占格子
        PlayerState s = baglord(1);
        PlayerState.Item first = item("weapon", "PATK", "10");
        first.name = "铁剑";   // 显式同名:吞噬按名字匹配
        PlayerState.AffixInst hawk1 = new PlayerState.AffixInst();
        hawk1.name = "鹰眼";
        hawk1.key = "ACC";
        hawk1.type = "flat";
        hawk1.val = new BigDecimal("0.05");
        first.affixes.add(hawk1);
        s.equipBag.add(first);
        game.equipExtra(s, 0);
        assertEquals(1, s.equipped.keySet().stream().filter(k -> k.startsWith("extra_")).count(),
                "首件占一格");

        PlayerState.Item dup = item("weapon", "PATK", "20");
        dup.name = "铁剑";
        PlayerState.AffixInst hawk2 = new PlayerState.AffixInst();
        hawk2.name = "鹰眼";
        hawk2.key = "ACC";
        hawk2.type = "flat";
        hawk2.val = new BigDecimal("0.05");
        dup.affixes.add(hawk2);
        s.equipBag.add(dup);
        game.equipExtra(s, 0);

        assertEquals(1, s.equipped.keySet().stream().filter(k -> k.startsWith("extra_")).count(),
                "同名吞噬不新占格子");
        PlayerState.Item worn = s.equipped.get("extra_0");
        assertNotNull(worn);
        assertEquals(0, worn.base.get("PATK").compareTo(new BigDecimal("30")), "同名吞噬 base 求和(10+20)");
        assertTrue(worn.affixes.stream().anyMatch(a -> "鹰眼".equals(a.name)
                && a.val.compareTo(new BigDecimal("0.10")) == 0), "同名词条 val 叠加(0.05+0.05)");
        assertTrue(s.equipBag.isEmpty(), "背包件被消化");
    }

    @Test
    void autoEquipCompactsLegacyDuplicateExtras() {
        // v3.12 前可穿多件同名:一次「穿最强」应把历史膨胀合并干净
        PlayerState s = baglord(1);
        PlayerState.Item a = item("weapon", "PATK", "10");
        a.name = "铁剑";
        PlayerState.Item b = item("weapon", "PATK", "15");
        b.name = "铁剑";
        s.equipped.put("extra_0", a);
        s.equipped.put("extra_1", b);
        game.autoEquip(s);
        long extras = s.equipped.keySet().stream().filter(k -> k.startsWith("extra_")).count();
        assertEquals(1, extras, "历史同名已穿件合并成一件");
        assertEquals(0, s.equipped.get("extra_0").base.get("PATK").compareTo(new BigDecimal("25")),
                "两件 PATK 累加(10+15)");
    }

    @Test
    void autoEquipDevoursDuplicateBagItems() {
        // 穿最强把背包剩余全部穿入时,同名件互相吞噬,不再制造重复已穿件
        PlayerState s = baglord(1);
        s.equipped.put("weapon", item("weapon", "PATK", "100"));   // 原生最强,不会被换
        PlayerState.Item boot = item("boots", "SPD", "50");
        boot.name = "皮靴";
        s.equipped.put("boots", boot);                              // 原生靴位预占异名靴 → 草鞋走 extra
        for (int i = 0; i < 3; i++) {
            PlayerState.Item dup = item("boots", "SPD", "5");
            dup.name = "草鞋";
            s.equipBag.add(dup);
        }
        game.autoEquip(s);
        long worn = s.equipped.keySet().stream().filter(k -> k.startsWith("extra_")).count();
        assertEquals(1, worn, "三双同名草鞋只占一格");
        assertEquals(0, s.equipBag.size());
        assertEquals(0, s.equipped.get("extra_0").base.get("SPD").compareTo(new BigDecimal("15")),
                "三双 SPD 累加(5+5+5)");
    }

    @Test
    void devourDoesNotStackBulletMp() {
        // 吞噬把「每发耗蓝」也累加会让合并枪耗蓝膨胀超蓝上限=永久哑火(玩家档实锤 265>80)
        PlayerState s = baglord(1);
        PlayerState.Item g1 = item("bow", "MATK", "100");
        g1.name = "魔弹加特林";
        g1.base.put("BULLET_MP", new BigDecimal("1"));
        s.equipBag.add(g1);
        game.equipExtra(s, 0);
        PlayerState.Item g2 = item("bow", "MATK", "100");
        g2.name = "魔弹加特林";
        g2.base.put("BULLET_MP", new BigDecimal("1"));
        s.equipBag.add(g2);
        game.equipExtra(s, 0);
        assertEquals(new BigDecimal("1"), s.equipped.get("extra_0").base.get("BULLET_MP"),
                "吞噬后耗蓝保留主件原值,不得累加");
        assertEquals(0, s.equipped.get("extra_0").base.get("MATK").compareTo(new BigDecimal("200")),
                "增益属性照常求和");
    }

    @Test
    void devourDoesNotTouchNativeRingPairs() {
        // 原生 ring1/ring2 双戒是合法配置:吞噬只清理 extra 栏,不动原生槽
        PlayerState s = baglord(1);
        PlayerState.Item r1 = item("ring1", "PATK", "1");
        r1.name = "铜戒";
        PlayerState.Item r2 = item("ring2", "PATK", "1");
        r2.name = "铜戒";
        s.equipped.put("ring1", r1);
        s.equipped.put("ring2", r2);
        game.autoEquip(s);
        assertNotNull(s.equipped.get("ring1"));
        assertNotNull(s.equipped.get("ring2"), "原生双戒不被吞噬");
    }

    // ==================== 转生 ====================

    /** 转生清空已穿(含 extra_N)但天赋跨世保留:无相位栏随穿随开,下一世照常无限。 */
    @Test
    void rebirthClearsEquippedTalentKeepsUnlimited() {
        PlayerState s = baglord(3);
        s.equipBag.add(item("weapon", "PATK", "99"));
        game.equipExtra(s, 0);
        assertEquals(1, s.equipped.size());
        s.fixed.ASPD = new BigDecimal("1e45");   // 满足转生攻速门槛 1e44
        game.rebirth(s, "PATK");
        assertTrue(s.equipped.isEmpty(), "转生应清空全部已穿(含 extra 栏)");
        assertEquals(0, s.equipBag.size());
        assertEquals(1, GameService.openExtraSlots(s), "清空后开放数=已用 0+1,加穿即再扩展(无限语义)");
        assertEquals("baglord", s.talent, "天赋跨世保留");
        assertEquals(1, s.gen);
    }
}
