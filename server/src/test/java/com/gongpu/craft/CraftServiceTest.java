package com.gongpu.craft;

import com.gongpu.cfg.AffixTemplateRepository;
import com.gongpu.cfg.BuffTemplateRepository;
import com.gongpu.cfg.CraftTemplate;
import com.gongpu.cfg.CraftTemplateRepository;
import com.gongpu.cfg.DropRateRuleRepository;
import com.gongpu.cfg.EquipmentTemplate;
import com.gongpu.cfg.EquipmentTemplateRepository;
import com.gongpu.cfg.GameData;
import com.gongpu.cfg.ItemTemplateRepository;
import com.gongpu.cfg.SkillTemplateRepository;
import com.gongpu.common.ApiException;
import com.gongpu.game.GameConst;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 第三批第 2 轮：图纸合成接通——校验顺序（图纸/材料/金币/背包）、扣料与产物入包、合成板标记。
 */
class CraftServiceTest {

    private final CraftService crafts = new CraftService();

    @BeforeAll
    static void initGameData() {
        EquipmentTemplate sword = new EquipmentTemplate();
        sword.setCode("iron_sword");
        sword.setName("铁剑");
        sword.setEmoji("🗡️");
        sword.setSlot("weapon");
        sword.setBaseStats("{\"PATK\":12}");
        sword.setMinZone("plain");
        EquipmentTemplateRepository equipRepo = mock(EquipmentTemplateRepository.class);
        when(equipRepo.findByEnabledTrueOrderBySortNoAscIdAsc()).thenReturn(List.of(sword));

        CraftTemplate basic = new CraftTemplate();
        basic.setCode("c_iron_sword");
        basic.setName("铁剑锻造");
        basic.setEmoji("⚒️");
        basic.setProductCode("iron_sword");
        basic.setMatsJson("{\"pelt\":2}");
        basic.setGoldCost(100);
        basic.setNeedBlueprint(false);
        basic.setEnabled(true);
        basic.setSortNo(1);
        CraftTemplate bpRecipe = new CraftTemplate();
        bpRecipe.setCode("c_super_sword");
        bpRecipe.setName("宝剑图纸");
        bpRecipe.setEmoji("📐");
        bpRecipe.setProductCode("iron_sword");
        bpRecipe.setMatsJson("{\"ore\":3}");
        bpRecipe.setGoldCost(500);
        bpRecipe.setNeedBlueprint(true);
        bpRecipe.setEnabled(true);
        bpRecipe.setSortNo(2);
        CraftTemplate cook = new CraftTemplate();
        cook.setCode("cook_test");
        cook.setName("测试烹饪");
        cook.setEmoji("🍳");
        cook.setProductCode("item:food");
        cook.setMatsJson("{\"pelt\":1}");
        cook.setGoldCost(10);
        cook.setNeedBlueprint(false);
        cook.setEnabled(true);
        cook.setSortNo(3);
        CraftTemplateRepository craftRepo = mock(CraftTemplateRepository.class);
        when(craftRepo.findByEnabledTrueOrderBySortNoAscIdAsc()).thenReturn(List.of(basic, bpRecipe, cook));

        com.gongpu.cfg.ItemTemplate food = new com.gongpu.cfg.ItemTemplate();
        food.setCode("food");
        food.setName("食物");
        food.setEmoji("🍖");
        ItemTemplateRepository itemRepo = mock(ItemTemplateRepository.class);
        when(itemRepo.findByEnabledTrueOrderBySortNoAscIdAsc()).thenReturn(List.of(food));

        new GameData(equipRepo, mock(AffixTemplateRepository.class), itemRepo,
                mock(SkillTemplateRepository.class), mock(BuffTemplateRepository.class),
                mock(DropRateRuleRepository.class), craftRepo);
    }

    private PlayerState richState() {
        PlayerState s = NewState.create();
        s.gold = new BigDecimal("10000");
        s.materials.put("pelt", 5);
        s.materials.put("ore", 5);
        return s;
    }

    @Test
    void basicCraftConsumesAndGrants() {
        PlayerState s = richState();
        int bagBefore = s.equipBag.size();
        Map<String, Object> r = crafts.craft(s, "c_iron_sword");
        assertEquals(Boolean.TRUE, r.get("ok"));
        assertEquals("铁剑", r.get("itemName"));
        assertEquals(bagBefore + 1, s.equipBag.size());
        assertEquals(3, s.materials.get("pelt"), "兽皮 5-2=3");
        assertEquals(1, s.stats.crafts, "合成成功应计 +1（成就里程碑）");
        assertEquals(0, s.gold.compareTo(new BigDecimal("9900")), "金币扣 100");
        int q = (Integer) r.get("quality");
        assertTrue(q >= 1 && q <= 2, "基础配方品质 1（25% 升 2）");
    }

    @Test
    void blueprintRecipeRequiresAndConsumesBlueprint() {
        PlayerState s = richState();
        assertThrows(ApiException.class, () -> crafts.craft(s, "c_super_sword"),
                "无图纸应拒绝");
        s.materials.put(CraftService.blueprintKey("c_super_sword"), 1);
        Map<String, Object> r = crafts.craft(s, "c_super_sword");
        assertEquals(Boolean.TRUE, r.get("ok"));
        assertTrue(!s.materials.containsKey(CraftService.blueprintKey("c_super_sword")),
                "图纸应被消耗");
        assertEquals(2, s.materials.get("ore"), "矿石 5-3=2");
        int q = (Integer) r.get("quality");
        assertTrue(q >= 2 && q <= 3, "图纸配方品质 2（25% 升 3）");
    }

    @Test
    void insufficientMatsOrGoldRejectedWithoutConsuming() {
        PlayerState s = richState();
        s.materials.put("pelt", 1);
        assertThrows(ApiException.class, () -> crafts.craft(s, "c_iron_sword"));
        assertEquals(1, s.materials.get("pelt"), "失败不扣料");
        PlayerState poor = richState();
        poor.gold = new BigDecimal("50");
        assertThrows(ApiException.class, () -> crafts.craft(poor, "c_iron_sword"));
        assertEquals(5, poor.materials.get("pelt"));
    }

    @Test
    void boardMarksCanCraftAndReasons() {
        PlayerState s = richState();
        Map<String, Object> board = crafts.board(s);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) board.get("crafts");
        assertEquals(3, rows.size());
        assertEquals(Boolean.TRUE, rows.get(0).get("canCraft"), "基础配方可合成");
        assertEquals(Boolean.FALSE, rows.get(1).get("canCraft"), "图纸配方缺图纸不可合成");
        assertTrue(String.valueOf(rows.get(1).get("blockReason")).contains("图纸"));
    }

    @Test
    void itemProductGoesToPotionBag() {
        // 烹饪类配方（product_code = item:<code>）：产物进药剂栏、不占背包、无品质
        PlayerState s = richState();
        int bagBefore = s.equipBag.size();
        Map<String, Object> r = crafts.craft(s, "cook_test");
        assertEquals(Boolean.TRUE, r.get("ok"));
        assertEquals("食物", r.get("itemName"));
        assertEquals(bagBefore, s.equipBag.size(), "物品产物不占背包");
        assertEquals(1, s.potions.getOrDefault("food", 0), "食物应进药剂栏");
    }

    @Test
    void exchangeCanReachInventoryLimitAndRejectsOverflowWithoutConsuming() {
        PlayerState s = richState();
        s.materials.put("pelt", GameConst.MAT_EXCHANGE_COST * 2);
        s.materials.put("ore", Integer.MAX_VALUE - 1);

        Map<String, Object> result = crafts.exchange(s, "pelt", "ore");

        assertEquals(Integer.MAX_VALUE, result.get("toHave"));
        assertEquals(GameConst.MAT_EXCHANGE_COST, s.materials.get("pelt"));
        assertInventoryLimitRejected(s, () -> crafts.exchange(s, "pelt", "ore"));
    }

    @Test
    void exchangeAllCanReachInventoryLimitAndPreservesRemainder() {
        PlayerState s = richState();
        s.materials.put("pelt", GameConst.MAT_EXCHANGE_COST * 2 + 1);
        s.materials.put("ore", Integer.MAX_VALUE - 2);

        Map<String, Object> result = crafts.exchangeAll(s, "pelt", "ore");

        assertEquals(2, result.get("times"));
        assertEquals(Integer.MAX_VALUE, result.get("toHave"));
        assertEquals(1, s.materials.get("pelt"));
    }

    @Test
    void exchangeAllRejectsLargeOverflowWithoutConsuming() {
        PlayerState s = richState();
        s.materials.put("pelt", Integer.MAX_VALUE);
        s.materials.put("ore", Integer.MAX_VALUE - 1);

        assertInventoryLimitRejected(s, () -> crafts.exchangeAll(s, "pelt", "ore"));
    }

    @Test
    void buyCanReachInventoryLimitAndRejectsOverflowWithoutCharging() {
        PlayerState s = richState();
        s.gold = new BigDecimal("1000000");
        s.materials.put("pelt", Integer.MAX_VALUE - 999);
        BigDecimal before = s.gold;

        Map<String, Object> result = crafts.buy(s, "pelt", 999);

        assertEquals(Integer.MAX_VALUE, result.get("have"));
        assertEquals(999, result.get("count"));
        long cost = (long) GameConst.MAT_SELL_PRICE.get("pelt") * CraftService.MAT_BUY_MUL * 999;
        assertEquals(0, s.gold.compareTo(before.subtract(BigDecimal.valueOf(cost))));
        assertInventoryLimitRejected(s, () -> crafts.buy(s, "pelt", 1));
        s.materials.put("pelt", Integer.MAX_VALUE - 998);
        assertInventoryLimitRejected(s, () -> crafts.buy(s, "pelt", 999));
    }

    @Test
    void buyGemCanReachInventoryLimitAndRejectsOverflowWithoutChargingOrCounting() {
        PlayerState s = richState();
        s.materials.put("gem_ruby", Integer.MAX_VALUE - 1);
        BigDecimal before = s.gold;
        long boughtBefore = s.stats.gemsBought;

        Map<String, Object> result = crafts.buyGem(s, "gem_ruby");

        assertEquals(Integer.MAX_VALUE, result.get("have"));
        long cost = (long) GameConst.GEM_SELL_PRICE.get("gem_ruby") * CraftService.GEM_BUY_MUL;
        assertEquals(0, s.gold.compareTo(before.subtract(BigDecimal.valueOf(cost))));
        assertEquals(boughtBefore + 1, s.stats.gemsBought);
        assertInventoryLimitRejected(s, () -> crafts.buyGem(s, "gem_ruby"));
    }

    @Test
    void itemCraftCanReachInventoryLimitAndRejectsOverflowWithoutConsumingOrCounting() {
        PlayerState s = richState();
        s.potions.put("food", Integer.MAX_VALUE - 1);

        crafts.craft(s, "cook_test");

        assertEquals(Integer.MAX_VALUE, s.potions.get("food"));
        assertEquals(4, s.materials.get("pelt"));
        assertEquals(0, s.gold.compareTo(new BigDecimal("9990")));
        assertEquals(1, s.stats.crafts);
        assertInventoryLimitRejected(s, () -> crafts.craft(s, "cook_test"));
    }

    @Test
    void ordinaryMaterialPurchasesAndExchangesKeepTheirCountsAndCosts() {
        PlayerState s = richState();
        s.materials.put("pelt", 10);

        crafts.exchange(s, "pelt", "ore");
        assertEquals(7, s.materials.get("pelt"));
        assertEquals(6, s.materials.get("ore"));
        Map<String, Object> exchanged = crafts.exchangeAll(s, "pelt", "ore");
        assertEquals(2, exchanged.get("times"));
        assertEquals(1, s.materials.get("pelt"));
        assertEquals(8, s.materials.get("ore"));

        Map<String, Object> bought = crafts.buy(s, "pelt", 2);
        assertEquals(2, bought.get("count"));
        assertEquals(3, s.materials.get("pelt"));
        crafts.buyGem(s, "gem_ruby");
        assertEquals(1, s.materials.get("gem_ruby"));
        long cost = (long) GameConst.MAT_SELL_PRICE.get("pelt") * CraftService.MAT_BUY_MUL * 2
                + (long) GameConst.GEM_SELL_PRICE.get("gem_ruby") * CraftService.GEM_BUY_MUL;
        assertEquals(0, s.gold.compareTo(new BigDecimal("10000").subtract(BigDecimal.valueOf(cost))));
        assertEquals(1, s.stats.gemsBought);
    }

    private void assertInventoryLimitRejected(PlayerState s, Runnable action) {
        Map<String, Integer> materialsBefore = Map.copyOf(s.materials);
        Map<String, Integer> potionsBefore = Map.copyOf(s.potions);
        BigDecimal goldBefore = s.gold;
        long craftsBefore = s.stats.crafts;
        long gemsBoughtBefore = s.stats.gemsBought;
        long matSoldBefore = s.stats.matSold;
        long matSoldGoldBefore = s.stats.matSoldGold;
        int bagBefore = s.equipBag.size();

        ApiException error = assertThrows(ApiException.class, action::run);

        assertEquals(400, error.getStatus());
        assertEquals("BAD_REQUEST", error.getCode());
        assertTrue(error.getMessage().contains("库存"));
        assertEquals(materialsBefore, s.materials);
        assertEquals(potionsBefore, s.potions);
        assertEquals(goldBefore, s.gold);
        assertEquals(craftsBefore, s.stats.crafts);
        assertEquals(gemsBoughtBefore, s.stats.gemsBought);
        assertEquals(matSoldBefore, s.stats.matSold);
        assertEquals(matSoldGoldBefore, s.stats.matSoldGold);
        assertEquals(bagBefore, s.equipBag.size());
    }

    @Test
    void unknownRecipeRejected() {
        assertThrows(ApiException.class, () -> crafts.craft(richState(), "c_nope"));
    }
}
