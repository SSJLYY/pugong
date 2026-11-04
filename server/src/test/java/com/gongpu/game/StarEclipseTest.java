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
 * 第八批第 88 轮：怪物词条「星蚀」（星界专属）——暴伤超额减半（与坚韧分工），
 * 只进星界词条池；击杀计数进 Stats.eclipseKills。
 */
class StarEclipseTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void critDamageHalvedAgainstEclipse() {
        PlayerState s = NewState.create();
        s.fixed.CRITD = new BigDecimal("2.5");
        PanelCalculator.Panel p = PanelCalculator.of(s);
        PlayerState.MobState plain = new PlayerState.MobState();
        PlayerState.MobState eclipse = new PlayerState.MobState();
        eclipse.trait = "star_eclipse";
        assertEquals(0, GameEngine.effCritD(p, plain).compareTo(new BigDecimal("2.5")));
        // 1 + (2.5 − 1) × 0.5 = 1.75
        assertEquals(0, GameEngine.effCritD(p, eclipse).compareTo(new BigDecimal("1.75")));
    }

    @Test
    void onlyInStarPool() {
        assertTrue(GameConst.traitPool(8).contains("star_eclipse"), "星界池应含星蚀");
        assertFalse(GameConst.traitPool(7).contains("star_eclipse"), "深渊池不应含星蚀");
        assertFalse(GameConst.traitPool(5).contains("star_eclipse"), "中层池不应含星蚀");
        assertEquals(19, GameConst.MOB_TRAITS.size(), "怪物词条总数应为 19");
        assertEquals(18, GameConst.traitPool(8).size(), "星界池应为全 18 条");
    }
}
