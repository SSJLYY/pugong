package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第七批实测修复锁：fullView（轮询主视图）必须带 dailyTasks/blackMarket/rerollPity——
 * 三者曾误挂在 autoconfig 回执上，前端每日任务卡与黑市在实测中刷不出来。
 */
class FullViewDailyTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fullViewCarriesDailySystems() {
        PlayerState s = NewState.create();
        Map<String, Object> v = new GameService().fullView(s);
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) v.get("dailyTasks");
        assertNotNull(tasks, "fullView 应带每日任务");
        assertEquals(3, tasks.size(), "每日任务应为 3 条");
        List<Map<String, Object>> offers = (List<Map<String, Object>>) v.get("blackMarket");
        assertNotNull(offers, "fullView 应带黑市");
        assertEquals(3, offers.size(), "黑市应为 3 个货位");
        assertTrue(v.containsKey("rerollPity"), "fullView 应带洗炼保底计数");
    }
}
