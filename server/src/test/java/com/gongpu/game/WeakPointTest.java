package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 第七批第 47 轮：弱点打击——每第 10 拳必暴击。
 * 期望口径下该拳按 CRITD/期望倍率 放大，节拍跨帧累计，触发计入 Stats.weakCrits。
 */
class WeakPointTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fighter() {
        PlayerState s = NewState.create();
        s.zone = "plain";
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "boar";
        m.name = "野猪";
        m.emoji = "🐗";
        m.maxHP = new BigDecimal("100000000");
        m.curHP = new BigDecimal("100000000");
        m.PATK = Num.ZERO;
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        s.mob = m;
        s.life.set("PATK", new BigDecimal("100"));
        s.fixed.CRIT = BigDecimal.ZERO;          // 暴击率 0 → 期望倍率 1
        s.fixed.CRITD = new BigDecimal("2");      // 暴击倍率 2 → 弱点拳 200
        s.life.maxHP = new BigDecimal("100000");
        s.life.curHP = new BigDecimal("100000");
        return s;
    }

    private BigDecimal damageDealt(PlayerState s, String punches) {
        BigDecimal before = s.mob.curHP;
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal(punches), 1.0);
        return before.subtract(s.mob.curHP);
    }

    @Test
    void tenthPunchCrits() {
        PlayerState s = fighter();
        assertEquals(0, damageDealt(s, "10").compareTo(new BigDecimal("1100")),
                "10 拳 = 9×100 + 弱点拳 200 = 1100");
        assertEquals(1, s.stats.weakCrits);
    }

    @Test
    void ninePunchesNoCrit() {
        PlayerState s = fighter();
        assertEquals(0, damageDealt(s, "9").compareTo(new BigDecimal("900")),
                "9 拳不应触发弱点打击");
        assertEquals(0, s.stats.weakCrits);
    }

    @Test
    void rhythmCarriesAcrossFrames() {
        PlayerState s = fighter();
        assertEquals(0, damageDealt(s, "5").compareTo(new BigDecimal("500")));
        assertEquals(0, damageDealt(s, "5").compareTo(new BigDecimal("600")),
                "跨帧累计满 10 拳，第二帧应含一次弱点拳：5×100+100 差额");
        assertEquals(1, s.stats.weakCrits);
        assertEquals(0, damageDealt(s, "20").compareTo(new BigDecimal("2200")),
                "20 拳应触发两次弱点打击");
        assertEquals(3, s.stats.weakCrits);
    }
}
