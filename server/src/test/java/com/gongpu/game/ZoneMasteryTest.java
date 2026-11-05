package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十二批集群五：区域精通（击杀累加、地利双口径、封顶、视图与成就）。 */
class ZoneMasteryTest {

    @Test
    void masteryMulTiers() {
        PlayerState s = NewState.create();
        s.zone = "plain";
        assertEquals(1.0, GameEngine.zoneMasteryMul(s), 1e-9);
        s.stats.zoneKills.put("plain", 499);
        assertEquals(1.0, GameEngine.zoneMasteryMul(s), 1e-9, "不足 500 无加成");
        s.stats.zoneKills.put("plain", 500);
        assertEquals(1.02, GameEngine.zoneMasteryMul(s), 1e-9, "500 杀 +2%");
        s.stats.zoneKills.put("plain", 5000);
        assertEquals(1.20, GameEngine.zoneMasteryMul(s), 1e-9, "5000 杀封顶 +20%");
        s.stats.zoneKills.put("plain", 99999);
        assertEquals(1.20, GameEngine.zoneMasteryMul(s), 1e-9, "超量仍封顶");
        // 只认当前区域
        s.zone = "forest";
        assertEquals(1.0, GameEngine.zoneMasteryMul(s), 1e-9);
    }

    @Test
    void masteryAppliesToBothDamagePaths() throws Exception {
        new com.gongpu.cfg.GameData(null, null, null, null, null, null, null);
        PlayerState s = NewState.create();
        s.zone = "guixu";
        s.fixed.CRIT = BigDecimal.ZERO;
        s.fixed.CRITD = BigDecimal.ONE;
        s.life.PATK = new BigDecimal("1000000");
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "guixu_wraith";
        m.name = "归墟游魂";
        m.maxHP = new BigDecimal("100000000000000");
        m.curHP = new BigDecimal("100000000000000");
        s.mob = m;
        PanelCalculator.Panel p = PanelCalculator.of(s);
        BigDecimal base = GameEngine.expectedPerHit(s, p, m);
        s.stats.zoneKills.put("guixu", 2500);
        BigDecimal boosted = GameEngine.expectedPerHit(s, p, m);
        assertEquals(0, boosted.compareTo(base.multiply(new BigDecimal("1.10"))), "2500 杀应 +10%：" + base + " -> " + boosted);
        // 实打口径一致：一帧总伤害同样 ×1.10
        PlayerState s2 = NewState.create();
        s2.zone = "guixu";
        s2.fixed.CRIT = BigDecimal.ZERO;
        s2.fixed.CRITD = BigDecimal.ONE;
        s2.life.PATK = new BigDecimal("1000000");
        PlayerState.MobState m2 = new PlayerState.MobState();
        m2.id = "guixu_wraith";
        m2.maxHP = new BigDecimal("100000000000000");
        m2.curHP = new BigDecimal("100000000000000");
        s2.mob = m2;
        GameEngine.dealDamage(s2, PanelCalculator.of(s2), BigDecimal.ONE, 0.1);
        BigDecimal dmgBase = new BigDecimal("100000000000000").subtract(m2.curHP);
        GameEngine.dealDamage(s, p, BigDecimal.ONE, 0.1);
        BigDecimal dmgBoost = new BigDecimal("100000000000000").subtract(m.curHP);
        assertEquals(0, dmgBoost.compareTo(dmgBase.multiply(new BigDecimal("1.10"))), "实打也应 +10%");
    }

    @Test
    void killsAccumulatePerZone() throws Exception {
        new com.gongpu.cfg.GameData(null, null, null, null, null, null, null);
        PlayerState s = NewState.create();
        s.zone = "guixu";
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "guixu_wraith";
        m.maxHP = BigDecimal.ONE;
        m.curHP = BigDecimal.ONE;
        s.mob = m;
        Method kill = GameEngine.class.getDeclaredMethod("onMobKilled", PlayerState.class, PlayerState.MobState.class);
        kill.setAccessible(true);
        kill.invoke(null, s, m);
        assertEquals(1, s.stats.zoneKills.get("guixu"));
    }

    @Test
    void masteryViewAndAchievements() {
        PlayerState s = NewState.create();
        s.stats.zoneKills.put("chaos", 5000);
        var game = new GameService();
        var view = game.fullView(s);
        assertTrue(view.containsKey("zoneKills"), "fullView 必须下发 zoneKills");
        assertEquals(5000, ((java.util.Map<?, ?>) view.get("zoneKills")).get("chaos"));
        assertTrue(Achievements.evaluate(s).contains("mastery_first"));
        assertTrue(Achievements.evaluate(s).contains("mastery_max"));
        assertEquals(5000, Achievements.maxZoneKills(s));
    }
}
