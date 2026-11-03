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
 * 第六批第 6 轮：巨人药剂——对 Boss 伤害 +15%，与巨人杀手被动乘算叠加。
 * 夹具同 BossHunterTest：PATK 恰好 100、暴击归零，10 拳定伤。
 */
class GiantPotionTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fighter(boolean passive, boolean potion) {
        PlayerState s = NewState.create();
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "boss_x";
        m.name = "Boss";
        m.boss = true;
        m.maxHP = new BigDecimal("100000");
        m.curHP = new BigDecimal("100000");
        m.PATK = Num.ZERO;
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        s.mob = m;
        s.zone = "plain";
        s.life.set("PATK", new BigDecimal("100"));
        s.fixed.CRIT = BigDecimal.ZERO;
        s.life.maxHP = new BigDecimal("10000");
        s.life.curHP = new BigDecimal("10000");
        if (passive) {
            s.passives.add("boss_hunter");
        }
        if (potion) {
            s.buffs.put("giant_pot", 180.0);
        }
        return s;
    }

    @Test
    void potionOnlyPlus15() {
        PlayerState s = fighter(false, true);
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("10"), 1.0);
        assertEquals(0, s.mob.curHP.compareTo(Num.of(98850)), "10 拳 ×115 = 1150 伤，实际余血=" + s.mob.curHP);
    }

    @Test
    void stacksWithPassive() {
        PlayerState s = fighter(true, true);
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("10"), 1.0);
        assertEquals(0, s.mob.curHP.compareTo(Num.of(98620)), "100×1.2×1.15=138/拳 ×10 = 1380 伤，实际=" + s.mob.curHP);
    }
}
