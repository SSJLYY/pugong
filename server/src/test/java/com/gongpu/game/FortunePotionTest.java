package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第六批第 84 轮：聚宝药剂——击杀金币 ×1.5，与点金手天赋乘算。 */
class FortunePotionTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private static BigDecimal killGold(String talent, boolean fortune) {
        PlayerState s = NewState.create();
        s.zone = "plain";
        s.talent = talent;
        if (fortune) {
            s.buffs.put("fortune_pot", 300.0);
        }
        s.life.set("PATK", new BigDecimal("100"));
        s.fixed.CRIT = BigDecimal.ZERO;
        s.life.maxHP = new BigDecimal("10000");
        s.life.curHP = new BigDecimal("10000");
        s.gold = Num.ZERO;
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "gold_dummy";
        m.name = "金靶";
        m.maxHP = Num.of(10);
        m.curHP = Num.of(10);
        m.PATK = Num.ZERO;
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        m.gold = 100;
        s.mob = m;
        GameEngine.dealDamage(s, PanelCalculator.of(s), BigDecimal.ONE, 1.0);   // 只打一拳：多拳会连杀重生怪、混入其金币
        return s.gold;
    }

    @Test
    void fortuneMultipliesGold() {
        assertEquals(0, new BigDecimal("100").compareTo(killGold(null, false)), "无增益应得 100");
        assertEquals(0, new BigDecimal("150").compareTo(killGold(null, true)), "聚宝应 ×1.5 得 150");
        assertEquals(0, new BigDecimal("180").compareTo(killGold("goldhand", true)), "点金手 ×1.2 与聚宝乘算得 180");
    }
}
