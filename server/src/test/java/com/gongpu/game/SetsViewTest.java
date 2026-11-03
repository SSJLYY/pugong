package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第七批第 4 轮：fullView 套装进度下发——件数/激活档位/加成文案（概率类按百分比）。 */
class SetsViewTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private static PlayerState.Item worn(String slot, String code) {
        PlayerState.Item it = new PlayerState.Item();
        it.slot = slot;
        it.code = code;
        it.name = code;
        return it;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> setRow(Map<String, Object> view, String id) {
        List<Map<String, Object>> sets = (List<Map<String, Object>>) view.get("sets");
        return sets.stream().filter(r -> id.equals(r.get("id"))).findFirst().orElseThrow();
    }

    @Test
    void fullViewCarriesSetProgress() {
        PlayerState s = NewState.create();
        s.equipped.put("head", worn("head", "leather_cap"));
        s.equipped.put("chest", worn("chest", "leather_armor"));
        Map<String, Object> view = new GameService().fullView(s);
        Map<String, Object> row = setRow(view, "farwalker");
        assertEquals(2, row.get("worn"));
        assertEquals(4, row.get("total"));
        assertTrue((Boolean) row.get("active2"));
        assertFalse((Boolean) row.get("active4"));
        assertEquals("生命上限 +150", row.get("bonus2"));
        assertEquals("生命上限 +350、回血 +5", row.get("bonus4"));
    }

    @Test
    void percentBonusRenderedAsPercent() {
        Map<String, Object> view = new GameService().fullView(NewState.create());
        Map<String, Object> row = setRow(view, "vinetalk");
        assertEquals("物攻 +25、暴击率 +3%", row.get("bonus4"), "概率类加成应按百分比展示");
        Map<String, Object> molten = setRow(view, "molten");
        assertEquals("物攻 +80、魔攻 +80、暴伤 +20%", molten.get("bonus4"));
    }
}
