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
 * 第五批第 9 轮：新被动「巨人杀手」——对 Boss 伤害 +20%，对普通怪无效。
 * 与 TraitThornsTest 同款夹具：PATK 恰好 100、暴击期望归零，10 拳定伤。
 */
class BossHunterTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fighter(boolean boss, boolean withPassive) {
        PlayerState s = NewState.create();
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = boss ? "boss_x" : "mob_x";
        m.name = "目标";
        m.boss = boss;
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
        if (withPassive) {
            s.passives.add("boss_hunter");
        }
        return s;
    }

    @Test
    void bossTakesTwentyPercentMore() {
        PlayerState plain = fighter(true, false);
        GameEngine.dealDamage(plain, PanelCalculator.of(plain), new BigDecimal("10"), 1.0);
        assertEquals(0, plain.mob.curHP.compareTo(Num.of(99000)), "无被动：10 拳 ×100 = 1000 伤");
        PlayerState hunter = fighter(true, true);
        GameEngine.dealDamage(hunter, PanelCalculator.of(hunter), new BigDecimal("10"), 1.0);
        assertEquals(0, hunter.mob.curHP.compareTo(Num.of(98800)), "有被动：应 1200 伤（+20%）");
    }

    @Test
    void normalMobUnaffected() {
        PlayerState hunter = fighter(false, true);
        GameEngine.dealDamage(hunter, PanelCalculator.of(hunter), new BigDecimal("10"), 1.0);
        assertEquals(0, hunter.mob.curHP.compareTo(Num.of(99000)), "普通怪不应吃巨人杀手加成");
    }
}
