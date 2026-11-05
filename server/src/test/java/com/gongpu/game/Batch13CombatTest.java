package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十三批集群四：韧皮/幻影/噬生词条、源初斩、太初体、太初源液。 */
class Batch13CombatTest {

    private static PlayerState.MobState mob(String trait, String hp) {
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "taichu_beast";
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
    void toughHideScalesDamageAndExpectation() {
        PlayerState s = fighter();
        PlayerState.MobState m = mob("tough_hide", "100000000");
        s.mob = m;
        PanelCalculator.Panel p = PanelCalculator.of(s);
        BigDecimal expected = GameEngine.expectedPerHit(s, p, m);
        GameEngine.dealDamage(s, p, BigDecimal.TEN, 1.0);
        BigDecimal dealt = new BigDecimal("100000000").subtract(m.curHP);
        // 韧皮 ×0.8：实打（10 拳）应与预估同口径（容差：面板基础 +2 与取整）
        assertEquals(0, expected.multiply(BigDecimal.TEN).setScale(0, java.math.RoundingMode.HALF_UP)
                .compareTo(dealt.setScale(0, java.math.RoundingMode.HALF_UP)), "韧皮实打应与预估一致");
        // 对照组：无词条怪同拳数伤害更高（约 ×1.25）
        PlayerState s2 = fighter();
        PlayerState.MobState m2 = mob(null, "100000000");
        s2.mob = m2;
        GameEngine.dealDamage(s2, PanelCalculator.of(s2), BigDecimal.TEN, 1.0);
        BigDecimal dealtPlain = new BigDecimal("100000000").subtract(m2.curHP);
        assertTrue(dealtPlain.compareTo(dealt) > 0, "韧皮应明显减伤");
    }

    @Test
    void phantomDodgesEveryFourthFrameWithoutEatingBuffs() {
        PlayerState s = fighter();
        PlayerState.MobState m = mob("phantom", "100000000");
        s.mob = m;
        PanelCalculator.Panel p = PanelCalculator.of(s);
        for (int i = 0; i < 3; i++) {
            GameEngine.dealDamage(s, p, BigDecimal.TEN, 1.0);
        }
        BigDecimal hpAfter3 = m.curHP;
        assertTrue(hpAfter3.compareTo(new BigDecimal("100000000")) < 0, "前三帧应正常掉血");
        assertEquals(3, m.phantomCount);
        GameEngine.dealDamage(s, p, BigDecimal.TEN, 1.0);
        assertEquals(0, hpAfter3.compareTo(m.curHP), "第 4 帧应被幻影整帧闪避");
        assertEquals(4, m.phantomCount);
        // 幻影不吞一次性增益：闪避帧挂源初斩，buff 应保留
        PlayerState s2 = fighter();
        PlayerState.MobState m2 = mob("phantom", "100000000");
        s2.mob = m2;
        PanelCalculator.Panel p2 = PanelCalculator.of(s2);
        for (int i = 0; i < 3; i++) {
            GameEngine.dealDamage(s2, p2, BigDecimal.TEN, 1.0);
        }
        s2.buffs.put("origin_slash", 30.0);
        GameEngine.dealDamage(s2, p2, BigDecimal.TEN, 1.0);
        assertTrue(s2.buffs.containsKey("origin_slash"), "幻影闪避不应消费一次性增益");
    }

    @Test
    void lifeEaterNullifiesLifesteal() {
        PlayerState s = fighter();
        s.buffs.put("guixu_pot", 60.0);   // 归墟琼浆：吸血 +0.08
        s.life.maxHP = new BigDecimal("1000000");
        s.life.curHP = new BigDecimal("1000");
        PlayerState.MobState m = mob("life_eater", "100000000");
        s.mob = m;
        GameEngine.dealDamage(s, PanelCalculator.of(s), BigDecimal.TEN, 1.0);
        assertEquals(0, new BigDecimal("1000").compareTo(s.life.curHP), "噬生应令吸血完全无效");
        // 对照组：普通怪吸血生效
        PlayerState s2 = fighter();
        s2.buffs.put("guixu_pot", 60.0);
        s2.life.maxHP = new BigDecimal("1000000");
        s2.life.curHP = new BigDecimal("1000");
        PlayerState.MobState m2 = mob(null, "100000000");
        s2.mob = m2;
        GameEngine.dealDamage(s2, PanelCalculator.of(s2), BigDecimal.TEN, 1.0);
        assertTrue(s2.life.curHP.compareTo(new BigDecimal("1000")) > 0, "普通怪应正常吸血");
    }

    @Test
    void originSlashMultipliesAndTaichuPanelWorks() {
        assertEquals(32, GameConst.MOB_TRAITS.size(), "怪物词条 32");
        assertEquals(45, GameConst.ACTIVE_SKILLS.size() + GameConst.PASSIVE_SKILLS.size(), "技能 45");
        assertEquals(18, GameConst.ACTIVE_SKILLS.size(), "主动 18");
        assertEquals(27, GameConst.PASSIVE_SKILLS.size(), "被动 27");
        assertEquals(48, GameConst.POTIONS.size(), "药剂 48");
        // 太初体：暴伤 +0.30
        PlayerState s = fighter();
        s.passives.add("taichu_body");
        PanelCalculator.Panel p = PanelCalculator.of(s);
        PlayerState plain = fighter();
        BigDecimal critdDiff = p.CRITD().subtract(PanelCalculator.of(plain).CRITD());
        assertEquals(0, new BigDecimal("0.30").compareTo(critdDiff), "太初体暴伤应 +0.30");
        // 太初源液：攻速 +0.25 / 暴击 +0.08
        PlayerState s3 = fighter();
        s3.buffs.put("taichu_pot", 60.0);
        PanelCalculator.Panel p3 = PanelCalculator.of(s3);
        assertTrue(p3.ASPD().compareTo(PanelCalculator.of(plain).ASPD()) > 0, "源液攻速应生效");
        assertEquals(0, new BigDecimal("0.08").compareTo(p3.CRIT().subtract(PanelCalculator.of(plain).CRIT())), "源液暴击应 +0.08");
        // 源初斩 ×7：挂 buff 后单拳伤害约为无 buff 的 7 倍
        PlayerState a = fighter();
        PlayerState.MobState ma = mob(null, "1000000000000");
        a.mob = ma;
        GameEngine.dealDamage(a, PanelCalculator.of(a), BigDecimal.ONE, 1.0);
        BigDecimal base = new BigDecimal("1000000000000").subtract(ma.curHP);
        PlayerState b = fighter();
        PlayerState.MobState mb = mob(null, "1000000000000");
        b.mob = mb;
        b.buffs.put("origin_slash", 30.0);
        GameEngine.dealDamage(b, PanelCalculator.of(b), BigDecimal.ONE, 1.0);
        BigDecimal boosted = new BigDecimal("1000000000000").subtract(mb.curHP);
        assertEquals(0, base.multiply(BigDecimal.valueOf(7)).compareTo(boosted), "源初斩应 ×7");
        assertTrue(!b.buffs.containsKey("origin_slash"), "源初斩应被消费");
    }

    @Test
    void newTraitsInPools() {
        assertTrue(GameConst.traitPool(10).contains("tough_hide"), "韧皮应入混沌池");
        assertTrue(GameConst.traitPool(12).contains("life_eater"), "噬生应入归墟池");
        assertTrue(GameConst.traitPool(13).contains("phantom"), "幻影应入太初池");
        assertTrue(!GameConst.traitPool(12).contains("phantom"), "幻影只应出现在太初池");
    }
}
