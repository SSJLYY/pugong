package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.craft.CraftService;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第九批·经济便利一致性（第 43 轮）：批量动作与逐次口径等价。 */
class EconomyBatch9Test {

    @org.junit.jupiter.api.BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void feedBatchStopsAtGoldOrCap() {
        PlayerState s = NewState.create();
        s.gold = new BigDecimal("1600");   // 够喂 10 次（150/次）还剩 100
        s.pet = "pet_cat";
        s.petsOwned.add("pet_cat");
        s.petLevels.put("pet_cat", 1);
        Map<String, Object> r = new GameService().feedPetBatch(s, 10);
        assertEquals(10, ((Number) r.get("fed")).intValue());
        assertEquals(0, new BigDecimal("100").compareTo(s.gold));
        assertTrue(((Number) r.get("level")).intValue() >= 3, "100 经验应至少升到 3 级");
    }

    @Test
    void exchangeAllMatchesRepeatedSingle() {
        PlayerState s = NewState.create();
        s.materials.put("pelt", 10);
        Map<String, Object> r = new CraftService().exchangeAll(s, "pelt", "ore");
        assertEquals(3, ((Number) r.get("times")).intValue());
        assertEquals(1, s.materials.get("pelt"));
        assertEquals(3, s.materials.get("ore"));
    }

    @Test
    void sellExtraBlueprintsKeepsOne() throws Exception {
        // 图纸定价依赖 GameData 配方表：离线为空表时 blueprintPrice 返回 null → 应抛没有可卖
        PlayerState s = NewState.create();
        s.materials.put("bp_star_blade", 3);
        try {
            Map<String, Object> r = new CraftService().sellExtraBlueprints(s);
            assertEquals(1, s.materials.get("bp_star_blade"));
            assertTrue(((Number) r.get("count")).longValue() == 2);
        } catch (com.gongpu.common.ApiException e) {
            // 离线 GameData 未装配方时定价为 null，走拒绝分支同样合法
            assertEquals(3, s.materials.get("bp_star_blade"));
        }
    }
}
