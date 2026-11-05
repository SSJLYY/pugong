package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十批战斗深化：镜像反弹、虚弱打折、时停、时之体、混沌精华、词条池扩容。 */
class Batch10CombatTest {

    @BeforeAll
    static void initData() {
        new com.gongpu.cfg.GameData(null, null, null, null, null, null, null);
    }

    private static PlayerState.MobState mob(String trait, String hp) {
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "dummy_beast";
        m.name = "测试兽";
        m.maxHP = new BigDecimal(hp);
        m.curHP = new BigDecimal(hp);
        m.PATK = BigDecimal.ZERO;
        m.MATK = BigDecimal.ZERO;
        m.PDEF = BigDecimal.ZERO;
        m.MDEF = BigDecimal.ZERO;
        m.ASPD = 1.0;
        m.trait = trait;
        return m;
    }

    private static PlayerState fighter() {
        PlayerState s = NewState.create();
        s.zone = "chaos";
        s.life.maxHP = new BigDecimal("1000000");
        s.life.curHP = new BigDecimal("1000000");
        s.life.PATK = new BigDecimal("1000");
        s.fixed.CRIT = BigDecimal.ZERO;
        s.fixed.CRITD = BigDecimal.ONE;
        return s;
    }

    @Test
    void weakenDiscountsDamage() {
        PlayerState s = fighter();
        s.mob = mob(null, "100000000");
        PanelCalculator.Panel p = PanelCalculator.of(s);
        BigDecimal base = GameEngine.expectedPerHit(s, p, s.mob);
        s.mob = mob("weaken", "100000000");
        BigDecimal weakened = GameEngine.expectedPerHit(s, p, s.mob);
        assertEquals(0, weakened.compareTo(base.multiply(new BigDecimal("0.85"))), "虚弱应使每拳 ×0.85");
    }

    @Test
    void mirrorReflectsCappedDamage() {
        PlayerState s = fighter();
        s.mob = mob("mirror", "500"); // 怪只有 500 血：反弹基数封顶 500
        PanelCalculator.Panel p = PanelCalculator.of(s);
        BigDecimal before = s.life.curHP;
        GameEngine.dealDamage(s, p, BigDecimal.ONE, 1.0);
        BigDecimal lost = before.subtract(s.life.curHP);
        assertEquals(0, lost.compareTo(new BigDecimal("50")), "镜像反弹 = 实际承受 500 × 10%");
    }

    @Test
    void timeStopHalvesMobAttack() {
        PlayerState s = fighter();
        s.mob = mob(null, "100000000");
        s.mob.PATK = new BigDecimal("100");
        PanelCalculator.Panel p = PanelCalculator.of(s);
        BigDecimal hp0 = s.life.curHP;
        GameEngine.mobAttack(s, p, 10);
        BigDecimal lostNormal = hp0.subtract(s.life.curHP);
        s.life.curHP = hp0;
        s.buffs.put("time_stop", 10.0);
        GameEngine.mobAttack(s, p, 10);
        BigDecimal lostStopped = hp0.subtract(s.life.curHP);
        assertTrue(lostNormal.signum() > 0, "对照组应掉血");
        assertEquals(0, lostStopped.compareTo(lostNormal.divide(BigDecimal.valueOf(2))),
                "时停应使怪物伤害减半");
    }

    @Test
    void chronoBodyAddsAspd() {
        PlayerState s = fighter();
        BigDecimal base = PanelCalculator.of(s).ASPD();
        s.passives.add("chrono_body");
        BigDecimal with = PanelCalculator.of(s).ASPD();
        assertEquals(0, with.compareTo(base.add(new BigDecimal("0.08"))), "时之体攻速 +0.08");
    }

    @Test
    void chaosPotionBoostsCritdAndEva() {
        PlayerState s = fighter();
        BigDecimal cd0 = PanelCalculator.of(s).CRITD();
        BigDecimal ev0 = PanelCalculator.of(s).EVA();
        s.buffs.put("chaos_pot", 60.0);
        assertEquals(0, PanelCalculator.of(s).CRITD().compareTo(cd0.add(new BigDecimal("0.40"))));
        assertEquals(0, PanelCalculator.of(s).EVA().compareTo(ev0.add(new BigDecimal("0.05"))));
    }

    @Test
    void poolsIncludeNewTraits() {
        assertEquals(32, GameConst.MOB_TRAITS.size(), "怪物词条 25");
        assertTrue(GameConst.traitPool(10).contains("mirror"), "混沌池含镜像");
        assertTrue(GameConst.traitPool(10).contains("weaken"), "混沌池含虚弱");
        assertTrue(GameConst.traitPool(5).contains("mirror"), "深区池含镜像");
        assertEquals(28, GameConst.traitPool(10).size(), "混沌池 26 条");
        assertEquals(20, GameConst.traitPool(5).size(), "深区池 20 条");
    }

    @Test
    void newSkillsRegistered() {
        assertEquals(45, GameConst.ACTIVE_SKILLS.size() + GameConst.PASSIVE_SKILLS.size(), "技能 38");
        assertTrue(GameConst.ACTIVE_SKILLS.containsKey("time_stop"));
        assertTrue(GameConst.PASSIVE_SKILLS.containsKey("chrono_body"));
    }
}
