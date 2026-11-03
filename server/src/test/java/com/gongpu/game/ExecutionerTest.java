package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第七批第 85 轮：被动「处决者」——Boss 残血（<30%）时伤害 +25%；
 * 恰 30% 不触发、普通怪不触发、与巨人杀手乘算。伤害走 dealDamage 确定性口径。
 */
class ExecutionerTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fighter(boolean boss, String bossHpPct) {
        PlayerState s = NewState.create();
        s.passives.add("executioner");
        s.life.set("PATK", Num.of(100));
        s.fixed.CRIT = Num.ZERO;
        s.fixed.CRITD = Num.of(1); // 中性化弱点打击（第七批口径）
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "test_mob";
        m.name = "测试怪";
        m.boss = boss;
        m.maxHP = Num.of(1000);
        m.curHP = Num.of(1000).multiply(new java.math.BigDecimal(bossHpPct));
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        s.mob = m;
        return s;
    }

    @Test
    void bossLowHpTriggers() {
        assertTrue(GameEngine.bossLowHp(fighter(true, "0.29").mob));
        assertFalse(GameEngine.bossLowHp(fighter(true, "0.30").mob), "恰 30% 不触发（严格小于）");
        assertFalse(GameEngine.bossLowHp(fighter(true, "0.80").mob));

        PlayerState s = fighter(true, "0.29");
        s.mob.curHP = Num.of(100000); // 别一拳打死：改大血池同比例
        s.mob.maxHP = Num.of(1000000);
        GameEngine.dealDamage(s, PanelCalculator.of(s), Num.of(1), 1.0);
        assertEquals(0, s.mob.curHP.compareTo(Num.of(100000).subtract(Num.of(125))),
                "残血 Boss 应吃 +25% 处决伤害（100 → 125）");
    }

    @Test
    void bossHighHpAndNormalMobNotTriggered() {
        PlayerState hi = fighter(true, "0.80");
        hi.mob.curHP = Num.of(800);
        GameEngine.dealDamage(hi, PanelCalculator.of(hi), Num.of(1), 1.0);
        assertEquals(0, hi.mob.curHP.compareTo(Num.of(700)), "高血 Boss 不触发处决（100）");

        PlayerState normal = fighter(false, "0.10");
        normal.mob.maxHP = Num.of(10000);
        normal.mob.curHP = Num.of(150); // 1.5% 血线、非 Boss
        GameEngine.dealDamage(normal, PanelCalculator.of(normal), Num.of(1), 1.0);
        assertEquals(0, normal.mob.curHP.compareTo(Num.of(50)),
                "普通怪残血也不触发（仅 Boss）：一拳恰 100");
    }

    @Test
    void stacksWithBossHunter() {
        PlayerState s = fighter(true, "0.20");
        s.passives.add("boss_hunter");
        s.mob.maxHP = Num.of(1000000);
        s.mob.curHP = Num.of(200000);
        GameEngine.dealDamage(s, PanelCalculator.of(s), Num.of(1), 1.0);
        // 100 × 1.2（巨人杀手）× 1.25（处决者）= 150
        assertEquals(0, s.mob.curHP.compareTo(Num.of(200000).subtract(Num.of(150))));
    }
}
