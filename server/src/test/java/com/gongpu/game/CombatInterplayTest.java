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
 * 第七批第 48 轮：战斗机制联动回归——血怒×冲锋、弱点×重击、狂暴词条×Boss 二阶段，
 * 锁定新机制与既有乘区的叠加口径，防止后续改动悄悄漂移。
 */
class CombatInterplayTest {

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
        s.fixed.CRIT = BigDecimal.ZERO;
        s.fixed.CRITD = new BigDecimal("2");
        s.life.maxHP = new BigDecimal("10000");
        s.life.curHP = new BigDecimal("10000");
        s.life.curMP = Num.of(100);
        return s;
    }

    @Test
    void bloodrageStacksWithCharge() {
        GameService game = new GameService();
        PlayerState s = fighter();
        s.passives.add("bloodrage");
        s.life.curHP = new BigDecimal("2000");   // 20% 血 → 血怒触发，面板物攻 115
        game.castSkill(s, "charge");
        BigDecimal before = s.mob.curHP;
        GameEngine.dealDamage(s, PanelCalculator.of(s), BigDecimal.ONE, 1.0);
        assertEquals(0, before.subtract(s.mob.curHP).compareTo(new BigDecimal("345")),
                "血怒 115 × 冲锋 3 = 345");
    }

    @Test
    void weakPointScalesWithHeavy() {
        GameService game = new GameService();
        PlayerState s = fighter();
        game.castSkill(s, "heavy");
        BigDecimal before = s.mob.curHP;
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("10"), 1.0);
        // 重击使整帧每拳 500；弱点拳按暴击比放大为 1000：总伤 = 500×10 + (1000-500) = 5500
        assertEquals(0, before.subtract(s.mob.curHP).compareTo(new BigDecimal("5500")),
                "弱点差额应随重击放大后的每拳伤害同比例放大");
        assertEquals(1, s.stats.weakCrits);
    }

    @Test
    void frenzyTraitStacksWithBossPhase2() {
        PlayerState s = NewState.create();
        s.zone = "plain";
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "boss_boar";
        m.name = "野猪王";
        m.emoji = "🐗";
        m.boss = true;
        m.trait = "frenzy";
        m.maxHP = new BigDecimal("10000");
        m.curHP = new BigDecimal("2000");   // 20%：狂暴触发（<30%）且已过二阶段血线
        m.PATK = new BigDecimal("100");
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        m.ASPD = 1.0;
        m.SPD = 1;
        s.mob = m;
        s.life.maxHP = new BigDecimal("1000000");
        s.life.curHP = new BigDecimal("1000000");
        s.life.set("PDEF", Num.ZERO);
        s.life.set("MDEF", Num.ZERO);
        s.fixed.EVA = BigDecimal.ZERO;
        s.fixed.SPD = new BigDecimal("9999");
        GameEngine.checkBossPhase(s);
        BigDecimal before = s.life.curHP;
        GameEngine.mobAttack(s, PanelCalculator.of(s), 1.0);
        // 命中 1 × 狂暴 1.5 × 二阶段攻速 1.3 = 1.95 次；每击 100 × 二阶段攻击 1.2 = 120 → 234（命中数为 double，容忍浮点尘）
        assertEquals(234.0, before.subtract(s.life.curHP).doubleValue(), 0.01,
                "狂暴词条与二阶段攻速/攻击应叠乘");
    }
}
