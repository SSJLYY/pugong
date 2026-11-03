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
 * 第四批第 2 轮：新维度药剂的面板接线——生生（回血 +25/s）、远眺（射程 +1）、疾行（移速 +40），
 * 且与既有药剂同语义受沉默压制。
 */
class PotionsDimensionTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void regenPotAddsRegen() {
        PlayerState s = NewState.create();
        BigDecimal before = PanelCalculator.of(s).REGEN();
        s.buffs.put("regen_pot", 120.0);
        BigDecimal after = PanelCalculator.of(s).REGEN();
        assertEquals(0, Num.sub(after, before).compareTo(new BigDecimal("25")),
                "生生药剂应使回血 +25/秒");
    }

    @Test
    void rngPotAddsRange() {
        PlayerState s = NewState.create();
        BigDecimal before = PanelCalculator.of(s).RNG();
        s.buffs.put("rng_pot", 120.0);
        BigDecimal after = PanelCalculator.of(s).RNG();
        assertEquals(0, Num.sub(after, before).compareTo(BigDecimal.ONE),
                "远眺药剂应使射程 +1");
    }

    @Test
    void fleetPotAddsSpeed() {
        PlayerState s = NewState.create();
        BigDecimal before = PanelCalculator.of(s).SPD();
        s.buffs.put("fleet_pot", 120.0);
        BigDecimal after = PanelCalculator.of(s).SPD();
        assertEquals(0, Num.sub(after, before).compareTo(new BigDecimal("40")),
                "疾行药剂应使移速 +40");
    }

    @Test
    void silenceSuppressesNewPots() {
        PlayerState s = NewState.create();
        BigDecimal baseRegen = PanelCalculator.of(s).REGEN();
        s.buffs.put("regen_pot", 120.0);
        s.buffs.put("silence", 5.0);
        assertEquals(0, PanelCalculator.of(s).REGEN().compareTo(baseRegen),
                "沉默时生生药剂应失效（与既有药剂同语义）");
    }

    @Test
    void drinkSetsBuffs() {
        PlayerState s = NewState.create();
        s.potions.put("regen", 1);
        s.potions.put("far", 1);
        s.potions.put("fleet", 1);
        GameService g = new GameService();
        g.usePotion(s, "regen");
        g.usePotion(s, "far");
        g.usePotion(s, "fleet");
        assertEquals(120.0, s.buffs.get("regen_pot"));
        assertEquals(120.0, s.buffs.get("rng_pot"));
        assertEquals(120.0, s.buffs.get("fleet_pot"));
    }
}
