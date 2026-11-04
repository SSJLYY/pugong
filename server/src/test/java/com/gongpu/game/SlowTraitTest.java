package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第六批第 87 轮：新词条「时滞」——面板攻速 ×0.8、沼泽起入池。 */
class SlowTraitTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void slowCutsAspd() {
        PlayerState s = NewState.create();
        BigDecimal base = PanelCalculator.of(s).ASPD();
        s.buffs.put("slow", GameConst.TRAIT_SLOW_DUR);
        BigDecimal slowed = PanelCalculator.of(s).ASPD();
        assertEquals(0, slowed.compareTo(Num.mul(base, Num.of(GameConst.TRAIT_SLOW_ASPD_MUL))),
                "时滞应让攻速 ×0.8：" + base + " → " + slowed);
    }

    @Test
    void slowInPoolsFromSwamp() {
        assertTrue(GameConst.traitPool(4).contains("slow"), "沼泽池应含时滞");
        assertTrue(GameConst.traitPool(5).contains("slow"), "深区池应含时滞");
        assertTrue(!GameConst.traitPool(3).contains("slow"), "矿洞池不应含时滞");
        assertEquals(16, GameConst.MOB_TRAITS.size(), "词条总数应为 16（+ 第八批再生/坚韧）");
    }
}
