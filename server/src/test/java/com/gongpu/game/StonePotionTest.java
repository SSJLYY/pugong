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
 * 第六批第 4 轮：石肤药剂——承伤 ×0.9（mobAttack 结算，格挡之后乘算）。
 * 夹具：怪 PATK 100、玩家双防 0、闪避 0，1 秒恰好挨 1 击 100 伤。
 */
class StonePotionTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState victim(boolean withPotion) {
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
        s.life.set("PDEF", Num.ZERO);   // 出生自带 PDEF 2 会让每击少 1.6，归零保恰好 100 伤
        s.life.set("MDEF", Num.ZERO);
        s.life.maxHP = Num.of(10000);
        s.life.curHP = Num.of(10000);
        if (withPotion) {
            s.buffs.put("stone_pot", 120.0);
        }
        return s;
    }

    @Test
    void damageReducedByTenPercent() {
        PlayerState plain = victim(false);
        GameEngine.mobAttack(plain, PanelCalculator.of(plain), 1.0);
        assertEquals(0, plain.life.curHP.compareTo(Num.of(9900)), "无药剂应掉 100 血");
        PlayerState stoned = victim(true);
        GameEngine.mobAttack(stoned, PanelCalculator.of(stoned), 1.0);
        assertEquals(0, stoned.life.curHP.compareTo(Num.of(9910)), "石肤应只掉 90 血");
    }
}
