package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第八批第 12 轮：怪物词条「坚韧」——暴击率对它减半（期望口径），
 * dealDamage 与 expectedPerHit 同口径；非暴击部分不受影响。
 */
class TraitTenacityTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fighter(String trait) {
        PlayerState s = NewState.create();
        s.life.set("PATK", new BigDecimal("100"));
        s.fixed.CRIT = new BigDecimal("0.5");
        s.fixed.CRITD = new BigDecimal("2");
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "dummy2";
        m.name = "试炼怪";
        m.maxHP = new BigDecimal("100000000");
        m.curHP = new BigDecimal("100000000");
        m.trait = trait;
        s.mob = m;
        return s;
    }

    @Test
    void tenacityHalvesCritExpectation() {
        PlayerState plain = fighter(null);
        PlayerState tough = fighter("tenacity");
        PanelCalculator.Panel pp = PanelCalculator.of(plain);
        PanelCalculator.Panel tp = PanelCalculator.of(tough);
        // 平常：critMul = 1 + 0.5×(2-1) = 1.5 → 每拳 150（防 0）
        assertEquals(0, GameEngine.expectedPerHit(plain, pp, plain.mob).compareTo(Num.of(150)));
        // 坚韧：有效暴击 0.25 → critMul = 1.25 → 每拳 125
        assertEquals(0, GameEngine.expectedPerHit(tough, tp, tough.mob).compareTo(Num.of(125)));
        // effCrit 直接口径
        assertEquals(0, GameEngine.effCrit(tp, tough.mob).compareTo(new BigDecimal("0.25")));
        assertEquals(0, GameEngine.effCrit(pp, plain.mob).compareTo(new BigDecimal("0.5")));
    }

    @Test
    void dealDamageMatchesExpectation() {
        PlayerState tough = fighter("tenacity");
        BigDecimal before = tough.mob.curHP;
        // 用 9 拳避开第 10 拳的弱点打击差额（那是另一套口径）
        GameEngine.dealDamage(tough, PanelCalculator.of(tough), Num.of(9), 1.0);
        BigDecimal dealt = before.subtract(tough.mob.curHP);
        assertTrue(Math.abs(dealt.doubleValue() - 1125.0) < 0.01,
                "9 拳应打 1125（坚韧口径），实际 " + dealt);
    }
}
