package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 第六批第 71 轮：新药剂组合回归——五药同饮的面板叠加口径，
 * 且沉默应统一压制全部药剂增益（乘区/平加/上限/回血同规则）。
 */
class PotionComboTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private static void drinkAll(PlayerState s) {
        s.buffs.put("vital_pot", 120.0);
        s.buffs.put("guard_pot", 120.0);
        s.buffs.put("might_pot", 120.0);
        s.buffs.put("spirit_pot", 120.0);
        s.buffs.put("regen_pot_l", 60.0);
    }

    @Test
    void comboStacks() {
        PlayerState s = NewState.create();
        PanelCalculator.Panel base = PanelCalculator.of(s);
        drinkAll(s);
        PanelCalculator.Panel p = PanelCalculator.of(s);
        assertEquals(0, p.maxHP().compareTo(Num.mul(base.maxHP(), Num.of(1.3))), "生命上限 ×1.3");
        assertEquals(0, p.PATK().compareTo(Num.add(base.PATK(), Num.of(20))), "物攻 +20");
        assertEquals(0, p.MATK().compareTo(Num.add(base.MATK(), Num.of(20))), "魔攻 +20");
        assertEquals(0, p.PDEF().compareTo(Num.add(base.PDEF(), Num.of(15))), "物防 +15");
        assertEquals(0, p.MDEF().compareTo(Num.add(base.MDEF(), Num.of(15))), "魔防 +15");
        assertEquals(0, p.MP().compareTo(Num.add(base.MP(), Num.of(300))), "蓝上限 +300");
        assertEquals(0, p.REGEN().compareTo(Num.add(base.REGEN(), Num.of(100))), "回血 +100/s");
    }

    @Test
    void silenceSuppressesAllPotionBuffs() {
        PlayerState s = NewState.create();
        PanelCalculator.Panel base = PanelCalculator.of(s);
        drinkAll(s);
        s.buffs.put("silence", 10.0);
        PanelCalculator.Panel p = PanelCalculator.of(s);
        assertEquals(0, p.maxHP().compareTo(base.maxHP()), "沉默压制生命乘区");
        assertEquals(0, p.PATK().compareTo(base.PATK()), "沉默压制大力平加");
        assertEquals(0, p.PDEF().compareTo(base.PDEF()), "沉默压制金钟平加");
        assertEquals(0, p.MP().compareTo(base.MP()), "沉默压制聚灵上限");
        assertEquals(0, p.REGEN().compareTo(base.REGEN()), "沉默压制回春");
    }
}
