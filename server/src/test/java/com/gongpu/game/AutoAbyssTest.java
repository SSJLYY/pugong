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

/** 第七批第 29 轮：自动推进链尾回归——火山 Boss 击杀后达标推进深渊、不达标回村；深渊 Boss 连战原地重刷。 */
class AutoAbyssTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private static PlayerState bossFighter(boolean strong, boolean autoBoss) {
        PlayerState s = NewState.create();
        s.zone = "volcano";
        s.autoAdvance = true;
        s.autoBoss = autoBoss;
        s.fixed.CRIT = BigDecimal.ZERO;
        s.life.maxHP = new BigDecimal(strong ? "5000000" : "100");
        s.life.curHP = new BigDecimal(strong ? "5000000" : "100");
        s.life.set("PATK", new BigDecimal(strong ? "10000" : "100"));
        if (strong) {
            s.life.set("PDEF", new BigDecimal("500"));
            s.life.set("MDEF", new BigDecimal("500"));
        }
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "boss_ifrit";
        m.name = "炎狱魔王";
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
    void volcanoBossKillAdvancesIntoAbyss() {
        PlayerState s = bossFighter(true, false);
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("10"), 1.0);
        assertEquals("abyss", s.zone, "达标应自动推进深渊裂隙");
        assertNotNull(s.mob);
        assertFalse(s.mob.boss, "推进后刷普通怪");
        assertTrue(s.events.stream().anyMatch(e -> e.contains("自动推进")), "推进应写战斗日志");
    }

    @Test
    void weakFighterFallsBackToVillage() {
        PlayerState s = bossFighter(false, false);
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("10"), 1.0);
        assertEquals("village", s.zone, "深渊门槛不达标应回村而非卡在火山");
    }

    @Test
    void abyssAutoBossRespawnsBossInPlace() {
        PlayerState s = bossFighter(true, true);
        s.zone = "abyss";
        s.mob.id = "boss_abyss";
        s.mob.name = "渊底之主·无光君王";
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("10"), 1.0);
        assertEquals("abyss", s.zone, "连战模式应留在深渊");
        assertNotNull(s.mob);
        assertTrue(s.mob.boss, "连战应原地重刷 Boss");
        assertEquals("boss_abyss", s.mob.id);
    }
}
