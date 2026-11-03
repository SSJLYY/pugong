package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.ApiException;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第七批第 53 轮：每日黑市——货单确定性生成（同日同种子恒定）、折扣区间、
 * 限购与金币校验、跨日重刷清已购。
 */
class BlackMarketTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void offersAreDeterministicAndDiscounted() {
        PlayerState a = NewState.create();
        PlayerState b = NewState.create();
        b.stats.born = a.stats.born;
        List<Map<String, Object>> va = BlackMarket.viewOf(a);
        List<Map<String, Object>> vb = BlackMarket.viewOf(b);
        assertEquals(3, va.size(), "每日应刷 3 种药剂");
        assertEquals(va.toString(), vb.toString(), "同日同种子货单应完全一致");
        for (Map<String, Object> m : va) {
            int discount = (Integer) m.get("discount");
            int price = (Integer) m.get("price");
            int base = (Integer) m.get("basePrice");
            assertTrue(discount >= 30 && discount <= 50, "折扣应在 30–50%：实际 " + discount);
            assertTrue(price < base, "黑市价应低于原价");
            assertTrue(BlackMarket.POOL.contains(m.get("id")), "货品应来自黑市药池");
        }
    }

    @Test
    void buyRespectsLimitAndGold() {
        GameService game = new GameService();
        PlayerState s = NewState.create();
        s.gold = Num.of(1_000_000);
        String id = (String) BlackMarket.viewOf(s).get(0).get("id");
        int price = (Integer) BlackMarket.viewOf(s).get(0).get("price");
        for (int i = 0; i < BlackMarket.LIMIT_PER_ITEM; i++) {
            game.buyBlackMarket(s, id);
        }
        assertEquals(BlackMarket.LIMIT_PER_ITEM, s.potions.get(id).intValue());
        assertEquals(BlackMarket.LIMIT_PER_ITEM, s.stats.marketBuys);
        assertEquals(0, s.gold.compareTo(Num.of(1_000_000L - (long) price * BlackMarket.LIMIT_PER_ITEM)),
                "应按折后价逐件扣款");
        assertThrows(ApiException.class, () -> game.buyBlackMarket(s, id), "超限购应拒绝");
        assertThrows(ApiException.class, () -> game.buyBlackMarket(s, "hp_s"), "非货单药剂应拒绝");

        PlayerState poor = NewState.create();
        poor.gold = Num.ZERO;
        BlackMarket.ensure(poor);
        String anyId = poor.marketOffers.get(0).id;
        assertThrows(ApiException.class, () -> game.buyBlackMarket(poor, anyId), "金币不足应拒绝");
    }

    @Test
    void crossDayRegeneratesAndResetsBought() {
        PlayerState s = NewState.create();
        BlackMarket.ensure(s);
        s.marketBought.put(s.marketOffers.get(0).id, 3);
        s.marketDay = "2000-01-01";
        BlackMarket.ensure(s);
        assertEquals(BlackMarket.today(), s.marketDay, "跨日应重刷货单");
        assertTrue(s.marketBought.isEmpty(), "跨日应清已购计数");
        assertEquals(3, s.marketOffers.size());
    }
}
