package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第六批第 41 轮：战斗日志——Boss 击杀入账、环形上限 20、fullView 下发。 */
class EventLogTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void bossKillLogged() {
        PlayerState s = NewState.create();
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "boss_x";
        m.name = "测试 Boss";
        m.boss = true;
        m.maxHP = Num.of(10);
        m.curHP = Num.of(10);
        m.PATK = Num.ZERO;
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        m.gold = 5;
        s.mob = m;
        s.zone = "plain";
        s.life.set("PATK", new BigDecimal("100"));
        s.fixed.CRIT = BigDecimal.ZERO;
        s.life.maxHP = new BigDecimal("10000");
        s.life.curHP = new BigDecimal("10000");
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("10"), 1.0);
        assertTrue(s.events.stream().anyMatch(e -> e.contains("击败 Boss")),
                "Boss 击杀应写入战斗日志：" + s.events);
    }

    @Test
    void ringCapsAt20() {
        PlayerState s = NewState.create();
        for (int i = 0; i < 25; i++) {
            GameEngine.pushEvent(s, "ev" + i);
        }
        assertEquals(20, s.events.size(), "日志应环形保留 20 条");
        assertEquals("ev24", s.events.get(19), "最新一条应在尾部");
        assertEquals("ev5", s.events.get(0), "最旧的 5 条应被挤出");
    }
}
