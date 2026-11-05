package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十一批集群二：第十二区域鸿蒙界（门槛/Boss/材料/套装/图鉴）。 */
class ZoneHongmengTest {

    @Test
    void hongmengIsTwelfthZone() {
        assertEquals(13, GameConst.ZONES.size(), "区域 12");
        GameConst.ZoneDef z = GameConst.ZONES.get("hongmeng");
        assertNotNull(z);
        assertEquals("50000000000", z.req().get("maxHP"), "门槛生命 500 亿");
        assertEquals("8000", z.req().get("PDEF"));
        assertEquals("400000", z.req().get("ATK"));
        assertEquals(4, z.mobs().size(), "鸿蒙界 4 怪");
        assertEquals("hongmeng", GameEngine.nextZoneId("chaos"), "混沌界之后即鸿蒙界");
        assertEquals("guixu", GameEngine.nextZoneId("hongmeng"), "鸿蒙界之后即归墟界（第十二批）");
        assertEquals("500000000000", z.boss().maxHP(), "Boss 鸿蒙道祖 5000 亿血");
        assertEquals(48, GameConst.codexTotal(), "图鉴 48（43 + 归墟 4 怪 1 Boss）");
    }

    @Test
    void hongmengMaterialChain() {
        assertEquals("hongmeng_qi", GameConst.ZONE_MAT.get("hongmeng"));
        assertEquals("鸿蒙紫气", GameConst.MAT_INFO.get("hongmeng_qi")[0]);
        assertEquals(20000, GameConst.MAT_SELL_PRICE.get("hongmeng_qi"), "鸿蒙紫气售价 2 万");
    }

    @Test
    void hongmengSuitIsTenth() {
        assertEquals(11, Sets.ALL.size(), "套装 11 套");
        assertNotNull(Sets.byId("hongmengsuit"));
    }

    @Test
    void hongmengAchievements() {
        PlayerState s = NewState.create();
        s.codex.add("hongmeng_spirit");
        assertTrue(Achievements.evaluate(s).contains("hongmeng_first"));
        assertEquals(1, Achievements.progressOf(s, "hongmeng_first")[0]);
        s.bossKills.add("boss_hongmeng");
        assertTrue(Achievements.evaluate(s).contains("hongmeng_lord"));
    }

    @Test
    void hongmengTraitPoolMirrorsChaosForNow() {
        var pool = GameConst.traitPool(11);
        assertEquals(GameConst.traitPool(10).size() + 1, pool.size(), "鸿蒙池 = 混沌池 + 太虚（专属）");
        assertTrue(pool.contains("chaos_frenzy"));
        assertTrue(pool.contains("taixu"), "太虚只在鸿蒙池");
        assertTrue(!GameConst.traitPool(10).contains("taixu"), "混沌池不应有太虚");
    }
}
