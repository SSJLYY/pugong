package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第三批第 1 轮（引擎侧）：击杀同时累加当世 kills 与终身 lifetimeKills。 */
class LifetimeKillsEngineTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void killIncrementsBothCounters() {
        PlayerState s = NewState.create();
        s.zone = "plain";
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "rabbit";
        m.name = "野兔";
        m.emoji = "🐇";
        m.maxHP = new BigDecimal("10");
        m.curHP = new BigDecimal("10");
        m.PATK = Num.ZERO;
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        m.ASPD = 0;
        m.RNG = 0;
        m.SPD = 1;
        s.mob = m;
        s.life.set("PATK", new BigDecimal("100"));
        // 只出 1 拳（约 100 伤）：打死 10 血目标后余伤 90 不足以打死重生的 120 血野兔，恰好 1 杀
        GameEngine.dealDamage(s, PanelCalculator.of(s), BigDecimal.ONE, 1.0);
        assertEquals(0, s.stats.kills.compareTo(BigDecimal.ONE), "当世击杀 +1");
        assertEquals(0, s.stats.lifetimeKills.compareTo(BigDecimal.ONE), "终身击杀同步 +1");
    }
}
