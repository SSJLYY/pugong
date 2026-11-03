package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.craft.CraftService;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 第六批第 36 轮：材料兑换——3:1 跨区调剂，非法组合全拒绝。 */
class MatExchangeTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void threeToOneExchange() {
        PlayerState s = NewState.create();
        s.materials.put("pelt", 7);
        CraftService cs = new CraftService();
        Map<String, Object> r = cs.exchange(s, "pelt", "fire_crystal");
        assertEquals(4, s.materials.get("pelt"), "应扣 3 份兽皮");
        assertEquals(1, s.materials.get("fire_crystal"), "应得 1 份火晶");
        assertEquals(1, r.get("toHave"));
    }

    @Test
    void rejectsInvalid() {
        PlayerState s = NewState.create();
        s.materials.put("pelt", 10);
        CraftService cs = new CraftService();
        assertThrows(RuntimeException.class, () -> cs.exchange(s, "pelt", "pelt"), "同种应拒");
        assertThrows(RuntimeException.class, () -> cs.exchange(s, "pelt", "bp_x"), "图纸键应拒");
        assertThrows(RuntimeException.class, () -> cs.exchange(s, "scroll_fire", "ore"), "卷轴应拒");
        PlayerState poor = NewState.create();
        poor.materials.put("ore", 2);
        assertThrows(RuntimeException.class, () -> cs.exchange(poor, "ore", "pelt"), "不足 3 份应拒");
    }
}
