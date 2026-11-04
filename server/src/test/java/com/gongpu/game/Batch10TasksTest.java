package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十批任务经济扩充：月常/每日新模板、黑市扩池、里程碑 90 档、喂到满级。 */
class Batch10TasksTest {

    @Test
    void templatesExpanded() {
        assertEquals(9, MonthlyQuests.TEMPLATES.size(), "月常模板 9");
        assertEquals(9, DailyQuests.TEMPLATES.size(), "每日模板 9");
        assertEquals(24, BlackMarket.POOL.size(), "黑市池 24");
        assertTrue(BlackMarket.POOL.contains("chaos"));
        assertTrue(BlackMarket.POOL.contains("pet_treat"));
    }

    @Test
    void counterOfNewTypes() {
        PlayerState s = NewState.create();
        s.stats.enchants = 7;
        s.gen = 3;
        s.stats.petFeeds = 11;
        s.stats.gemsFused = 5;
        assertEquals(0, DailyQuests.counterOf(s, "enchant").compareTo(BigDecimal.valueOf(7)));
        assertEquals(0, DailyQuests.counterOf(s, "rebirth").compareTo(BigDecimal.valueOf(3)));
        assertEquals(0, DailyQuests.counterOf(s, "feed").compareTo(BigDecimal.valueOf(11)));
        assertEquals(0, DailyQuests.counterOf(s, "fuse").compareTo(BigDecimal.valueOf(5)));
    }

    @Test
    void milestoneHas90Tier() {
        assertEquals(7, GameService.ACH_MILESTONE_TIERS.length, "里程碑七档");
        assertEquals(105, GameService.ACH_MILESTONE_TIERS[6]);
        assertEquals(3_000_000, GameService.ACH_MILESTONE_BONUS[6]);
    }

    @Test
    void feedPetMaxLevelsToCap() {
        PlayerState s = NewState.create();
        s.petsOwned.add("pet_cat");
        s.pet = "pet_cat";
        s.petLevels.put("pet_cat", 1);
        s.gold = BigDecimal.valueOf(10_000_000);
        Map<String, Object> r = new GameService().feedPetMax(s);
        assertEquals(Boolean.TRUE, r.get("ok"));
        assertEquals(GameConst.PET_MAX_LEVEL, s.petLevels.get("pet_cat"), "应喂到满级");
        assertTrue(((Number) r.get("fed")).intValue() > 1000, "满级需千次以上喂食");
    }

    @Test
    void feedPetMaxStopsWhenBroke() {
        PlayerState s = NewState.create();
        s.petsOwned.add("pet_cat");
        s.pet = "pet_cat";
        s.petLevels.put("pet_cat", 1);
        s.gold = BigDecimal.valueOf(GameConst.PET_FEED_COST * 3L);
        Map<String, Object> r = new GameService().feedPetMax(s);
        assertEquals(3, ((Number) r.get("fed")).intValue(), "金币只够 3 次");
    }
}
