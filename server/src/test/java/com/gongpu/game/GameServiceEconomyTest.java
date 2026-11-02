package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.ApiException;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 经济路径纯逻辑测试(GameService,无 DB 依赖):
 * buyPotion 动态定价(+2%/个封顶 2 倍/金币不足 400)、sell 按品质卖价、
 * enhance 花费 1.5^n 递增与 +10 上限、equip 换装回背包/背包满自动卖旧装。
 * <p>价格全部为可精确表示的浮点(1.0/1.2/2.0/1.5^n),断言用 compareTo 不吃精度亏。</p>
 */
class GameServiceEconomyTest {

    private final GameService game = new GameService();

    @BeforeAll
    static void initGameData() {
        // null 仓库 → load() 失败回落空快照 → 商品表回落 GameConst.POTIONS(hp_s=50 金)
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState.Item item(String slot, String statKey, String statVal, int quality) {
        PlayerState.Item it = new PlayerState.Item();
        it.uid = 1;
        it.name = "测试" + slot;
        it.emoji = "🗡️";
        it.slot = slot;
        it.quality = quality;
        it.base.put(statKey, new BigDecimal(statVal));
        return it;
    }

    // ==================== buyPotion 动态定价 ====================

    /** 首次购买无溢价:priceMul=1.0,扣款 = 标价 × 数量(hp_s 标价 50)。 */
    @Test
    void buyPotionFirstPurchaseChargesBasePrice() {
        PlayerState s = NewState.create();
        s.gold = Num.of(60);
        s.potions.clear();
        s.potionBought.clear();                 // 摘掉新手礼包(hp_s=5)干扰,聚焦定价逻辑
        Map<String, Object> r = game.buyPotion(s, "hp_s", 1);
        assertEquals(0, s.gold.compareTo(Num.of(10)), "首购应恰好扣标价 50,剩 10");
        assertEquals(1.0, (Double) r.get("priceMul"), 1e-9, "首购无溢价");
        assertEquals(1, s.potions.get("hp_s"), "到货 1 瓶");
        assertEquals(1, s.potionBought.get("hp_s"), "购买计数同步 +1(动态定价依据)");
    }

    /** 批量购买推高后续单价:一次买 10 个后,单价 = 1+10×2% = ×1.2(50→60),不封顶区间内线性。 */
    @Test
    void buyPotionPriceRisesTwoPercentPerUnitBought() {
        PlayerState s = NewState.create();
        s.gold = Num.of(1000);
        s.potions.clear();
        s.potionBought.clear();                 // 摘掉新手礼包干扰
        game.buyPotion(s, "hp_s", 10);           // 首单 ×1.0 → 扣 500
        assertEquals(0, s.gold.compareTo(Num.of(500)), "首单 10 瓶 ×1.0 应扣 500");
        assertEquals(10, s.potionBought.get("hp_s"), "购买计数按个数累计");
        Map<String, Object> r = game.buyPotion(s, "hp_s", 1);   // 已购 10 → ×1.2 → 60
        assertEquals(0, s.gold.compareTo(Num.of(440)), "第 11 瓶应扣 50×1.2=60,剩 440");
        assertEquals(1.2, (Double) r.get("priceMul"), 1e-9, "已购 10 个后单价 ×1.2");
        assertEquals(11, s.potions.get("hp_s"));
    }

    /** 涨价封顶 2 倍:已购 50 个(×2.0 恰好到顶)与已购 80 个同价,均为标价×2。 */
    @Test
    void buyPotionPriceCappedAtDoubleBase() {
        PlayerState s = NewState.create();
        s.gold = Num.of(1000);
        s.potionBought.put("hp_s", 50);          // 1+50×0.02 = 2.0 恰好到顶
        Map<String, Object> r = game.buyPotion(s, "hp_s", 1);
        assertEquals(0, s.gold.compareTo(Num.of(900)), "封顶价 = 50×2 = 100");
        assertEquals(2.0, (Double) r.get("priceMul"), 1e-9, "已购 50 个应恰好 ×2.0");
        s.potionBought.put("hp_s", 80);          // 未封顶应为 ×2.6,必须被钳回 ×2.0
        game.buyPotion(s, "hp_s", 1);
        assertEquals(0, s.gold.compareTo(Num.of(800)), "超顶后仍按 ×2 封顶扣 100");
        assertEquals(2.0, (Double) game.buyPotion(s, "hp_s", 1).get("priceMul"), 1e-9,
                "封顶后 priceMul 恒为 2.0");
    }

    /** 服务端权威:金币不足 / 数量越界 / 未知药剂 → 400,且状态零改动(不扣钱不到货)。 */
    @Test
    void buyPotionRejectsInsufficientGoldAndIllegalCount() {
        PlayerState s = NewState.create();
        s.gold = Num.of(10);                     // 不够一瓶(50)
        s.potions.clear();
        s.potionBought.clear();                 // 摘掉新手礼包干扰
        ApiException poor = assertThrows(ApiException.class, () -> game.buyPotion(s, "hp_s", 1),
                "金币不足必须 400");
        assertEquals(400, poor.getStatus());
        assertTrue(poor.getMessage().contains("金币不足"));
        assertEquals(0, s.gold.compareTo(Num.of(10)), "失败购买不得扣钱");
        assertNull(s.potions.get("hp_s"), "失败购买不得发货");
        assertNull(s.potionBought.get("hp_s"), "失败购买不得计入涨价计数");

        assertThrows(ApiException.class, () -> game.buyPotion(s, "hp_s", 0), "数量 0 拒绝");
        assertThrows(ApiException.class, () -> game.buyPotion(s, "hp_s", 100), "数量 >99 拒绝");
        assertThrows(ApiException.class, () -> game.buyPotion(s, "no_such", 1), "未知药剂拒绝");
        assertEquals(0, s.gold.compareTo(Num.of(10)), "全部失败后金币原封不动");
    }

    // ==================== sell 卖价 ====================

    /** 卖价按品质递增:20×(品质+1) → 白/精良/稀有/史诗 = 20/40/60/80;强化再 ×(1+0.5/级)。 */
    @Test
    void sellPaysByQualityAndEnhance() {
        PlayerState s = NewState.create();
        for (int q = 0; q <= 3; q++) {
            s.equipBag.add(item("weapon", "PATK", "5", q));
        }
        BigDecimal expected = Num.ZERO;
        for (int q = 0; q <= 3; q++) {
            Map<String, Object> r = game.sell(s, 0);   // 每次卖最前(品质 q 递增入包)
            expected = Num.add(expected, Num.of(20L * (q + 1)));
            assertEquals(Num.plain(Num.of(20L * (q + 1))), r.get("soldFor"),
                    "品质 " + q + " 卖价应为 " + 20 * (q + 1));
        }
        assertEquals(0, s.gold.compareTo(expected), "四件合计 200 金");
        // 强化加价:白装 +2 级 → 20×(1+2×0.5) = 40
        PlayerState.Item enh2 = item("boots", "SPD", "1", 0);
        enh2.enhance = 2;
        s.equipBag.add(enh2);
        Map<String, Object> r = game.sell(s, 0);
        assertEquals("40", r.get("soldFor"), "白装强 2 应卖 40(=品质加成×强化系数)");
        assertEquals(0, s.gold.compareTo(Num.of(240)), "累计 200+40=240");
    }

    // ==================== enhance 强化 ====================

    /** 强化花费 100×1.5^现有等级 递增:0→1 花 100,1→2 花 150,2→3 花 225;没钱 400 且状态不动。 */
    @Test
    void enhanceCostEscalatesGeometrically() {
        PlayerState s = NewState.create();
        PlayerState.Item it = item("weapon", "PATK", "5", 1);
        s.equipBag.add(it);

        s.gold = Num.of(100);
        Map<String, Object> r = game.enhance(s, 0);
        assertEquals(1, r.get("enhancedTo"), "第一级强化成功");
        assertEquals(0, s.gold.compareTo(Num.of(0)), "100×1.5^0 = 100,恰好花光");
        s.gold = Num.of(150);
        game.enhance(s, 0);
        assertEquals(0, s.gold.compareTo(Num.of(0)), "第二级 100×1.5^1 = 150,恰好花光");
        assertEquals(0, EquipFactory.enhanceCost(it).compareTo(Num.of(225)),
                "第三级花费应为 100×1.5^2 = 225");

        s.gold = Num.of(224);
        ApiException poor = assertThrows(ApiException.class, () -> game.enhance(s, 0),
                "差 1 金也必须 400");
        assertEquals(400, poor.getStatus());
        assertTrue(poor.getMessage().contains("金币不足"));
        assertEquals(2, it.enhance, "失败强化不涨级");
        s.gold = Num.of(225);
        game.enhance(s, 0);
        assertEquals(3, it.enhance, "付得起就强化成功");
    }

    /** 强化上限 +10:到顶后 400,金币分文不动。 */
    @Test
    void enhanceRejectedAtMaxLevel() {
        PlayerState s = NewState.create();
        PlayerState.Item it = item("weapon", "PATK", "5", 1);
        it.enhance = GameConst.ENHANCE_MAX_LV;   // 10
        s.equipBag.add(it);
        s.gold = Num.of(999999);
        ApiException ex = assertThrows(ApiException.class, () -> game.enhance(s, 0),
                "已达上限必须 400");
        assertTrue(ex.getMessage().contains("已达强化上限"), "上限提示应明示 +10");
        assertEquals(0, s.gold.compareTo(Num.of(999999)), "上限拒绝不得扣钱");
        assertEquals(GameConst.ENHANCE_MAX_LV, it.enhance, "等级不再上涨");
    }

    // ==================== equip 换装 ====================

    /** 同部位换装:换下的旧装备回背包(不销毁、不折现)。 */
    @Test
    void equipSwappedOldItemReturnsToBag() {
        PlayerState s = NewState.create();
        PlayerState.Item old = item("weapon", "PATK", "10", 1);
        s.equipBag.add(old);
        game.equip(s, 0);
        assertSame(old, s.equipped.get("weapon"), "第一件穿入 weapon 槽");
        s.equipBag.add(item("weapon", "PATK", "30", 2));
        game.equip(s, 0);
        assertEquals("30", Num.plain(s.equipped.get("weapon").base.get("PATK")),
                "新武器应占据 weapon 槽");
        assertEquals(1, s.equipBag.size(), "旧武器回背包");
        assertSame(old, s.equipBag.get(0), "背包里正是被换下的旧武器(同一实例)");
        assertEquals(0, s.gold.compareTo(Num.ZERO), "有位可回时旧装绝不折现");
    }

    /**
     * 背包满时换装:旧装备自动折现卖出(金币 = 卖价),不丢失也不回包。
     * 用 BAG_CAP 腰带制造"容量缩水":穿普通 offhand 替换 +20 容量腰带后,
     * 背包剩余量超过新容量 40 → 走卖出分支。
     */
    @Test
    void equipWithNoBagRoomSellsOldItem() {
        PlayerState s = NewState.create();
        PlayerState.Item belt = new PlayerState.Item();
        belt.uid = 1;
        belt.name = "负重腰带";
        belt.slot = "offhand";
        belt.quality = 1;                        // 卖价 = 20×(1+1) = 40
        belt.base.put("BAG_CAP", new BigDecimal("20"));
        s.equipBag.add(belt);
        game.equip(s, 0);
        assertEquals(GameConst.BAG_MAX + 20, GameService.bagCap(s), "腰带应扩容到 60");

        while (s.equipBag.size() < GameConst.BAG_MAX + 20) {
            s.equipBag.add(item("boots", "SPD", "1", 0));
        }
        s.equipBag.add(item("offhand", "PDEF", "4", 0));   // 普通盾(无 BAG_CAP)
        game.equip(s, s.equipBag.size() - 1);

        assertEquals(0, s.gold.compareTo(Num.of(40)), "背包满换装应按卖价 40 折现旧腰带");
        assertEquals("offhand", s.equipped.get("offhand").slot, "新盾已穿上");
        assertTrue(s.equipBag.stream().noneMatch(it -> "负重腰带".equals(it.name)),
                "旧腰带已折现,不残留背包");
        assertNull(s.equipped.get("extra_0"), "也不应被塞进别的槽");
        assertEquals(GameConst.BAG_MAX, GameService.bagCap(s), "换装后容量回落 40");
    }
}
