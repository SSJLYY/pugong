package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.ApiException;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第五批第 1 轮：装备分解——按来源区域出材料（ZONE_MAT 口径）、数量随品质与强化，
 * 锁定件拒绝、旧档无 zone 按平原兽皮兜底、计数入 Stats.dismantles。
 */
class DismantleTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState.Item item(String zone, int quality, int enhance) {
        PlayerState.Item it = new PlayerState.Item();
        it.name = "测试装";
        it.slot = "weapon";
        it.zone = zone;
        it.quality = quality;
        it.enhance = enhance;
        return it;
    }

    @Test
    void yieldByZoneAndQuality() {
        Object[] y = GameService.dismantleYield(item("volcano", 2, 0));
        assertEquals("fire_crystal", y[0]);
        assertEquals(3, y[1], "紫装（品质 2）应出 3 个");
        Object[] y2 = GameService.dismantleYield(item("mine", 0, 6));
        assertEquals("ore", y2[0]);
        assertEquals(2, y2[1], "白装 + 强化 6 应出 1+1");
        Object[] y3 = GameService.dismantleYield(item(null, 1, 0));
        assertEquals("pelt", y3[0], "旧档无 zone 应兜底兽皮");
        assertEquals(2, y3[1]);
    }

    @Test
    void dismantleGrantsMaterialsAndCounts() {
        PlayerState s = NewState.create();
        s.equipBag.add(item("snow", 3, 0));
        Map<String, Object> r = new GameService().dismantle(s, 0);
        assertEquals(4, s.materials.getOrDefault("mana_dust", 0), "史诗雪原装应出 4 魔尘");
        assertEquals(1, s.stats.dismantles);
        assertTrue(s.equipBag.isEmpty());
        @SuppressWarnings("unchecked")
        Map<String, Object> dz = (Map<String, Object>) r.get("dismantled");
        assertEquals("魔尘", dz.get("matName"));
    }

    @Test
    void lockedItemRejected() {
        PlayerState s = NewState.create();
        PlayerState.Item it = item("plain", 0, 0);
        it.locked = true;
        s.equipBag.add(it);
        assertThrows(ApiException.class, () -> new GameService().dismantle(s, 0));
        assertEquals(1, s.equipBag.size(), "锁定件不应被分解");
    }
}
