package com.gongpu.game;

import com.gongpu.common.ApiException;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 第九批·宝石批量融合与一键拆卸（第 42 轮）。 */
class GemBatchTest {

    @Test
    void fuseAllConsumesTriples() {
        PlayerState s = NewState.create();
        s.materials.put("gem_ruby", 7);
        s.materials.put("gem_opal", 2);
        Map<String, Object> r = new GameService().fuseAllGems(s);
        assertEquals(2, ((Number) r.get("fused")).intValue());
        assertEquals(1, s.materials.get("gem_ruby"));
        assertEquals(2, s.materials.get("gem_ruby_2"));
        assertEquals(2, s.stats.gemsFused);
        assertThrows(ApiException.class, () -> new GameService().fuseAllGems(s));
    }

    @Test
    void unsocketAllChargesPerGem() {
        PlayerState s = NewState.create();
        s.gold = new BigDecimal("10000");
        PlayerState.Item it = new PlayerState.Item();
        it.code = "star_blade";
        it.name = "星陨之刃";
        it.slot = "weapon";
        it.gems = new ArrayList<>(List.of("gem_ruby", "gem_opal_2"));
        s.equipBag.add(it);
        Map<String, Object> r = new GameService().unsocketAll(s);
        assertEquals(2, ((Number) r.get("removed")).intValue());
        assertTrue(it.gems.isEmpty());
        assertEquals(1, s.materials.get("gem_ruby"));
        assertEquals(0, new BigDecimal("9600").compareTo(s.gold));
    }
}
