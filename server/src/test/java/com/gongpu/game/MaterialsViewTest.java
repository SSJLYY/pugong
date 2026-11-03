package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第六批第 50 轮：fullView 下发材料库存（管家面板总览数据源）。 */
class MaterialsViewTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fullViewCarriesMaterials() {
        PlayerState s = NewState.create();
        s.materials.put("pelt", 7);
        s.materials.put("bp_iron_sword", 1);
        Map<String, Object> view = new GameService().fullView(s);
        Map<String, Integer> mats = (Map<String, Integer>) view.get("materials");
        assertEquals(7, mats.get("pelt"));
        assertEquals(1, mats.get("bp_iron_sword"));
    }
}
