package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十二批集群二：归墟界（第十三区域）结构与口径。 */
class ZoneGuixuTest {

    @Test
    void guixuZoneShape() {
        assertEquals(13, GameConst.ZONES.size(), "区域 13");
        GameConst.ZoneDef z = GameConst.ZONES.get("guixu");
        assertEquals("归墟界", z.name());
        assertEquals("500000000000", z.req().get("maxHP"));
        assertEquals("20000", z.req().get("PDEF"));
        assertEquals("1000000", z.req().get("ATK"));
        assertEquals(4, z.mobs().size(), "归墟界 4 只普通怪");
        assertEquals("boss_guixu", z.boss().id());
        assertEquals("50000000000000", z.boss().maxHP(), "归墟之主 5e13 血");
        assertEquals("guixu", GameEngine.nextZoneId("hongmeng"));
        assertEquals(null, GameEngine.nextZoneId("guixu"), "归墟界为最深区域");
    }

    @Test
    void guixuMaterialAndSet() {
        assertEquals("guixu_pearl", GameConst.ZONE_MAT.get("guixu"));
        assertEquals(60000, GameConst.MAT_SELL_PRICE.get("guixu_pearl"));
        assertEquals(11, Sets.ALL.size(), "套装 11");
        assertTrue(Sets.byId("guixusuit") != null);
        assertEquals(48, GameConst.codexTotal(), "图鉴 48");
    }

    @Test
    void guixuTraitPoolMirrorsHongmengForNow() {
        var pool = GameConst.traitPool(12);
        assertEquals(GameConst.traitPool(11).size() + 1, pool.size(), "归墟池 = 鸿蒙池 + 雷霆（专属）");
        assertTrue(pool.contains("taixu"));
    }

    @Test
    void guixuAchievements() {
        PlayerState s = NewState.create();
        s.codex.add("guixu_wraith");
        assertTrue(Achievements.evaluate(s).contains("guixu_first"));
        s.bossKills.add("boss_guixu");
        assertTrue(Achievements.evaluate(s).contains("guixu_lord"));
        assertEquals(1, Achievements.guixuCodexHits(s));
    }
}
