package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第九批第 88 轮：词条「燃血」——残血放大攻击、词条池分布与击杀计数。 */
class BloodFuryTraitTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private static PlayerState.MobState mob(String trait, String hpPct) {
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "test_mob";
        m.name = "燃血靶";
        m.trait = trait;
        m.maxHP = Num.of(1000);
        m.curHP = Num.mul(Num.of(1000), new java.math.BigDecimal(hpPct));
        m.PATK = Num.of(500);
        m.MATK = Num.ZERO;
        m.ASPD = 1;
        return m;
    }

    private static double hpLostAfterAttack(String trait, String hpPct) {
        PlayerState s = NewState.create();
        s.life.maxHP = Num.of(1_000_000);
        s.life.curHP = Num.of(1_000_000);
        s.mob = mob(trait, hpPct);
        var panel = PanelCalculator.of(s);
        double before = s.life.curHP.doubleValue();
        GameEngine.mobAttack(s, panel, 1.0);
        return before - s.life.curHP.doubleValue();
    }

    @Test
    void furyScalesWithMissingHp() {
        double full = hpLostAfterAttack("blood_fury", "1");
        double half = hpLostAfterAttack("blood_fury", "0.5");
        double low = hpLostAfterAttack("blood_fury", "0.1");
        assertTrue(half > full * 1.2, "半血应明显更痛：" + full + " → " + half);
        assertTrue(low > half, "残血应更痛：" + half + " → " + low);
        double plainHalf = hpLostAfterAttack(null, "0.5");
        assertEquals(plainHalf, full, 0.001, "满血燃血与无词条同伤");
    }

    @Test
    void poolsAndCounting() {
        assertTrue(GameConst.traitPool(5).contains("blood_fury"), "深区池应含燃血");
        assertTrue(GameConst.traitPool(8).contains("blood_fury"), "星界池应含燃血");
        assertTrue(GameConst.traitPool(9).contains("blood_fury"), "虚空池应含燃血");
        assertTrue(!GameConst.traitPool(4).contains("blood_fury"), "沼泽池不应含燃血");
        assertEquals(29, GameConst.MOB_TRAITS.size(), "怪物词条 25");
    }
}
