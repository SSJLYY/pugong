package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第六批第 79 轮：Boss 首杀奖励口径——每 Boss 一次，金额 1000×(区域序+1)，复刷不重复发。 */
class BossFirstKillTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private static PlayerState.MobState boss() {
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
        return m;
    }

    @Test
    void firstKillBonusOnce() {
        PlayerState s = NewState.create();
        s.zone = "plain";
        s.life.set("PATK", new BigDecimal("100"));
        s.fixed.CRIT = BigDecimal.ZERO;
        s.life.maxHP = new BigDecimal("10000");
        s.life.curHP = new BigDecimal("10000");
        s.gold = Num.ZERO;
        s.mob = boss();
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("10"), 1.0);
        // 平原序 1 → 首杀奖 2000 + 掉落金币 5
        assertEquals(0, new BigDecimal("2005").compareTo(s.gold), "首杀应得 2000 奖 + 5 金币");
        // 复刷同一 Boss：只有掉落金币
        s.zone = "plain";
        s.mob = boss();
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("10"), 1.0);
        assertEquals(0, new BigDecimal("2010").compareTo(s.gold), "复刷不应重复发首杀奖");
    }
}
