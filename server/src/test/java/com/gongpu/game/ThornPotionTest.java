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
 * 第六批第 5 轮：荆棘药剂——反弹实际承受伤害的 20% 给怪。
 * 夹具同 StonePotionTest：挨 1 击恰好 100 伤 → 应反弹 20。
 */
class ThornPotionTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void reflectsTwentyPercent() {
        PlayerState s = NewState.create();
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "boar";
        m.name = "野猪";
        m.maxHP = Num.of(100000);
        m.curHP = Num.of(100000);
        m.PATK = Num.of(100);
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        m.ASPD = 1;
        m.RNG = 1;
        m.SPD = 10;
        s.mob = m;
        s.zone = "plain";
        s.fixed.EVA = BigDecimal.ZERO;
        s.fixed.RNG = Num.of(1);
        s.fixed.SPD = Num.of(10);
        s.life.set("PDEF", Num.ZERO);
        s.life.set("MDEF", Num.ZERO);
        s.life.maxHP = Num.of(10000);
        s.life.curHP = Num.of(10000);
        s.buffs.put("thorn_pot", 120.0);
        GameEngine.mobAttack(s, PanelCalculator.of(s), 1.0);
        assertEquals(0, s.life.curHP.compareTo(Num.of(9900)), "玩家应掉 100 血");
        assertEquals(0, m.curHP.compareTo(Num.of(99980)), "怪应被反弹 20，实际=" + m.curHP);
    }
}
