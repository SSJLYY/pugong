package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十二批集群九：归墟词条/潮汐之体/星辰之心/雷霆 + 终版计数预检。 */
class Batch12ContentTest {

    @Test
    void finalContentCounts() {
        assertEquals(36, GameConst.AFFIXES.size(), "词条 34");
        assertEquals(45, GameConst.ACTIVE_SKILLS.size() + GameConst.PASSIVE_SKILLS.size(), "技能 42");
        assertEquals(18, GameConst.ACTIVE_SKILLS.size(), "主动 17");
        assertEquals(27, GameConst.PASSIVE_SKILLS.size(), "被动 25");
        assertEquals(32, GameConst.MOB_TRAITS.size(), "怪物词条 29");
        assertEquals(16, GameConst.TALENTS.size(), "天赋 15");
        assertEquals(143, Achievements.ALL.size(), "成就 128");
        assertEquals(30, GameConst.traitPool(12).size(), "归墟池 28（鸿蒙 27 + 雷霆）");
        assertEquals(29, GameConst.traitPool(11).size(), "鸿蒙池 27");
        assertTrue(GameConst.traitPool(12).contains("thunder"));
        assertTrue(!GameConst.traitPool(11).contains("thunder"), "雷霆仅归墟");
    }

    @Test
    void tideBodyEva() {
        PlayerState s = NewState.create();
        BigDecimal eva0 = PanelCalculator.of(s).EVA();
        s.passives.add("tide_body");
        assertEquals(0, PanelCalculator.of(s).EVA().subtract(eva0).compareTo(new BigDecimal("0.06")), "潮汐之体闪避 +0.06");
    }

    @Test
    void starHeartStacksWithGemAffinityPath() {
        // 星辰之心：宝石效果 ×1.5（单天赋口径）；与宝石亲和不同天赋不可同持，此处只验单天赋乘区
        PlayerState s = NewState.create();
        s.talent = "star_heart";
        PlayerState.Item it = new PlayerState.Item();
        it.name = "测试剑";
        it.slot = "weapon";
        it.quality = 3;
        it.base.put("PATK", new BigDecimal("100"));
        it.gems.add("gem_ruby");
        s.equipped.put("weapon", it);
        PlayerState plain = NewState.create();
        PlayerState.Item it2 = new PlayerState.Item();
        it2.name = "测试剑";
        it2.slot = "weapon";
        it2.quality = 3;
        it2.base.put("PATK", new BigDecimal("100"));
        it2.gems.add("gem_ruby");
        plain.equipped.put("weapon", it2);
        // 同底子对照：星辰之心比无天赋多出 60 × 0.5 = 30 点（宝石 +60 被放大为 +90）
        assertEquals(0, PanelCalculator.of(s).PATK().subtract(PanelCalculator.of(plain).PATK())
                .compareTo(new BigDecimal("30")), "星辰之心应把宝石效果 ×1.5");
    }

    @Test
    void thunderExtraHit() {
        PlayerState s = NewState.create();
        s.life.maxHP = new BigDecimal("10000000");
        s.life.curHP = new BigDecimal("10000000");
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "guixu_wraith";
        m.maxHP = new BigDecimal("100000000");
        m.curHP = new BigDecimal("100000000");
        m.trait = "thunder";
        m.PATK = new BigDecimal("10000");
        m.ASPD = 1.0;
        s.mob = m;
        PanelCalculator.Panel p = PanelCalculator.of(s);
        BigDecimal hp0 = s.life.curHP;
        GameEngine.mobAttack(s, p, 1.0);
        BigDecimal totalDmg = hp0.subtract(s.life.curHP);
        // 主伤害约 10000 + 雷霆 3000 = 13000（帧内有少量回复结算，容差 20）
        assertTrue(totalDmg.compareTo(new BigDecimal("12980")) > 0 && totalDmg.compareTo(new BigDecimal("13000")) <= 0,
                "雷霆额外一击应为 PATK×0.3：" + totalDmg);
    }

    @Test
    void thunderAndMastery10kAchievements() {
        PlayerState s = NewState.create();
        s.stats.thunderKills = 10;
        s.stats.zoneKills.put("guixu", 10000);
        var got = Achievements.evaluate(s);
        assertTrue(got.contains("thunder_10"));
        assertTrue(got.contains("mastery_10k"));
    }
}
