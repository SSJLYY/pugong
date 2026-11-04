package com.gongpu.game;

import com.gongpu.common.ApiException;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十一批集群一：宠物进化（满级 ×2）与新宠麒麟。 */
class PetBatch11Test {

    private final GameService game = new GameService();

    private PlayerState readyCat() {
        PlayerState s = NewState.create();
        s.petsOwned.add("pet_cat");
        s.pet = "pet_cat";
        s.petLevels.put("pet_cat", GameConst.PET_MAX_LEVEL);
        s.gold = new BigDecimal("1000000");
        s.materials.put("chaos_core", 10);
        return s;
    }

    @Test
    void qilinIsNinthPet() {
        assertEquals(9, GameConst.PETS.size(), "宠物 9 只");
        GameConst.PetDef q = GameConst.PETS.get("pet_qilin");
        assertEquals(50000, q.price());
        assertEquals(new BigDecimal("400"), q.stats().get("PATK"));
    }

    @Test
    void evolveDoublesActivePetBonus() {
        PlayerState s = readyCat();
        BigDecimal before = PanelCalculator.petBonus(s).get("PATK");
        Map<String, Object> r = game.evolvePet(s, "pet_cat");
        assertEquals(Boolean.TRUE, r.get("ok"));
        assertTrue(s.petEvolved.contains("pet_cat"));
        assertEquals(1, s.stats.petEvolves, "进化计数 +1");
        BigDecimal after = PanelCalculator.petBonus(s).get("PATK");
        assertEquals(0, before.multiply(new BigDecimal("2")).compareTo(after), "进化后属性恰 ×2");
        // 成本精确扣除：20 万金 + 混沌核心 3
        assertEquals(0, new BigDecimal("800000").compareTo(s.gold));
        assertEquals(7, s.materials.get("chaos_core"));
    }

    @Test
    void evolveGuards() {
        PlayerState s = readyCat();
        s.petLevels.put("pet_cat", 49);
        assertThrows(ApiException.class, () -> game.evolvePet(s, "pet_cat"), "未满级不可进化");
        s.petLevels.put("pet_cat", 50);
        s.materials.put("chaos_core", 2);
        assertThrows(ApiException.class, () -> game.evolvePet(s, "pet_cat"), "材料不足不可进化");
        s.materials.put("chaos_core", 3);
        s.gold = new BigDecimal("199999");
        assertThrows(ApiException.class, () -> game.evolvePet(s, "pet_cat"), "金币不足不可进化");
        s.gold = new BigDecimal("200000");
        game.evolvePet(s, "pet_cat");
        assertThrows(ApiException.class, () -> game.evolvePet(s, "pet_cat"), "不可重复进化");
        assertThrows(ApiException.class, () -> game.evolvePet(s, "pet_qilin"), "未领养不可进化");
    }

    @Test
    void evolveAchievementUnlocks() {
        PlayerState s = readyCat();
        game.evolvePet(s, "pet_cat");
        assertTrue(Achievements.evaluate(s).contains("evolve_1"), "进化成就应解锁");
        long[] p = Achievements.progressOf(s, "evolve_1");
        assertEquals(1, p[0]);
        assertEquals(1, p[1]);
    }

    @Test
    void petViewCarriesEvolved() {
        PlayerState s = readyCat();
        game.evolvePet(s, "pet_cat");
        Map<String, Object> v = GameService.petView(s);
        assertTrue(v.get("evolved").toString().contains("pet_cat"), "petView 应下发 evolved 列表");
    }
}
