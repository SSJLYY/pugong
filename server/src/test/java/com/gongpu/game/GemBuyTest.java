package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.ApiException;
import com.gongpu.common.Num;
import com.gongpu.craft.CraftService;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 第八批第 44 轮：宝石直购——一阶按售价 ×4；高阶与未知宝石拒绝；金币不足拒绝。
 */
class GemBuyTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void buyTier1AtQuadruple() {
        PlayerState s = NewState.create();
        s.gold = Num.of(10000);
        Map<String, Object> r = new CraftService().buyGem(s, "gem_opal");
        assertEquals(2800L, r.get("cost"), "蛋白石售价 700 → 直购 2800");
        assertEquals(1, s.materials.get("gem_opal"));
        assertEquals(0, s.gold.compareTo(Num.of(7200)));
    }

    @Test
    void allTier1GemsRemainPurchasable() {
        CraftService crafts = new CraftService();
        assertEquals(6, GameConst.gemTier1Ids().size());
        for (String code : GameConst.gemTier1Ids()) {
            PlayerState s = NewState.create();
            s.gold = Num.of(100000);
            s.materials.put(code, 2);
            s.stats.gemsBought = 7;
            long cost = (long) GameConst.GEM_SELL_PRICE.get(code) * CraftService.GEM_BUY_MUL;

            Map<String, Object> result = crafts.buyGem(s, code);

            assertEquals(cost, result.get("cost"), code);
            assertEquals(3, s.materials.get(code), code);
            assertEquals(0, s.gold.compareTo(Num.of(100000 - cost)), code);
            assertEquals(8, s.stats.gemsBought, code);
        }
    }

    @Test
    void rejectsAllTier3GemsWithoutChangingResourcesOrStats() {
        CraftService crafts = new CraftService();
        int checked = 0;
        for (String code : GameConst.GEMS.keySet()) {
            if (!code.endsWith("_3")) {
                continue;
            }
            PlayerState s = NewState.create();
            s.gold = Num.of(1000000);
            s.materials.put(code, 2);
            s.stats.gemsBought = 7;
            Map<String, Integer> materialsBefore = Map.copyOf(s.materials);

            ApiException error = assertThrows(ApiException.class, () -> crafts.buyGem(s, code), code);

            assertEquals(400, error.getStatus(), code);
            assertEquals("BAD_REQUEST", error.getCode(), code);
            assertEquals(0, s.gold.compareTo(Num.of(1000000)), code);
            assertEquals(materialsBefore, s.materials, code);
            assertEquals(7, s.stats.gemsBought, code);
            checked++;
        }
        assertEquals(6, checked);
    }

    @Test
    void rejectsTier2UnknownAndPoor() {
        PlayerState s = NewState.create();
        s.gold = Num.of(100000);
        assertThrows(ApiException.class, () -> new CraftService().buyGem(s, "gem_ruby_2"), "二阶不可直购");
        assertThrows(ApiException.class, () -> new CraftService().buyGem(s, "gem_nope"), "未知宝石拒绝");
        PlayerState poor = NewState.create();
        poor.gold = Num.of(100);
        assertThrows(ApiException.class, () -> new CraftService().buyGem(poor, "gem_ruby"), "金币不足拒绝");
    }
}
