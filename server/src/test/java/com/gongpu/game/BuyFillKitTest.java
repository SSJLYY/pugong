package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第七批第 56 轮：一键补给包——四件套一次补到目标库存；
 * 已满不重复买、金币不足时逐件尽力而为。
 */
class BuyFillKitTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void kitFillsAllFourToTargets() {
        PlayerState s = NewState.create();
        s.potions.clear();
        s.gold = Num.of(1_000_000);
        Map<String, Object> r = GameService.buyFillKit(s);
        assertEquals(21, r.get("bought"), "10+5+3+3 应共购 21 件");
        for (Map.Entry<String, Integer> e : GameService.FILL_KIT.entrySet()) {
            assertEquals(e.getValue(), s.potions.get(e.getKey()),
                    e.getKey() + " 应补到目标库存");
        }
        // 再买一次：已满应零购入
        Map<String, Object> r2 = GameService.buyFillKit(s);
        assertEquals(0, r2.get("bought"), "已满不应重复购买");
    }

    @Test
    void kitDoesBestEffortWhenPoor() {
        PlayerState s = NewState.create();
        s.potions.clear();
        s.gold = Num.of(60);   // 只够买 1 瓶小血药（50 金）
        Map<String, Object> r = GameService.buyFillKit(s);
        int bought = (Integer) r.get("bought");
        assertTrue(bought >= 1 && bought < 21, "金币不足时应逐件尽力，实际购入 " + bought);
        assertTrue(Num.gte(s.gold, Num.ZERO), "不应透支金币");
    }
}
