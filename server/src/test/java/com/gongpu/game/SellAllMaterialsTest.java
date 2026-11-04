package com.gongpu.game;

import com.gongpu.common.ApiException;
import com.gongpu.craft.CraftService;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 第九批·一键出售全部区域材料（第 41 轮）：总价与逐个卖一致，宝石/图纸不动。 */
class SellAllMaterialsTest {

    @Test
    void sellsOnlyZoneMaterialsAtListedPrices() {
        PlayerState s = NewState.create();
        s.materials.put("pelt", 10);        // 20 × 10 = 200
        s.materials.put("void_shard", 2);   // 2000 × 2 = 4000
        s.materials.put("gem_ruby", 5);     // 宝石不动
        s.materials.put("bp_star_blade", 1); // 图纸不动
        Map<String, Object> r = new CraftService().sellAllMaterials(s);
        assertEquals(4200L, ((Number) r.get("gold")).longValue());
        assertEquals(2, ((Number) r.get("kinds")).intValue());
        assertEquals(0, s.materials.get("pelt"));
        assertEquals(5, s.materials.get("gem_ruby"));
        assertEquals(1, s.materials.get("bp_star_blade"));
        assertEquals(12, s.stats.matSold);
        assertEquals(4200, s.stats.matSoldGold);
    }

    @Test
    void emptyThrows() {
        PlayerState s = NewState.create();
        assertThrows(ApiException.class, () -> new CraftService().sellAllMaterials(s));
    }
}
