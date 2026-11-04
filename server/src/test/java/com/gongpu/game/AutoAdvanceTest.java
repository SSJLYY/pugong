package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第六批第 42 轮：自动推进——Boss 击杀后门槛达标进下一区，不达标回村。 */
class AutoAdvanceTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private static PlayerState fighter(boolean strong) {
        PlayerState s = NewState.create();
        s.zone = "plain";
        s.autoAdvance = true;
        s.life.set("PATK", new BigDecimal("100"));
        if (strong) {
            s.life.set("PDEF", new BigDecimal("10"));   // 森林门槛：maxHP≥3000 且 PDEF≥5
        }
        s.fixed.CRIT = BigDecimal.ZERO;
        s.life.maxHP = new BigDecimal(strong ? "50000" : "100");
        s.life.curHP = new BigDecimal(strong ? "50000" : "100");
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
        return s;
    }

    @Test
    void advancesWhenReqMet() {
        PlayerState s = fighter(true);
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("10"), 1.0);
        assertEquals("forest", s.zone, "门槛达标应自动推进森林");
        assertNotNull(s.mob, "推进后应已刷出新怪");
        assertFalse(s.mob.boss, "推进刷的是普通怪");
        assertTrue(s.events.stream().anyMatch(e -> e.contains("自动推进")), "推进应写战斗日志");
    }

    @Test
    void fallsBackToVillageWhenWeak() {
        PlayerState s = fighter(false);
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("10"), 1.0);
        assertEquals("village", s.zone, "门槛不达标应回村（原行为）");
    }

    @Test
    void helpers() {
        assertEquals("forest", GameEngine.nextZoneId("plain"));
        assertEquals("abyss", GameEngine.nextZoneId("volcano"));   // 第七批：火山之后是深渊裂隙
        assertEquals("star", GameEngine.nextZoneId("abyss"));   // 第八批：深渊之后是星界
        assertEquals("void", GameEngine.nextZoneId("star"));
        assertEquals(null, GameEngine.nextZoneId("void"));
        assertTrue(GameEngine.zoneReqMet(fighter(true), "forest"), "强档应达标森林");
        assertFalse(GameEngine.zoneReqMet(fighter(false), "forest"), "弱档不应达标森林");
    }
}
