package com.gongpu.game;

import com.gongpu.common.ApiException;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十三批集群三：区域扫荡（成本/截断/安全区/精通联动/只涨击杀口径）。 */
class SweepTest {

    private final GameService game = new GameService();

    @Test
    void sweepChargesByDepthAndFeedsMastery() {
        PlayerState s = NewState.create();
        s.zone = "plain";   // 深度 1 → 单杀 100 金
        s.gold = new BigDecimal("100000");
        Map<String, Object> r = game.sweep(s, 50);
        assertEquals(50L, r.get("swept"));
        assertEquals("5000", r.get("goldCost"));
        assertEquals(0, new BigDecimal("95000").compareTo(s.gold));
        assertEquals(50, s.stats.zoneKills.get("plain"), "分区域击杀应累加（精通口径）");
        assertEquals(50, s.stats.sweeps);
        assertEquals(0, new BigDecimal("50").compareTo(s.stats.lifetimeKills), "终身击杀应同步（猎杀任务口径）");
        // 精通联动：500 杀一档，扫满 500 应 +2%
        game.sweep(s, 200);
        game.sweep(s, 200);
        game.sweep(s, 50);
        assertEquals(500, s.stats.zoneKills.get("plain"));
        assertEquals(0.02, GameEngine.zoneMasteryMul(s) - 1.0, 1e-9, "500 杀精通应 +2%");
    }

    @Test
    void sweepTruncatesByGoldAndCapsPerCall() {
        PlayerState s = NewState.create();
        s.zone = "guixu";   // 深度 12 → 单杀 650 金
        s.gold = new BigDecimal("6500");
        Map<String, Object> r = game.sweep(s, 500);
        assertEquals(10L, r.get("swept"), "余额只够 10 只应截断为 10（且单次上限 200 在此不 binding）");
        assertEquals(0, BigDecimal.ZERO.compareTo(s.gold));
        PlayerState rich = NewState.create();
        rich.zone = "plain";
        rich.gold = new BigDecimal("10000000");
        assertEquals(200L, game.sweep(rich, 999).get("swept"), "单次上限 200");
    }

    @Test
    void sweepRejectsSafeZoneAndZeroCount() {
        PlayerState s = NewState.create();
        s.zone = "village";
        s.gold = new BigDecimal("100000");
        assertThrows(ApiException.class, () -> game.sweep(s, 10), "安全区应拒绝");
        s.zone = "plain";
        assertThrows(ApiException.class, () -> game.sweep(s, 0), "数量 0 应拒绝");
        PlayerState broke = NewState.create();
        broke.zone = "plain";
        broke.gold = new BigDecimal("50");
        ApiException e = assertThrows(ApiException.class, () -> game.sweep(broke, 1));
        assertTrue(e.getMessage().contains("金币不足"));
    }
}
