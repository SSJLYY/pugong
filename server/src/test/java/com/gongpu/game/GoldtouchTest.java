package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第九批第 83 轮：被动「点金手」——击杀金币乘区 +15%，与聚宝药剂乘算叠加。 */
class GoldtouchTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private static BigDecimal goldMul(PlayerState s) throws Exception {
        Method m = GameEngine.class.getDeclaredMethod("goldMul", PlayerState.class);
        m.setAccessible(true);
        return (BigDecimal) m.invoke(null, s);
    }

    @Test
    void goldtouchMultipliesKillGold() throws Exception {
        PlayerState s = NewState.create();
        assertEquals(0, BigDecimal.ONE.compareTo(goldMul(s)));
        s.passives.add("goldtouch");
        assertEquals(0, new BigDecimal("1.15").compareTo(goldMul(s)), "点金手应 ×1.15");
        s.buffs.put("fortune_pot", 60.0);
        assertEquals(0, new BigDecimal("1.725").compareTo(goldMul(s)), "与聚宝应乘算 ×1.725");
    }
}
