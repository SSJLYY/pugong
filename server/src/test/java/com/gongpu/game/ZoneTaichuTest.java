package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十三批集群二：太初界（第十四区域）结构与口径锁。 */
class ZoneTaichuTest {

    @Test
    void taichuIsDeepestZoneWithGateAndBoss() {
        assertEquals("taichu", GameConst.ZONES.keySet().stream().reduce((a, b) -> b).orElse(""), "太初应为最深区域");
        assertEquals("taichu", GameEngine.nextZoneId("guixu"), "归墟的下一站是太初");
        GameConst.ZoneDef z = GameConst.ZONES.get("taichu");
        assertEquals("5000000000000", z.req().get("maxHP"));
        assertEquals("50000", z.req().get("PDEF"));
        assertEquals("2500000", z.req().get("ATK"));
        assertEquals(4, z.mobs().size());
        assertEquals("500000000000000", z.boss().maxHP(), "源祖 5e14 血");
        assertEquals(14, GameConst.ZONES.size(), "区域共 14");
        assertEquals(53, GameConst.codexTotal(), "图鉴 53（太初 +5）");
    }

    @Test
    void taichuMaterialSetAndPool() {
        assertEquals("taichu_crystal", GameConst.ZONE_MAT.get("taichu"));
        assertEquals(200000, GameConst.MAT_SELL_PRICE.get("taichu_crystal"));
        assertTrue(Sets.byId("taichusuit") != null, "太初战装第十二套应注册");
        assertEquals(12, Sets.ALL.size(), "套装共 12");
        List<String> pool = GameConst.traitPool(13);
        assertTrue(pool.containsAll(GameConst.traitPool(12)), "太初池应包含归墟池全部词条");
        assertTrue(pool.contains("phantom") && pool.contains("tough_hide") && pool.contains("life_eater"),
                "太初池应含第十三批三新词条");
    }

    @Test
    void taichuAchievementsFollowCodexAndBoss() {
        PlayerState s = NewState.create();
        assertEquals(0, Achievements.taichuCodexHits(s));
        s.codex.add("taichu_beast");
        assertEquals(1, Achievements.taichuCodexHits(s));
        s.bossKills.add("boss_taichu");
        assertTrue(Achievements.evaluate(s).contains("taichu_first"));
        assertTrue(Achievements.evaluate(s).contains("taichu_lord"));
    }
}
