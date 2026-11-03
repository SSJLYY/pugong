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

/**
 * 第七批第 42 轮：词条「寒霜」——命中上霜 5 秒、攻速 -15%（与时滞同乘区叠加），
 * 解毒剂与净化技能可解；雪原起入池。
 */
class TraitFrostTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fighter() {
        PlayerState s = NewState.create();
        s.fixed.ASPD = new BigDecimal("2");
        return s;
    }

    @Test
    void frostSlowsAspd() {
        PlayerState s = fighter();
        assertEquals(0, PanelCalculator.of(s).ASPD().compareTo(new BigDecimal("2")),
                "无减益时攻速应为基线 2");
        s.buffs.put("frost", GameConst.TRAIT_FROST_DUR);
        assertEquals(0, PanelCalculator.of(s).ASPD().compareTo(new BigDecimal("1.7")),
                "寒霜应使攻速 ×0.85");
    }

    @Test
    void frostStacksWithSlow() {
        PlayerState s = fighter();
        s.buffs.put("frost", GameConst.TRAIT_FROST_DUR);
        s.buffs.put("slow", GameConst.TRAIT_SLOW_DUR);
        assertEquals(0, PanelCalculator.of(s).ASPD().compareTo(new BigDecimal("1.36")),
                "寒霜与时滞同乘区叠加：2×0.85×0.8=1.36");
    }

    @Test
    void cleanseClearsFrost() {
        GameService game = new GameService();
        PlayerState s = fighter();
        s.life.curMP = Num.of(100);
        s.buffs.put("frost", GameConst.TRAIT_FROST_DUR);
        game.castSkill(s, "cleanse");
        assertFalse(s.buffs.containsKey("frost"), "净化应清除寒霜");
    }

    @Test
    void curePotionClearsFrost() {
        GameService game = new GameService();
        PlayerState s = fighter();
        s.buffs.put("frost", GameConst.TRAIT_FROST_DUR);
        s.potions.put("cure", 1);
        game.usePotion(s, "cure");
        assertFalse(s.buffs.containsKey("frost"), "解毒剂应清除寒霜");
    }
}
