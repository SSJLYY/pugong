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

/**
 * 第七批第 98 轮：觉醒天赋池下发锁——talentView 必须列全 10 个天赋（含第七批幸运儿/铁胃），
 * 每项 id/name/emoji/desc 齐全，前端觉醒仪式与天赋徽章同源于此。
 */
class TalentViewTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void poolListsAllTalents() {
        PlayerState s = NewState.create();
        Map<String, Object> v = new GameService().talentView(s);
        List<Map<String, Object>> pool = (List<Map<String, Object>>) v.get("pool");
        assertEquals(GameConst.TALENTS.size(), pool.size(), "天赋池下发应与 GameConst.TALENTS 同数");
        assertEquals(13, pool.size());
        List<String> ids = pool.stream().map(m -> String.valueOf(m.get("id"))).toList();
        assertTrue(ids.contains("lucky"), "幸运儿应在天赋池下发中");
        assertTrue(ids.contains("iron_gut"), "铁胃应在天赋池下发中");
        for (Map<String, Object> t : pool) {
            assertFalse(String.valueOf(t.get("name")).isBlank());
            assertFalse(String.valueOf(t.get("desc")).isBlank());
        }
    }
}
