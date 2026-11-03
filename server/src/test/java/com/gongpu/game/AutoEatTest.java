package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 自动进食锁：饱食回血断档自动吃食物续上；buff 在身不浪费，关了不吃，村里不吃。 */
class AutoEatTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState combatReady() {
        PlayerState s = NewState.create();
        s.zone = "plain";
        GameEngine.spawnMob(s, "plain");
        s.dead = false;
        s.lastTickAt = System.currentTimeMillis() - 5000;
        s.potions.put("food", 2);
        return s;
    }

    @Test
    void eatsWhenBuffMissing() {
        PlayerState s = combatReady();
        s.autoEat = true;
        GameEngine.advance(s, System.currentTimeMillis(), 1.0, false);
        assertEquals(1, s.potions.get("food"), "应自动吃掉一份食物");
        assertTrue(PanelCalculator.active(s, "regen_food"), "吃食后饱食回血应生效");
    }

    @Test
    void skipsWhenBuffActiveOrOffOrInVillage() {
        PlayerState a = combatReady();
        a.autoEat = true;
        a.buffs.put("regen_food", 8.0);
        GameEngine.advance(a, System.currentTimeMillis(), 1.0, false);
        assertEquals(2, a.potions.get("food"), "buff 在身不应浪费食物");

        PlayerState b = combatReady();
        GameEngine.advance(b, System.currentTimeMillis(), 1.0, false);
        assertEquals(2, b.potions.get("food"), "未开启不应自动吃");

        PlayerState c = combatReady();
        c.autoEat = true;
        c.zone = "village";
        c.mob = null;
        GameEngine.advance(c, System.currentTimeMillis(), 1.0, false);
        assertEquals(2, c.potions.get("food"), "村里不应吃（防浪费）");
    }
}
