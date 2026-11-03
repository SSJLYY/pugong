package com.gongpu.craft;

import com.gongpu.cfg.GameData;
import com.gongpu.common.ApiException;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 第七批第 51 轮：材料出售——区域材料按单价卖金币（count<=0 全卖、超量封顶），
 * 图纸/卷轴不可售，无库存拒绝；累计口径入 Stats。
 */
class MaterialSellTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void sellAllByDefault() {
        CraftService crafts = new CraftService();
        PlayerState s = NewState.create();
        s.materials.put("ore", 10);
        Map<String, Object> r = crafts.sell(s, "ore", 0);
        assertEquals(10, r.get("count"));
        assertEquals(600L, r.get("gold"), "矿石 60/份 × 10");
        assertEquals(0, s.materials.get("ore"));
        assertEquals(0, s.gold.compareTo(Num.of(600)));
        assertEquals(10, s.stats.matSold);
        assertEquals(600, s.stats.matSoldGold);
    }

    @Test
    void sellPartialAndCapAtStock() {
        CraftService crafts = new CraftService();
        PlayerState s = NewState.create();
        s.materials.put("fire_crystal", 3);
        Map<String, Object> r = crafts.sell(s, "fire_crystal", 99);
        assertEquals(3, r.get("count"), "超量应按库存封顶");
        assertEquals(660L, r.get("gold"), "火晶 220/份 × 3");
        PlayerState s2 = NewState.create();
        s2.materials.put("pelt", 5);
        Map<String, Object> r2 = crafts.sell(s2, "pelt", 2);
        assertEquals(2, r2.get("count"));
        assertEquals(3, s2.materials.get("pelt"));
    }

    @Test
    void rejectsUnsellableAndEmpty() {
        CraftService crafts = new CraftService();
        PlayerState s = NewState.create();
        s.materials.put("bp_c_abyss_blade", 2);
        assertThrows(ApiException.class, () -> crafts.sell(s, "bp_c_abyss_blade", 0),
                "图纸不可出售");
        assertThrows(ApiException.class, () -> crafts.sell(s, "scroll_fire", 0),
                "卷轴不可出售");
        assertThrows(ApiException.class, () -> crafts.sell(s, "ore", 0),
                "无库存应拒绝");
    }
}
