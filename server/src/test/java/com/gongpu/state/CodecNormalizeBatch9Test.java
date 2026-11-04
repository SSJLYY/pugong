package com.gongpu.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第九批第 69 轮：normalize 兜底——显式 null 的第九批集合字段须重建，
 * 出战宠物非法（未拥有/图鉴外）须下场，宠物等级/经验须钳制，防坏档把 petView 打成 500。
 */
class CodecNormalizeBatch9Test {

    @Test
    void explicitNullsAreRebuilt() {
        PlayerState s = StateCodec.decode("{\"petsOwned\":null,\"petLevels\":null,"
                + "\"petExps\":null,\"petNames\":null,\"monthlyTasks\":null,"
                + "\"weeklyTasks\":null,\"dailyTasks\":null}");
        assertNotNull(s.petsOwned);
        assertNotNull(s.petLevels);
        assertNotNull(s.petExps);
        assertNotNull(s.petNames);
        assertNotNull(s.monthlyTasks);
        assertNotNull(s.weeklyTasks);
        assertNotNull(s.dailyTasks);
        assertTrue(GameServicePetViewSafe(s));
    }

    private boolean GameServicePetViewSafe(PlayerState s) {
        return com.gongpu.game.GameService.petView(s) != null;
    }

    @Test
    void illegalActivePetIsBenchedAndLevelsClamped() {
        PlayerState s = NewState.create();
        s.pet = "pet_cat"; // 未拥有
        s.petLevels.put("pet_cat", 999);
        s.petExps.put("pet_cat", -5.0);
        PlayerState back = StateCodec.decode(StateCodec.encode(s));
        assertNull(back.pet, "未拥有的出战宠物应被下场");
        assertEquals(com.gongpu.game.GameConst.PET_MAX_LEVEL, back.petLevels.get("pet_cat"));
        assertEquals(0.0, back.petExps.get("pet_cat"));
    }
}
