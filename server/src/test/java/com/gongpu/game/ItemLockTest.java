package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.ApiException;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import com.gongpu.state.StateCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 第 3 轮：装备锁定——锁定件不被单卖/一键卖/丢弃/管护（卖白装+无相位吞噬）处置，
 * 解锁后恢复；锁定标记随存档编解码保留，旧档无字段默认未锁定。
 */
class ItemLockTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private final GameService game = new GameService();

    private PlayerState.Item item(String name, int quality, boolean locked) {
        PlayerState.Item it = new PlayerState.Item();
        it.name = name;
        it.slot = "sword";
        it.quality = quality;
        it.locked = locked;
        return it;
    }

    @Test
    void toggleFlipsAndReports() {
        PlayerState s = NewState.create();
        s.equipBag.add(item("剑", 3, false));
        assertEquals(Boolean.TRUE, game.toggleLock(s, 0).get("locked"));
        assertTrue(s.equipBag.get(0).locked);
        assertEquals(Boolean.FALSE, game.toggleLock(s, 0).get("locked"));
        assertThrows(ApiException.class, () -> game.toggleLock(s, 5));
    }

    @Test
    void lockedCannotBeSoldOrDiscarded() {
        PlayerState s = NewState.create();
        s.equipBag.add(item("心头好", 3, true));
        assertThrows(ApiException.class, () -> game.sell(s, 0));
        assertThrows(ApiException.class, () -> game.discard(s, 0));
        assertEquals(1, s.equipBag.size(), "锁定件必须留在背包");
    }

    @Test
    void autoSellSkipsLockedEvenAtTopTier() {
        PlayerState s = NewState.create();
        s.equipBag.add(item("锁定的紫装", 3, true));
        s.equipBag.add(item("普通紫装", 3, false));
        s.equipBag.add(item("白装", 0, false));
        Object sold = game.autoSell(s, 4).get("soldCount");
        assertEquals(2, sold, "紫档全卖也只卖未锁定件");
        assertEquals(1, s.equipBag.size());
        assertTrue(s.equipBag.get(0).locked);
    }

    @Test
    void autoMaintainKeepsLockedWhiteAndSkipsDevour() {
        PlayerState s = NewState.create();
        s.talent = "baglord";
        s.equipBag.add(item("锁定的白装", 0, true));
        PlayerState.Item worn = item("同名剑", 1, false);
        s.equipped.put("sword", worn);
        s.equipBag.add(item("同名剑", 1, true));   // 锁定同名件：不得被无相位吞噬
        GameService.autoMaintain(s);
        assertTrue(s.equipBag.stream().anyMatch(it -> it.locked && it.quality == 0),
                "管护卖白装不得动锁定白装");
        assertTrue(s.equipBag.stream().anyMatch(it -> it.locked && "同名剑".equals(it.name)),
                "管护吞噬不得动锁定件");
    }

    @Test
    void lockSurvivesCodecRoundTrip() {
        PlayerState s = NewState.create();
        s.equipBag.add(item("剑", 2, true));
        PlayerState back = StateCodec.decode(StateCodec.encode(s));
        assertTrue(back.equipBag.get(0).locked);
        // 旧档 JSON 无 locked 字段 → 默认 false
        PlayerState legacy = StateCodec.decode("{\"gen\":1,\"equipBag\":[{\"name\":\"旧剑\",\"slot\":\"sword\",\"quality\":1}]}");
        assertFalse(legacy.equipBag.get(0).locked);
    }
}
