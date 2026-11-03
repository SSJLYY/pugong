package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 第七批第 44 轮：被动「血怒」——生命低于 30% 时双攻 ×1.15；
 * 血线之上不触发、沉默压制不触发。
 */
class BloodrageTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fighter(BigDecimal curHP) {
        PlayerState s = NewState.create();
        s.passives.add("bloodrage");
        s.life.set("PATK", new BigDecimal("100"));
        s.life.set("MATK", new BigDecimal("200"));
        s.life.maxHP = new BigDecimal("10000");
        s.life.curHP = curHP;
        return s;
    }

    @Test
    void lowHpBoostsBothAttacks() {
        PlayerState s = fighter(new BigDecimal("2900"));   // 29% < 30%
        PanelCalculator.Panel p = PanelCalculator.of(s);
        assertEquals(0, p.PATK().compareTo(new BigDecimal("115")), "残血物攻应 ×1.15");
        assertEquals(0, p.MATK().compareTo(new BigDecimal("230")), "残血魔攻应 ×1.15");
    }

    @Test
    void aboveLineNoBoost() {
        PlayerState s = fighter(new BigDecimal("3000"));   // 恰 30%，不低于血线
        PanelCalculator.Panel p = PanelCalculator.of(s);
        assertEquals(0, p.PATK().compareTo(new BigDecimal("100")), "血线之上不应触发血怒");
    }

    @Test
    void silenceSuppressesBloodrage() {
        PlayerState s = fighter(new BigDecimal("1000"));
        s.buffs.put("silence", 10.0);
        PanelCalculator.Panel p = PanelCalculator.of(s);
        assertEquals(0, p.PATK().compareTo(new BigDecimal("100")), "沉默时血怒应被压制");
    }

    @Test
    void noPassiveNoBoost() {
        PlayerState s = fighter(new BigDecimal("1000"));
        s.passives.clear();
        PanelCalculator.Panel p = PanelCalculator.of(s);
        assertEquals(0, p.PATK().compareTo(new BigDecimal("100")), "未学血怒不应触发");
    }
}
