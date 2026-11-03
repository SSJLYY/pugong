package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第六批第 43 轮：Boss 连战——击杀后留在本区再刷 Boss，且优先于自动推进。 */
class AutoBossTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void bossRespawnsInSameZone() {
        PlayerState s = NewState.create();
        s.zone = "plain";
        s.autoBoss = true;
        s.autoAdvance = true;   // 两者同开时连战优先
        s.life.set("PATK", new BigDecimal("100"));
        s.life.set("PDEF", new BigDecimal("10"));
        s.fixed.CRIT = BigDecimal.ZERO;
        s.life.maxHP = new BigDecimal("50000");
        s.life.curHP = new BigDecimal("50000");
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "boss_plain";
        m.name = "平原 Boss";
        m.boss = true;
        m.maxHP = Num.of(10);
        m.curHP = Num.of(10);
        m.PATK = Num.ZERO;
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        m.gold = 5;
        s.mob = m;
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("10"), 1.0);
        assertEquals("plain", s.zone, "连战应留在本区（不推进、不回村）");
        assertNotNull(s.mob, "连战应已刷出新 Boss");
        assertTrue(s.mob.boss, "新怪应仍是 Boss");
        assertTrue(s.events.stream().anyMatch(e -> e.contains("连战")), "连战应写战斗日志");
    }
}
