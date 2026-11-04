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
 * 第八批第 4 轮：宝石出售——与材料出售同端点同口径（GEM_SELL_PRICE），
 * 计入 matSold 统计；不可售材料仍拒绝。
 */
class GemSellTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void gemsSellAtTablePrice() {
        PlayerState s = NewState.create();
        s.materials.put("gem_topaz", 3);
        Map<String, Object> r = new CraftService().sell(s, "gem_topaz", 2);
        assertEquals(1600L, r.get("gold"), "黄玉 800/颗 ×2");
        assertEquals(1, s.materials.get("gem_topaz"));
        assertEquals(1600, s.stats.matSoldGold);
        // 全卖（count<=0）
        Map<String, Object> r2 = new CraftService().sell(s, "gem_topaz", 0);
        assertEquals(800L, r2.get("gold"));
        assertEquals(0, s.materials.get("gem_topaz"));
        assertEquals(0, Num.of(2400).compareTo(Num.of(s.stats.matSoldGold)));
    }

    @Test
    void unsellableStillRejected() {
        PlayerState s = NewState.create();
        s.materials.put("bp_c_iron_sword", 1);
        assertThrows(ApiException.class, () -> new CraftService().sell(s, "bp_c_iron_sword", 1),
                "图纸仍不可出售");
    }
}
