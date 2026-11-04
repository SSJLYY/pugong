package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.ApiException;
import com.gongpu.craft.CraftService;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 第八批第 41 轮：图纸出售口径——离线空快照下未知图纸仍不可售（防幽灵定价）；
 * 定价公式按区域深度（由 blueprintPrice 的深度项静态验证：深度经 ZONES 序）。
 */
class BlueprintSellTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void unknownBlueprintNotPricedOffline() {
        // 离线 GameData 无配方快照：查不到配方就不能定价、不能卖（与上线口径一致地拒绝）
        assertNull(CraftService.blueprintPrice("bp_c_iron_sword"));
        assertNull(CraftService.blueprintPrice("pelt"), "非图纸键不应进入图纸定价");
        PlayerState s = NewState.create();
        s.materials.put("bp_c_iron_sword", 2);
        assertThrows(ApiException.class, () -> new CraftService().sell(s, "bp_c_iron_sword", 1));
        assertEquals(2, s.materials.get("bp_c_iron_sword"), "拒绝后库存不应变动");
    }
}
