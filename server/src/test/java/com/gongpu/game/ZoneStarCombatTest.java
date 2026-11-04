package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.ApiException;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第八批第 26 轮：星界实战链——门槛拒绝/放行、刷怪属于星界怪种、
 * 淬炼材料走星核碎片（ZONE_MAT 统一口径的回归锁）。
 */
class ZoneStarCombatTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private final GameService game = new GameService();

    @Test
    void gateRejectsWeakAndAdmitsStrong() {
        PlayerState weak = NewState.create();
        assertThrows(ApiException.class, () -> game.gotoZone(weak, "star"), "白板号应被星界门槛拒绝");

        PlayerState s = NewState.create();
        s.life.maxHP = new BigDecimal("50000000");
        s.life.curHP = new BigDecimal("50000000");
        s.life.set("PATK", new BigDecimal("30000"));
        s.life.set("PDEF", new BigDecimal("1000"));
        s.life.set("MDEF", new BigDecimal("1000"));
        game.gotoZone(s, "star");
        assertEquals("star", s.zone);
        assertNotNull(s.mob, "进入星界应刷出野怪");
        assertTrue(GameConst.ZONES.get("star").mobs().stream().anyMatch(d -> d.id().equals(s.mob.id)),
                "刷出的应是星界怪种：" + s.mob.id);
    }

    @Test
    void starGearTempersWithStarShards() {
        PlayerState.Item it = new PlayerState.Item();
        it.name = "星陨之刃";
        it.zone = "star";
        assertEquals("star_shard", GameService.temperMatKey(it), "星界装备淬炼应吃星核碎片");
        PlayerState.Item abyssIt = new PlayerState.Item();
        abyssIt.zone = "abyss";
        assertEquals("abyss_crystal", GameService.temperMatKey(abyssIt));
    }
}
