package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第四批第 4 轮：解毒剂三清——中毒/灼烧/沉默一剂全清，且不清正面增益。
 */
class CureExpandTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void cureClearsAllThreeDebuffs() {
        PlayerState s = NewState.create();
        s.buffs.put("poison", 10.0);
        s.buffs.put("burn_dot", 10.0);
        s.buffs.put("silence", 8.0);
        s.buffs.put("atk_pot", 30.0);
        s.potions.put("cure", 1);
        new GameService().usePotion(s, "cure");
        assertFalse(s.buffs.containsKey("poison"));
        assertFalse(s.buffs.containsKey("burn_dot"));
        assertFalse(s.buffs.containsKey("silence"), "沉默应被清（此前只能清毒/灼烧）");
        assertTrue(s.buffs.containsKey("atk_pot"), "正面增益不应误清");
        assertFalse(PanelCalculator.isSilenced(s), "清后应解除沉默态");
    }
}
