package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第六批第 58 轮：击杀预估——每拳期望与 dealDamage 同口径，fullView 下发 estPunches。 */
class EstPunchesTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private static PlayerState.MobState mob(long hp, boolean boss) {
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "est_x";
        m.name = "预估靶";
        m.boss = boss;
        m.maxHP = Num.of(hp);
        m.curHP = Num.of(hp);
        m.PATK = Num.ZERO;
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        m.gold = 1;
        return m;
    }

    @Test
    void perHitAndEstimate() {
        PlayerState s = NewState.create();
        s.life.set("PATK", new BigDecimal("100"));
        s.fixed.CRIT = BigDecimal.ZERO;
        s.mob = mob(950, false);
        BigDecimal perHit = GameEngine.expectedPerHit(s, PanelCalculator.of(s), s.mob);
        assertEquals(0, new BigDecimal("100").compareTo(perHit), "无防无暴时每拳期望应 = 物攻 100");
        Map<String, Object> view = new GameService().fullView(s);
        @SuppressWarnings("unchecked")
        Map<String, Object> mv = (Map<String, Object>) view.get("mob");
        assertEquals(10L, mv.get("estPunches"), "950 血 / 100 每拳 → 10 拳");
    }

    @Test
    void bossHunterRaisesPerHit() {
        PlayerState s = NewState.create();
        s.life.set("PATK", new BigDecimal("100"));
        s.fixed.CRIT = BigDecimal.ZERO;
        s.passives.add("boss_hunter");
        s.mob = mob(1000, true);
        BigDecimal perHit = GameEngine.expectedPerHit(s, PanelCalculator.of(s), s.mob);
        assertEquals(0, new BigDecimal("120").compareTo(perHit), "巨人杀手对 Boss 应 ×1.2");
    }
}
