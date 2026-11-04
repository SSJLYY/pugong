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
 * 第八批第 42 轮：材料商店——区域材料按售价 ×3 买入（买贵卖贱防套利），
 * 金币不足/非区域材料拒绝，单次钳制 999。
 */
class MatBuyTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void buyAtTripleSellPrice() {
        PlayerState s = NewState.create();
        s.gold = Num.of(10000);
        Map<String, Object> r = new CraftService().buy(s, "ore", 10);
        // 矿石售价 60 → 买入 180/份 ×10 = 1800
        assertEquals(1800L, r.get("cost"));
        assertEquals(10, s.materials.get("ore"));
        assertEquals(0, s.gold.compareTo(Num.of(8200)));
        // 卖回只得 600：价差防套利
        Map<String, Object> back = new CraftService().sell(s, "ore", 0);
        assertEquals(600L, back.get("gold"));
    }

    @Test
    void buyRejectsPoorAndNonZoneMats() {
        PlayerState s = NewState.create();
        s.gold = Num.of(100);
        assertThrows(ApiException.class, () -> new CraftService().buy(s, "star_shard", 1), "金币不足应拒绝");
        assertThrows(ApiException.class, () -> new CraftService().buy(s, "gem_ruby", 1), "宝石不可在材料商店买");
        assertThrows(ApiException.class, () -> new CraftService().buy(s, "bp_c_iron_sword", 1), "图纸不可买");
    }
}
