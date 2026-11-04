package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 第九批·宠物视图挂 fullView 本体（第 6 轮，防第七/八批同类误挂事故）。 */
class PetViewTest {

    @Test
    void petViewShape() {
        PlayerState s = NewState.create();
        Map<String, Object> v = GameService.petView(s);
        assertNull(v.get("active"));
        s.pet = "pet_hawk";
        s.petsOwned.add("pet_hawk");
        s.petLevels.put("pet_hawk", 3);
        s.petExps.put("pet_hawk", 5.0);
        v = GameService.petView(s);
        assertEquals("pet_hawk", v.get("active"));
        assertEquals(3, v.get("level"));
        assertEquals(GameConst.petExpNeed(3), v.get("expNeed"));
    }

    @Test
    void fullViewSourceCarriesPet() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/gongpu/game/GameService.java"));
        int full = src.indexOf("public Map<String, Object> fullView(PlayerState s)");
        int pet = src.indexOf("m.put(\"pet\", petView(s))", full);
        assertTrue(full > 0 && pet > full, "pet 必须挂在 fullView 本体内");
    }
}
