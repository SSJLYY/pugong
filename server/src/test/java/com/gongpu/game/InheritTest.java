package com.gongpu.game;

import com.gongpu.common.ApiException;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十三批集群一：装备传承（四维整表转移、覆盖语义、成本、同槽校验）。 */
class InheritTest {

    private final GameService game = new GameService();

    private static PlayerState.Item item(String slot, int enhance, int temper, int star, int awaken) {
        PlayerState.Item it = new PlayerState.Item();
        it.name = "测试装";
        it.slot = slot;
        it.quality = 3;
        it.enhance = enhance;
        it.temper = temper;
        it.star = star;
        it.awaken = awaken;
        it.base.put("PATK", new BigDecimal("1000"));
        return it;
    }

    @Test
    void inheritMovesAllFourDimsAndChargesExactCost() {
        PlayerState s = NewState.create();
        s.gold = new BigDecimal("1000000");
        PlayerState.Item src = item("weapon", 10, 3, 5, 2);
        PlayerState.Item dst = item("weapon", 1, 1, 1, 1);
        s.equipBag.add(src);
        s.equipBag.add(dst);
        Map<String, Object> r = game.inherit(s, 0, 1);
        // 目标被覆盖为源四维；源清零
        assertEquals(10, dst.enhance);
        assertEquals(3, dst.temper);
        assertEquals(5, dst.star);
        assertEquals(2, dst.awaken);
        assertEquals(0, src.enhance + src.temper + src.star + src.awaken);
        // 成本 = 5000 + 2000×(10+3) + 20000×5 + 50000×2 = 231000
        assertEquals("231000", r.get("goldCost"));
        assertEquals(0, new BigDecimal("769000").compareTo(s.gold));
        assertEquals(1, s.stats.inherits);
        @SuppressWarnings("unchecked")
        Map<String, Object> moved = (Map<String, Object>) r.get("inherited");
        assertEquals(5, moved.get("star"));
    }

    @Test
    void inheritRejectsCrossSlotSelfAndEmptySource() {
        PlayerState s = NewState.create();
        s.gold = new BigDecimal("1000000");
        s.equipBag.add(item("weapon", 5, 0, 0, 0));
        s.equipBag.add(item("head", 0, 0, 0, 0));
        assertThrows(ApiException.class, () -> game.inherit(s, 0, 1), "异槽应拒绝");
        assertThrows(ApiException.class, () -> game.inherit(s, 0, 0), "自己传自己应拒绝");
        assertThrows(ApiException.class, () -> game.inherit(s, 1, 0), "源无成长应拒绝");
        assertThrows(ApiException.class, () -> game.inherit(s, 0, 9), "越界应拒绝");
        assertEquals(0, s.stats.inherits);
    }

    @Test
    void inheritRejectsWhenGoldShort() {
        PlayerState s = NewState.create();
        s.gold = new BigDecimal("100");
        s.equipBag.add(item("weapon", 0, 0, 5, 0));
        s.equipBag.add(item("weapon", 0, 0, 0, 0));
        ApiException e = assertThrows(ApiException.class, () -> game.inherit(s, 0, 1));
        assertTrue(e.getMessage().contains("金币不足"));
        assertEquals(5, s.equipBag.get(0).star, "失败不得改动源装备");
    }
}
