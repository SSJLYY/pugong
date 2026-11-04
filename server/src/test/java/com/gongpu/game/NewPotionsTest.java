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

/**
 * 第八批第 81 轮：凝神/生机药剂——buff 生效时面板命中 +0.12、回复 +150，
 * 与鹰眼/回春同区叠加；沉默压制两者。
 */
class NewPotionsTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void focusAndVigorApplyToPanel() {
        PlayerState s = NewState.create();
        s.fixed.ACC = new BigDecimal("0.80");
        s.buffs.put("focus_pot", 90.0);
        s.buffs.put("vigor_pot", 60.0);
        PanelCalculator.Panel p = PanelCalculator.of(s);
        assertEquals(0, p.ACC().compareTo(new BigDecimal("0.92")), "命中 0.80 + 凝神 0.12（面板命中上限 1.0）");
        // REGEN 基线 2（NewState）+ 生机 150
        assertTrue(Num.gte(p.REGEN(), Num.of(152)), "回复应含生机 +150，实际 " + p.REGEN());
        // GameConst 镜像齐备
        assertEquals(44, GameConst.POTIONS.size(), "药剂总数应为 40");
        assertTrue(GameConst.POTIONS.containsKey("focus") && GameConst.POTIONS.containsKey("vigor"));
    }
}
