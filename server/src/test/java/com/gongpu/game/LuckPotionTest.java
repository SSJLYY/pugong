package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 第四批第 3 轮：鸿运药剂——luck_pot 生效中掉落倍率 1.5（装备/图纸/溢出期望三处共用 dropLuckMul），
 * 无 buff 或 buff 过期（0 秒）时为 1.0；喝下后 buff 时长 300 秒。
 */
class LuckPotionTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void mulSwitchesWithBuff() {
        PlayerState s = NewState.create();
        assertEquals(1.0, GameEngine.dropLuckMul(s), "无 buff 应为 1.0");
        s.buffs.put("luck_pot", 300.0);
        assertEquals(1.5, GameEngine.dropLuckMul(s), "鸿运中应为 1.5");
        s.buffs.put("luck_pot", 0.0);
        assertEquals(1.0, GameEngine.dropLuckMul(s), "buff 归零应回到 1.0");
    }

    @Test
    void drinkSets300sBuff() {
        PlayerState s = NewState.create();
        s.potions.put("luck", 1);
        new GameService().usePotion(s, "luck");
        assertEquals(300.0, s.buffs.get("luck_pot"));
    }
}
