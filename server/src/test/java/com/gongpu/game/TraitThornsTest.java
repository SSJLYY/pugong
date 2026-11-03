package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第三批第 3 轮：词条「荆棘」——所造伤害 10% 反扎玩家（护盾先吸），可扎死；
 * 词条池雪原起解锁（沼泽仍只出四词条）。
 */
class TraitThornsTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fighter(BigDecimal curHP) {
        PlayerState s = NewState.create();
        s.zone = "snow";
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "yeti";
        m.name = "雪怪";
        m.emoji = "👹";
        m.maxHP = new BigDecimal("100000");
        m.curHP = new BigDecimal("100000");
        m.PATK = Num.ZERO;
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        m.trait = "thorns";
        s.mob = m;
        s.life.set("PATK", new BigDecimal("100"));
        s.fixed.CRIT = BigDecimal.ZERO;   // 归零固有暴击期望，保证每拳恰好 100 伤、反扎恰好 10%
        s.fixed.CRITD = BigDecimal.ONE;   // 弱点打击中性化（第七批机制：暴伤=1 时弱点拳无加成，旧断言口径不变）
        s.life.maxHP = new BigDecimal("10000");
        s.life.curHP = curHP;
        return s;
    }

    @Test
    void thornsReflectsTenPercent() {
        PlayerState s = fighter(new BigDecimal("10000"));
        // PATK 100 × 10 拳 = 1000 伤害 → 反扎 100
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("10"), 1.0);
        assertEquals(0, s.life.curHP.compareTo(new BigDecimal("9900")),
                "荆棘应反弹 10%：10000 - 100");
        assertFalse(s.dead);
    }

    @Test
    void shieldAbsorbsReflectFirst() {
        PlayerState s = fighter(new BigDecimal("10000"));
        s.shield = new BigDecimal("60");
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("10"), 1.0);
        assertEquals(0, s.shield.compareTo(BigDecimal.ZERO), "护盾先吸 60");
        assertEquals(0, s.life.curHP.compareTo(new BigDecimal("9960")),
                "余下 40 才扣血");
    }

    @Test
    void thornsCanKill() {
        PlayerState s = fighter(new BigDecimal("50"));
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("10"), 1.0);
        assertTrue(s.dead, "反扎 100 > 残血 50 应致死");
        assertTrue(s.deathCause.contains("雪怪"), "死因应记在荆棘怪头上");
    }

    /**
     * 溢出反扎封顶（实测事故修复）：玩家一拳 1e12 打 8 万血荆棘雪人，
     * 旧口径按整帧伤害反扎 1e11 无视双防一击致命；新口径只按怪实际承受的 8 万计，反扎 8000。
     */
    @Test
    void overkillReflectCappedAtMobHp() {
        PlayerState s = fighter(new BigDecimal("100000"));
        s.mob.maxHP = new BigDecimal("80000");
        s.mob.curHP = new BigDecimal("80000");
        s.life.maxHP = new BigDecimal("100000");
        s.life.set("PATK", new BigDecimal("1000000000000"));
        GameEngine.dealDamage(s, PanelCalculator.of(s), BigDecimal.ONE, 1.0);
        assertFalse(s.dead, "溢出伤害不该被荆棘全额反扎致死");
        assertEquals(0, s.life.curHP.compareTo(new BigDecimal("92000")),
                "反扎应封顶在怪血量 80000 的 10% = 8000");
    }

    /** 死因标注词条：被荆棘扎死时死亡画面能看出死因。 */
    @Test
    void deathCauseNamesTrait() {
        PlayerState s = fighter(new BigDecimal("50"));
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("10"), 1.0);
        assertTrue(s.dead);
        assertTrue(s.deathCause.contains("荆棘"), "死因应标注词条，实际：" + s.deathCause);
    }

    @Test
    void poolUnlocksAtSnow() {
        boolean sawThornsAtSnow = false;
        for (int i = 0; i < 4000; i++) {
            String t = GameConst.rollMobTrait("snow");
            if ("thorns".equals(t)) {
                sawThornsAtSnow = true;
            }
            assertFalse("thorns".equals(GameConst.rollMobTrait("swamp")),
                    "沼泽词条池不应含荆棘");
        }
        assertTrue(sawThornsAtSnow, "雪原应能掷出荆棘");
    }
}
