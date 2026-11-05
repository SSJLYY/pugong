package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十二批集群三：金钟/腐蚀词条、天罚、归墟体、归墟琼浆。 */
class Batch12CombatTest {

    private static PlayerState.MobState mob(String trait, String hp) {
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "guixu_wraith";
        m.name = "测试怪";
        m.maxHP = new BigDecimal(hp);
        m.curHP = new BigDecimal(hp);
        m.trait = trait;
        return m;
    }

    private static PlayerState fighter() {
        PlayerState s = NewState.create();
        s.fixed.CRIT = BigDecimal.ZERO;
        s.fixed.CRITD = BigDecimal.ONE;
        s.life.PATK = new BigDecimal("10000");
        return s;
    }

    @Test
    void goldenBellBlocksFirstFrameOnly() {
        PlayerState s = fighter();
        PlayerState.MobState m = mob("golden_bell", "100000000");
        s.mob = m;
        PanelCalculator.Panel p = PanelCalculator.of(s);
        int kills = GameEngine.dealDamage(s, p, BigDecimal.TEN, 1.0);
        assertEquals(0, kills);
        assertEquals(0, new BigDecimal("100000000").compareTo(m.curHP), "金钟应挡下第一帧全部伤害");
        assertTrue(m.bellUsed);
        // 第二帧正常造成伤害
        GameEngine.dealDamage(s, p, BigDecimal.TEN, 1.0);
        assertTrue(m.curHP.compareTo(new BigDecimal("100000000")) < 0, "第二帧应正常掉血");
        // 一次性增益不被金钟吞掉：先挂万剑归宗，金钟帧后 buff 仍在
        PlayerState s2 = fighter();
        PlayerState.MobState m2 = mob("golden_bell", "100000000");
        s2.mob = m2;
        s2.buffs.put("sword_rain", 30.0);
        GameEngine.dealDamage(s2, PanelCalculator.of(s2), BigDecimal.TEN, 1.0);
        assertTrue(s2.buffs.containsKey("sword_rain"), "金钟不应消费一次性增益");
    }

    @Test
    void corrodeStacksReduceDefense() {
        PlayerState s = NewState.create();
        s.life.maxHP = new BigDecimal("100000000");
        s.life.curHP = new BigDecimal("100000000");
        s.life.PDEF = new BigDecimal("1000");
        PlayerState.MobState m = mob("corrode", "100000000");
        m.PATK = new BigDecimal("5000");
        m.ASPD = 1.0;
        s.mob = m;
        PanelCalculator.Panel p = PanelCalculator.of(s);
        BigDecimal hp0 = s.life.curHP;
        GameEngine.mobAttack(s, p, 1.0);
        BigDecimal dmg1 = hp0.subtract(s.life.curHP);
        assertEquals(1, m.corrodeStacks, "命中后应叠 1 层");
        BigDecimal hp1 = s.life.curHP;
        GameEngine.mobAttack(s, p, 1.0);
        BigDecimal dmg2 = hp1.subtract(s.life.curHP);
        assertEquals(2, m.corrodeStacks);
        assertTrue(dmg2.compareTo(dmg1) > 0, "腐蚀层数越高承伤越高：" + dmg1 + " -> " + dmg2);
    }

    @Test
    void heavenWrathConsumesForSixfold() {
        new com.gongpu.cfg.GameData(null, null, null, null, null, null, null);
        PlayerState s = fighter();
        s.life.curMP = new BigDecimal("1000");
        PlayerState.MobState m = mob(null, "100000000000");
        s.mob = m;
        PanelCalculator.Panel p = PanelCalculator.of(s);
        BigDecimal plain = GameEngine.expectedPerHit(s, p, m);
        GameService.castSkill(s, "heaven_wrath");
        assertTrue(s.buffs.containsKey("heaven_wrath"));
        // 直接验 dealDamage 口径：带 buff 一帧的总伤害应约为不带 buff 的 6 倍（同 perHit 基数）
        PlayerState s2 = fighter();
        PlayerState.MobState m2 = mob(null, "100000000000");
        s2.mob = m2;
        GameEngine.dealDamage(s2, PanelCalculator.of(s2), BigDecimal.ONE, 0.1);
        BigDecimal dmgPlain = new BigDecimal("100000000000").subtract(m2.curHP);
        GameEngine.dealDamage(s, p, BigDecimal.ONE, 0.1);
        BigDecimal dmgWrath = new BigDecimal("100000000000").subtract(m.curHP);
        assertTrue(!s.buffs.containsKey("heaven_wrath"), "天罚应被消费");
        assertEquals(0, dmgWrath.compareTo(dmgPlain.multiply(new BigDecimal("6"))), "天罚一击应为 6 倍：" + dmgPlain + " -> " + dmgWrath);
        assertTrue(plain.signum() > 0);
    }

    @Test
    void guixuBodyAndPotionPanel() {
        PlayerState s = NewState.create();
        BigDecimal crit0 = PanelCalculator.of(s).CRIT();
        s.passives.add("guixu_body");
        assertEquals(0, PanelCalculator.of(s).CRIT().subtract(crit0).compareTo(new BigDecimal("0.05")), "归墟体暴击 +0.05");
        BigDecimal critd0 = PanelCalculator.of(s).CRITD();
        BigDecimal steal0 = PanelCalculator.of(s).LIFESTEAL();
        s.buffs.put("guixu_pot", 30.0);
        assertEquals(0, PanelCalculator.of(s).CRITD().subtract(critd0).compareTo(new BigDecimal("0.80")), "归墟琼浆暴伤 +0.80");
        assertEquals(0, PanelCalculator.of(s).LIFESTEAL().subtract(steal0).compareTo(new BigDecimal("0.08")), "归墟琼浆吸血 +0.08");
    }

    @Test
    void traitCountersAndPools() throws Exception {
        new com.gongpu.cfg.GameData(null, null, null, null, null, null, null);
        PlayerState s = fighter();
        PlayerState.MobState m = mob("corrode", "1");
        s.mob = m;
        Method kill = GameEngine.class.getDeclaredMethod("onMobKilled", PlayerState.class, PlayerState.MobState.class);
        kill.setAccessible(true);
        kill.invoke(null, s, m);
        assertEquals(1, s.stats.corrodeKills);
        assertTrue(GameConst.traitPool(12).contains("golden_bell"));
        assertTrue(GameConst.traitPool(10).contains("corrode"));
        assertEquals(29, GameConst.MOB_TRAITS.size(), "怪物词条 28");
        assertEquals(42, GameConst.ACTIVE_SKILLS.size() + GameConst.PASSIVE_SKILLS.size(), "技能 42");
        assertEquals(47, GameConst.POTIONS.size(), "药剂 47");
    }
}
