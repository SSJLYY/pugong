package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十三批集群九：词条 +2、源流之瞳、太初之心、鲲鹏与计数终版预演。 */
class Batch13ContentTest {

    @Test
    void contentCountsReachFinalShape() {
        assertEquals(36, GameConst.AFFIXES.size(), "词条 36");
        assertEquals(45, GameConst.ACTIVE_SKILLS.size() + GameConst.PASSIVE_SKILLS.size(), "技能 45");
        assertEquals(27, GameConst.PASSIVE_SKILLS.size(), "被动 27");
        assertEquals(16, GameConst.TALENTS.size(), "天赋 16");
        assertEquals(10, GameConst.PETS.size(), "宠物 10");
        assertTrue(GameConst.PETS.containsKey("pet_kunpeng"));
        assertTrue(GameConst.AFFIXES.stream().anyMatch(a -> a.name().equals("源初") && a.key().equals("MATK")));
        assertTrue(GameConst.AFFIXES.stream().anyMatch(a -> a.name().equals("星源") && a.key().equals("REGEN")));
    }

    @Test
    void yuanliuEyeAddsAccuracy() {
        PlayerState plain = NewState.create();
        plain.fixed.CRIT = BigDecimal.ZERO;
        plain.fixed.ACC = BigDecimal.ZERO;
        PlayerState s = NewState.create();
        s.fixed.CRIT = BigDecimal.ZERO;
        s.fixed.ACC = BigDecimal.ZERO;
        s.passives.add("yuanliu_eye");
        BigDecimal diff = PanelCalculator.of(s).ACC().subtract(PanelCalculator.of(plain).ACC());
        assertEquals(0, new BigDecimal("0.08").compareTo(diff), "源流之瞳命中应 +0.08");
    }

    @Test
    void taichuHeartMultipliesMasteryBonus() {
        PlayerState s = NewState.create();
        s.zone = "plain";
        s.stats.zoneKills.put("plain", 5000);
        assertEquals(1.20, GameEngine.zoneMasteryMul(s), 1e-9, "无天赋满档 +20%");
        s.talent = "taichu_heart";
        assertEquals(1.30, GameEngine.zoneMasteryMul(s), 1e-9, "太初之心满档应 +30%");
        PlayerState mid = NewState.create();
        mid.zone = "plain";
        mid.stats.zoneKills.put("plain", 1000);
        mid.talent = "taichu_heart";
        assertEquals(1.06, GameEngine.zoneMasteryMul(mid), 1e-9, "两档 +4% ×1.5 = +6%");
    }

    @Test
    void taichuAchievementsEvaluate() {
        PlayerState s = NewState.create();
        s.usedPotions.add("taichu");
        s.stats.phantomKills = 50;
        assertTrue(Achievements.evaluate(s).contains("taichu_1"));
        assertTrue(Achievements.evaluate(s).contains("phantom_50"));
    }
}
