package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第九批·击杀漏斗宠物经验（第 5 轮）。 */
class PetKillExpTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private static void kill(PlayerState s) throws Exception {
        Method m = GameEngine.class.getDeclaredMethod("onMobKilled", PlayerState.class, PlayerState.MobState.class);
        m.setAccessible(true);
        PlayerState.MobState mob = new PlayerState.MobState();
        mob.id = "rabbit";
        mob.name = "测试怪";
        m.invoke(null, s, mob);
    }

    @Test
    void killsGrantPetExpAndLevel() throws Exception {
        PlayerState s = NewState.create();
        s.pet = "pet_cat";
        s.petsOwned.add("pet_cat");
        s.petLevels.put("pet_cat", 1);
        for (int i = 0; i < 20; i++) {
            kill(s);
        }
        assertEquals(2, s.petLevels.get("pet_cat"));
        assertTrue(s.events.stream().anyMatch(e -> e.contains("升到了 2 级")));
    }

    @Test
    void noPetNoExp() throws Exception {
        PlayerState s = NewState.create();
        kill(s);
        assertTrue(s.petLevels.isEmpty());
    }
}
