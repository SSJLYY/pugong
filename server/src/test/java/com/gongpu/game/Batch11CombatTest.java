package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十一批战斗深化：不朽复苏、吞噬真伤、万剑归宗、鸿蒙体、鸿蒙灵液。 */
class Batch11CombatTest {

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
        s.zone = "hongmeng";
        s.life.maxHP = new BigDecimal("1000000");
        s.life.curHP = new BigDecimal("1000000");
        s.life.PATK = new BigDecimal("1000");
        s.fixed.CRIT = BigDecimal.ZERO;
        s.fixed.CRITD = BigDecimal.ONE;
        return s;
    }

    @Test
    void immortalSurvivesFirstLethalBlowAtThirtyPercent() {
        PlayerState s = fighter();
        PlayerState.MobState m = mob("immortal", "1000");
        s.mob = m;
        PanelCalculator.Panel p = PanelCalculator.of(s);
        // 一拳 1000 恰好致死：不朽触发，保留 30% = 300，且溢出为 0 不当场真杀
        GameEngine.dealDamage(s, p, BigDecimal.ONE, 1.0);
        assertTrue(m.immortalUsed, "不朽应已触发");
        assertEquals(0, m.curHP.compareTo(new BigDecimal("300")), "复苏保留 30% 生命");
        assertEquals(0, s.stats.immortalKills, "尚未真杀不计数");
        // 第二拳真杀
        GameEngine.dealDamage(s, p, BigDecimal.ONE, 1.0);
        assertEquals(1, s.stats.immortalKills, "真杀后计数 +1");
    }

    @Test
    void immortalOverflowKillsInSameFrame() {
        PlayerState s = fighter();
        PlayerState.MobState m = mob("immortal", "1000");
        s.mob = m;
        PanelCalculator.Panel p = PanelCalculator.of(s);
        // 10 拳 ×1000：先触发不朽（吃掉致死 1000），剩余伤害同帧真杀（300 血）
        GameEngine.dealDamage(s, p, new BigDecimal("10"), 1.0);
        assertEquals(1, s.stats.immortalKills, "溢出伤害应当帧真杀不朽怪");
    }

    @Test
    void devourDrainsTwoPercentMaxHp() {
        PlayerState s = fighter();
        s.mob = mob("devour", "100000000");
        s.mob.PATK = BigDecimal.ZERO;
        s.mob.MATK = BigDecimal.ZERO;
        PanelCalculator.Panel p = PanelCalculator.of(s);
        BigDecimal hp0 = s.life.curHP;
        GameEngine.mobAttack(s, p, 10);
        BigDecimal lost = hp0.subtract(s.life.curHP);
        // 吞噬真伤 = 1,000,000 × 2% = 20,000（怪本身攻击为 0 档另有至少 1 点/击的主伤害，只断言吞噬量级）
        assertTrue(lost.compareTo(new BigDecimal("20000")) >= 0, "吞噬应至少扣 2% 生命上限，实际 " + lost);
        assertTrue(lost.compareTo(new BigDecimal("20010")) <= 0, "不应远超吞噬口径，实际 " + lost);
    }

    @Test
    void swordRainMultipliesNextHitByFive() {
        PlayerState s = fighter();
        s.mob = mob(null, "100000000");
        PanelCalculator.Panel p = PanelCalculator.of(s);
        BigDecimal base = GameEngine.expectedPerHit(s, p, s.mob);
        // 施放技能挂 buff（castSkill 分支），下一击消费并 ×5
        GameService.castSkill(s, "sword_rain");
        assertTrue(s.buffs.containsKey("sword_rain"), "施放后应挂 sword_rain buff");
        BigDecimal hp0 = s.mob.curHP;
        GameEngine.dealDamage(s, PanelCalculator.of(s), BigDecimal.ONE, 1.0);
        BigDecimal dealt = hp0.subtract(s.mob.curHP);
        assertEquals(0, dealt.compareTo(base.multiply(new BigDecimal("5"))), "万剑归宗下一击应 ×5，实际 " + dealt);
        assertTrue(!s.buffs.containsKey("sword_rain"), "一次性 buff 应被消费");
    }

    @Test
    void hongmengBodyAddsFlatAspd() {
        PlayerState s = fighter();
        BigDecimal base = PanelCalculator.of(s).ASPD();
        s.passives.add("hongmeng_body");
        BigDecimal with = PanelCalculator.of(s).ASPD();
        assertTrue(with.compareTo(base) > 0, "鸿蒙体应提升攻速");
        assertEquals(0, with.subtract(base).compareTo(new BigDecimal("0.1")), "平直攻速 +0.10");
    }

    @Test
    void hongmengPotionBoostsCritdAndSteal() {
        PlayerState s = fighter();
        s.usedPotions.add("hongmeng");
        PanelCalculator.Panel base = PanelCalculator.of(s);
        s.buffs.put("hongmeng_pot", 60.0);
        PanelCalculator.Panel buffed = PanelCalculator.of(s);
        assertEquals(0, buffed.CRITD().subtract(base.CRITD()).compareTo(new BigDecimal("0.6")), "暴伤 +0.60");
        assertEquals(0, buffed.LIFESTEAL().subtract(base.LIFESTEAL()).compareTo(new BigDecimal("0.06")), "吸血 +0.06");
    }

    @Test
    void deepPoolsIncludeImmortalAndDevour() {
        assertTrue(GameConst.traitPool(10).contains("immortal"), "混沌池应含不朽");
        assertTrue(GameConst.traitPool(11).contains("devour"), "鸿蒙池应含吞噬");
        assertEquals(26, GameConst.MOB_TRAITS.size(), "怪物词条 25");
    }
}
