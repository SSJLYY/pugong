package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第 4 轮：背包整理——按评分（品质→词条→底子→强化）降序，同分稳定保持原序。 */
class SortBagTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private final GameService game = new GameService();

    private PlayerState.Item item(String name, int quality, int baseSum) {
        PlayerState.Item it = new PlayerState.Item();
        it.name = name;
        it.slot = "sword";
        it.quality = quality;
        if (baseSum > 0) {
            it.base.put("PATK", BigDecimal.valueOf(baseSum));
        }
        return it;
    }

    @Test
    void sortsByScoreDescendingStable() {
        PlayerState s = NewState.create();
        s.equipBag.add(item("白装", 0, 10));
        s.equipBag.add(item("紫装甲", 3, 10));
        s.equipBag.add(item("绿装", 1, 10));
        s.equipBag.add(item("紫装乙", 3, 10));   // 与紫装甲同分：稳定排序保持甲在乙前
        s.equipBag.add(item("强蓝装", 2, 900));  // 底子极高也压不过品质档（品质×1000 主导）
        game.sortBag(s);
        assertEquals("紫装甲", s.equipBag.get(0).name);
        assertEquals("紫装乙", s.equipBag.get(1).name);
        assertEquals("强蓝装", s.equipBag.get(2).name);
        assertEquals("绿装", s.equipBag.get(3).name);
        assertEquals("白装", s.equipBag.get(4).name);
    }

    @Test
    void emptyBagIsNoOp() {
        PlayerState s = NewState.create();
        game.sortBag(s);
        assertEquals(0, s.equipBag.size());
    }
}
