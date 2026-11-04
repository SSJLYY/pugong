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
 * 第八批第 14 轮：主动「连环掌」——施放得 30 次双击额度，命中逐次消耗，
 * 追加命中同额结算并计入 Stats.flurryExtra；额度耗尽后恢复常态。
 */
class FlurryTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fighter() {
        PlayerState s = NewState.create();
        s.life.set("PATK", new BigDecimal("100"));
        s.life.curMP = new BigDecimal("1000");
        s.fixed.CRIT = BigDecimal.ZERO;
        s.fixed.CRITD = BigDecimal.ONE; // 中性化弱点打击差额（既定 fixture 口径）
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "flurry_dummy";
        m.name = "连环桩";
        m.maxHP = new BigDecimal("1000000000");
        m.curHP = new BigDecimal("1000000000");
        s.mob = m;
        return s;
    }

    @Test
    void castGrantsThirtyDoubleHits() {
        PlayerState s = fighter();
        GameService.castSkill(s, "flurry");
        assertEquals(30.0, s.flurryHits, 0.001, "施放应赋予 30 次双击");
        PanelCalculator.Panel p = PanelCalculator.of(s);
        // 4 拳：4 次常规 + 4 次追加 = 800 伤
        BigDecimal before = s.mob.curHP;
        GameEngine.dealDamage(s, p, Num.of(4), 1.0);
        assertEquals(0, before.subtract(s.mob.curHP).compareTo(Num.of(800)), "双击应翻倍伤害");
        assertEquals(26.0, s.flurryHits, 0.001, "应消耗 4 次额度");
        assertEquals(0, s.stats.flurryExtra.compareTo(Num.of(4)));
        // 40 拳打完剩余额度 26：总伤 = (40+26)×100
        before = s.mob.curHP;
        GameEngine.dealDamage(s, PanelCalculator.of(s), Num.of(40), 1.0);
        assertEquals(0, before.subtract(s.mob.curHP).compareTo(Num.of(6600)), "额度耗尽后只剩常规命中");
        assertEquals(0.0, s.flurryHits, 0.001);
        assertTrue(s.stats.flurryExtra.compareTo(Num.of(30)) == 0, "累计追加应为 30");
    }
}
