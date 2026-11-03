package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.craft.CraftService;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第七批第 58 轮：经济系统联动回归——黑市与商店涨价阶梯互不干扰、
 * 材料出售与兑换共用库存口径、黑市货单跨转生保留（当日维度）。
 */
class EconomyInterplayTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void marketBuysDoNotRaiseShopPriceLadder() {
        GameService game = new GameService();
        PlayerState s = NewState.create();
        s.gold = Num.of(1_000_000);
        String id = s.marketOffers.isEmpty()
                ? (String) BlackMarket.viewOf(s).get(0).get("id")
                : s.marketOffers.get(0).id;
        game.buyBlackMarket(s, id);
        assertEquals(0, s.potionBought.getOrDefault(id, 0),
                "黑市是独立折扣通道，不应推高商店的动态涨价计数");
        // 商店首购仍是原价阶梯（×1.0）
        int price = GameData.instance().potion(id).price();
        java.math.BigDecimal goldBefore = s.gold;
        GameService.buyPotion(s, id, 1);
        assertEquals(0, goldBefore.subtract(s.gold).compareTo(Num.of(price)),
                "黑市买过之后商店首购仍应按原价");
    }

    @Test
    void sellAndExchangeShareStock() {
        CraftService crafts = new CraftService();
        PlayerState s = NewState.create();
        s.materials.put("pelt", 10);
        crafts.exchange(s, "pelt", "ore");            // -3 pelt +1 ore
        assertEquals(7, s.materials.get("pelt"));
        assertEquals(1, s.materials.get("ore"));
        crafts.sell(s, "pelt", 0);                    // 全卖剩余 7 份兽皮
        assertEquals(0, s.materials.get("pelt"));
        assertEquals(0, s.gold.compareTo(Num.of(7 * 20)), "兑换后再卖，库存与收入应衔接");
        assertEquals(1, s.materials.get("ore"), "兑换所得矿石不受出售影响");
    }

    @Test
    void marketSurvivesRebirthSameDay() {
        PlayerState s = NewState.create();
        s.gold = Num.of(1_000_000);
        BlackMarket.ensure(s);
        String firstId = s.marketOffers.get(0).id;
        BlackMarket.buy(s, firstId);
        com.gongpu.state.SaveService.resetForNewLife(s);
        BlackMarket.ensure(s);
        assertEquals(firstId, s.marketOffers.get(0).id, "同日转生不应重刷黑市货单");
        assertEquals(1, s.marketBought.getOrDefault(firstId, 0),
                "同日已购计数应保留（防转生刷限购）");
        assertTrue(s.gold.signum() == 0, "转生清金币是既有口径，与黑市无关");
    }
}
