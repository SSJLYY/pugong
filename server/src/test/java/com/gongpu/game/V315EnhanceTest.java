package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v3.15 增强轮回归锁：
 * <ul>
 *   <li>BULLET_MP 是成本属性，不吃强化乘区（魔弹枪越强化越哑火的根治）；</li>
 *   <li>怪物词条掷骰的区域池约束（村口无词条/平原只有狂暴/深区全开）；</li>
 *   <li>三词条的战斗效果（缴械压普攻/破甲折防/狂暴残血加速）。</li>
 * </ul>
 */
class V315EnhanceTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState.Item manaGun(int enhance) throws Exception {
        PlayerState.Item it = new PlayerState.Item();
        it.uid = 1;
        it.name = "测试魔弹枪";
        it.emoji = "🔫";
        it.slot = "bow";
        it.enhance = enhance;
        Map<String, Object> stats = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                "{\"MATK\":\"18\",\"RNG\":\"6\",\"ASPD\":\"3\",\"BULLET_MP\":\"1\"}",
                new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() { });
        for (Map.Entry<String, Object> e : stats.entrySet()) {
            it.base.put(e.getKey(), Num.parse(String.valueOf(e.getValue()), Num.ZERO));
        }
        return it;
    }

    /** 强化只放大战力：+10 枪的魔攻涨 50%，每发耗蓝必须仍是 1。 */
    @Test
    void bulletMpIgnoresEnhanceMultiplier() throws Exception {
        PlayerState s = NewState.create();
        s.equipped.put("bow", manaGun(0));
        PanelCalculator.Panel p0 = PanelCalculator.of(s);
        assertEquals(0, p0.BULLETMP().compareTo(Num.of(1)));

        s.equipped.put("bow", manaGun(10));
        PanelCalculator.Panel p10 = PanelCalculator.of(s);
        assertEquals(0, p10.BULLETMP().compareTo(Num.of(1)),
                "BULLET_MP 是成本，吃强化乘区会让魔弹枪越强化越哑火");
        assertEquals(0, p10.MATK().subtract(p0.MATK()).compareTo(Num.of(9)),
                "魔攻应吃满强化：18 × (10 × 5%) = +9");
    }

    /** 词条池随区域深度解锁，村口永远无词条。 */
    @Test
    void traitPoolFollowsZoneDepth() {
        for (int i = 0; i < 300; i++) {
            assertNull(GameConst.rollMobTrait("village"), "村口是安全区，永不出词条");
        }
        boolean sawFrenzy = false;
        for (int i = 0; i < 300; i++) {
            String t = GameConst.rollMobTrait("plain");
            assertTrue(t == null || "frenzy".equals(t), "平原词条池只有狂暴，实际=" + t);
            sawFrenzy |= "frenzy".equals(t);
        }
        assertTrue(sawFrenzy, "平原 300 次掷骰应至少出一次狂暴（单次 10%）");
        for (int i = 0; i < 300; i++) {
            String t = GameConst.rollMobTrait("forest");
            // 森林池：狂暴/破甲（v3.15）+ 富饶（第五批）+ 晶化（第六批第 23 轮）；疾影要矿洞起，不应出现
            assertTrue(t == null || "frenzy".equals(t) || "sunder".equals(t) || "rich".equals(t) || "crystal".equals(t),
                    "森林词条池不应出现缴械/疾影，实际=" + t);
        }
        boolean sawDisarm = false;
        for (int i = 0; i < 2000; i++) {
            sawDisarm |= "disarm".equals(GameConst.rollMobTrait("volcano"));
        }
        assertTrue(sawDisarm, "火山 2000 次掷骰应至少出一次缴械（单次 25%/3）");
    }

    private PlayerState.MobState testMob(String trait, String maxHP) {
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "testmob";
        m.name = "测试怪";
        m.emoji = "👾";
        m.maxHP = new BigDecimal(maxHP);
        m.curHP = m.maxHP;
        m.PATK = Num.ZERO;
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        m.ASPD = 1;
        m.RNG = 0;
        m.SPD = 1;
        m.trait = trait;
        return m;
    }

    /** 缴械：同样的 10 拳，无词条打 10000，缴械只打 8500。 */
    @Test
    void disarmCutsPlayerDamageBy15Percent() {
        PlayerState plain = NewState.create();
        plain.mob = testMob(null, "1000000");
        plain.life.set("PATK", new BigDecimal("1000"));
        plain.fixed.CRIT = Num.ZERO;   // 出生自带 5% 暴击会污染精确伤害断言
        plain.fixed.CRITD = Num.of(1);   // 弱点打击中性化（第七批机制，旧断言口径不变）
        GameEngine.dealDamage(plain, PanelCalculator.of(plain), Num.of(10), 1.0);
        assertEquals(0, plain.mob.curHP.compareTo(new BigDecimal("990000")));

        PlayerState disarmed = NewState.create();
        disarmed.mob = testMob("disarm", "1000000");
        disarmed.life.set("PATK", new BigDecimal("1000"));
        disarmed.fixed.CRIT = Num.ZERO;
        disarmed.fixed.CRITD = Num.of(1);   // 弱点打击中性化（同上）
        GameEngine.dealDamage(disarmed, PanelCalculator.of(disarmed), Num.of(10), 1.0);
        assertEquals(0, disarmed.mob.curHP.compareTo(new BigDecimal("991500")),
                "缴械应把普攻总伤压到 85%");
    }

    /** 破甲：怪攻 200 打防 100，无词条承伤 120，破甲承伤 144（防按 70% 结算）。 */
    @Test
    void sunderIgnoresThirtyPercentDefense() {
        BigDecimal lossPlain = mobAttackLoss(null);
        BigDecimal lossSunder = mobAttackLoss("sunder");
        assertEquals(0, lossPlain.compareTo(new BigDecimal("120")));
        assertEquals(0, lossSunder.compareTo(new BigDecimal("144")));
    }

    /** 狂暴：满血攻速不变，残血（<30%）攻速 ×1.5，承伤随之 ×1.5。 */
    @Test
    void frenzyHastesOnlyBelowThirtyPercent() {
        assertEquals(0, frenzyLoss("0.5").compareTo(new BigDecimal("100")),
                "半血未触发狂暴，承伤应为基础值");
        assertEquals(0, frenzyLoss("0.2").compareTo(new BigDecimal("150")),
                "残血触发狂暴，1 秒内命中数 ×1.5");
    }

    private BigDecimal mobAttackLoss(String trait) {
        PlayerState s = NewState.create();
        s.life.maxHP = new BigDecimal("100000");
        s.life.curHP = s.life.maxHP;
        s.life.set("PDEF", new BigDecimal("100"));
        PlayerState.MobState m = testMob(trait, "100000");
        m.PATK = new BigDecimal("200");
        s.mob = m;
        GameEngine.mobAttack(s, PanelCalculator.of(s), 1.0);
        return s.life.maxHP.subtract(s.life.curHP);
    }

    private BigDecimal frenzyLoss(String hpRatio) {
        PlayerState s = NewState.create();
        s.life.maxHP = new BigDecimal("100000");
        s.life.curHP = s.life.maxHP;
        s.life.set("PDEF", Num.ZERO);   // 出生自带 2 点物防会污染精确承伤断言
        PlayerState.MobState m = testMob("frenzy", "1000");
        m.curHP = m.maxHP.multiply(new BigDecimal(hpRatio));
        m.PATK = new BigDecimal("100");
        s.mob = m;
        GameEngine.mobAttack(s, PanelCalculator.of(s), 1.0);
        return s.life.maxHP.subtract(s.life.curHP);
    }
}
