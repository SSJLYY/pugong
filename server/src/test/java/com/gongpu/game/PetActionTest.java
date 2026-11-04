package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import com.gongpu.common.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** 第九批·宠物领养/切换/喂食（第 4 轮）。 */
class PetActionTest {

    private PlayerState rich() {
        PlayerState s = NewState.create();
        s.gold = new BigDecimal("100000");
        return s;
    }

    @Test
    void adoptDeductsAndActivates() {
        PlayerState s = rich();
        GameService g = new GameService();
        g.adoptPet(s, "pet_cat");
        assertEquals("pet_cat", s.pet);
        assertTrue(s.petsOwned.contains("pet_cat"));
        assertEquals(0, new BigDecimal("98000").compareTo(s.gold));
        assertThrows(ApiException.class, () -> g.adoptPet(s, "pet_cat"));
    }

    @Test
    void adoptRequiresGold() {
        PlayerState s = NewState.create();
        s.gold = BigDecimal.ZERO;
        assertThrows(ApiException.class, () -> new GameService().adoptPet(s, "pet_cat"));
    }

    @Test
    void switchRequiresOwned() {
        PlayerState s = rich();
        GameService g = new GameService();
        assertThrows(ApiException.class, () -> g.switchPet(s, "pet_dog"));
        g.adoptPet(s, "pet_dog");
        g.adoptPet(s, "pet_cat");
        g.switchPet(s, "pet_cat");
        assertEquals("pet_cat", s.pet);
    }

    @Test
    void feedLevelsUp() {
        PlayerState s = rich();
        GameService g = new GameService();
        g.adoptPet(s, "pet_cat");
        g.feedPet(s);   // +10 exp, need 20
        assertEquals(1, s.petLevels.get("pet_cat"));
        g.feedPet(s);   // +10 → 20 升级
        assertEquals(2, s.petLevels.get("pet_cat"));
        assertEquals(2, s.stats.petFeeds);
    }

    @Test
    void gainExpMultiLevelAndCap() {
        PlayerState s = rich();
        new GameService().adoptPet(s, "pet_cat");
        int[] r = GameService.gainPetExp(s, 1000);
        assertTrue(r[1] > 1);
        int[] cap = GameService.gainPetExp(s, 1_000_000);
        assertEquals(GameConst.PET_MAX_LEVEL, cap[0]);
        assertEquals(0.0, s.petExps.get("pet_cat"));
    }
}
