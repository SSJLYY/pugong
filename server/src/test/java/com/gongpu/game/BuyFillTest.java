package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第六批第 37 轮：buyfill——补给到目标库存，金币不足部分买入，未知药剂报错。 */
class BuyFillTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void fillsToTarget() {
        PlayerState s = NewState.create();
        s.potions.put("hp_s", 3);
        s.gold = Num.of(100000);
        Map<String, Object> r = GameService.buyFill(s, "hp_s", 10);
        assertEquals(7, r.get("bought"), "应补 7 瓶");
        assertEquals(10, s.potions.get("hp_s"));
    }

    @Test
    void stopsWhenGoldRunsOut() {
        PlayerState s = NewState.create();
        s.potions.put("hp_s", 0);
        s.gold = Num.of(1);   // 一瓶都买不起（标价远超 1 金）
        Map<String, Object> r = GameService.buyFill(s, "hp_s", 10);
        assertEquals(0, r.get("bought"), "金币不足应 0 买入且不抛错");
        assertTrue(Num.gte(s.gold, Num.ZERO), "金币不应被扣成负数");
    }

    @Test
    void unknownPotionRejected() {
        PlayerState s = NewState.create();
        s.gold = Num.of(1000);
        assertThrows(RuntimeException.class, () -> GameService.buyFill(s, "nope", 10),
                "未知药剂应报错而非静默");
        assertThrows(RuntimeException.class, () -> GameService.buyFill(s, "hp_s", 0),
                "目标 0 应报错");
    }
}
