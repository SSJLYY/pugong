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
 * 第八批第 17 轮：新机制联动回归——
 * ① 冲锋 × 连环掌：冲锋的 ×3 先落 perHit，连环追加命中同享该 perHit（首帧双暴）；
 * ② 坚韧 × 弱点打击：弱点差额按坚韧调整后的 critMul 计算，不重复打折也不放大。
 */
class LinkageBatch8Test {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fighter(String trait) {
        PlayerState s = NewState.create();
        s.life.set("PATK", new BigDecimal("100"));
        s.fixed.CRIT = BigDecimal.ZERO;
        s.fixed.CRITD = BigDecimal.ONE;
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "link_dummy";
        m.name = "联动桩";
        m.maxHP = new BigDecimal("1000000000");
        m.curHP = new BigDecimal("1000000000");
        m.trait = trait;
        s.mob = m;
        return s;
    }

    @Test
    void chargeBoostsFlurryExtraToo() {
        PlayerState s = fighter(null);
        s.buffs.put("charge", 30.0);
        s.flurryHits = 10;
        BigDecimal before = s.mob.curHP;
        GameEngine.dealDamage(s, PanelCalculator.of(s), Num.of(4), 1.0);
        // perHit = 100×3 = 300；常规 4 拳 1200 + 连环追加 4 次 1200 = 2400
        assertEquals(0, before.subtract(s.mob.curHP).compareTo(Num.of(2400)),
                "冲锋应同时放大常规与连环追加命中");
        assertEquals(6.0, s.flurryHits, 0.001);
    }

    @Test
    void tenacityWeakCritUsesAdjustedCritMul() {
        PlayerState s = fighter("tenacity");
        s.fixed.CRIT = new BigDecimal("0.5");
        s.fixed.CRITD = new BigDecimal("2");
        // 十拳：第 10 拳弱点必暴。critMul（坚韧）= 1 + 0.25×1 = 1.25 → perHit = 125
        // 弱点拳 = 125 × (2/1.25) = 200，差额 +75；总 = 125×10 + 75 = 1325
        BigDecimal before = s.mob.curHP;
        GameEngine.dealDamage(s, PanelCalculator.of(s), Num.of(10), 1.0);
        BigDecimal dealt = before.subtract(s.mob.curHP);
        assertTrue(Math.abs(dealt.doubleValue() - 1325.0) < 0.01,
                "坚韧下弱点总伤应为 1325，实际 " + dealt);
        assertEquals(1, s.stats.weakCrits);
    }
}
