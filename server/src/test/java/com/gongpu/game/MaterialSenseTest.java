package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第六批第 88 轮：被动「材料嗅觉」——材料掉率 50%→75%，沉默压制。 */
class MaterialSenseTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void chanceBonus() {
        PlayerState s = NewState.create();
        assertEquals(0.5, GameEngine.materialChance(s), 1e-9, "基础材料掉率 50%");
        s.passives.add("material_sense");
        assertEquals(0.75, GameEngine.materialChance(s), 1e-9, "材料嗅觉应 +25 个百分点");
        s.buffs.put("silence", 5.0);
        assertEquals(0.5, GameEngine.materialChance(s), 1e-9, "沉默时被动应失效");
    }
}
