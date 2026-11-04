package com.gongpu.game;

import com.gongpu.common.ApiException;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** 第九批·宠物改名（第 47 轮）。 */
class PetRenameTest {

    @Test
    void renameAndClear() {
        PlayerState s = NewState.create();
        s.gold = new BigDecimal("10000");
        GameService g = new GameService();
        g.adoptPet(s, "pet_cat");
        g.renamePet(s, "pet_cat", "小金库");
        assertEquals("小金库", GameService.petDisplayName(s, "pet_cat"));
        assertThrows(ApiException.class, () -> g.renamePet(s, "pet_cat", "一二三四五六七八九"));
        assertThrows(ApiException.class, () -> g.renamePet(s, "pet_dog", "阿黄"));
        g.renamePet(s, "pet_cat", "");
        assertEquals("招财猫", GameService.petDisplayName(s, "pet_cat"));
    }
}
