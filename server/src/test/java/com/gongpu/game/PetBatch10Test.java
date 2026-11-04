package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十批宠物深化：新宠 2 只、羁绊倍率、经验分享、宠物零食。 */
class PetBatch10Test {

    @org.junit.jupiter.api.BeforeAll
    static void initData() {
        new com.gongpu.cfg.GameData(null, null, null, null, null, null, null);
    }

    @Test
    void eightPetsRegistered() {
        assertEquals(8, GameConst.PETS.size(), "宠物 8 只");
        assertTrue(GameConst.PETS.containsKey("pet_panda"));
        assertTrue(GameConst.PETS.containsKey("pet_ferret"));
    }

    @Test
    void bondMulTiers() {
        assertEquals(1.0, GameConst.petBondMul(29, false));
        assertEquals(1.25, GameConst.petBondMul(30, false));
        assertEquals(1.6, GameConst.petBondMul(100, false));
        assertEquals(2.2, GameConst.petBondMul(250, false));
        assertEquals(3.3, GameConst.petBondMul(250, true), 1e-9, "混沌之心羁绊 ×1.5");
    }

    @Test
    void bondMultipliesPanelPetBonus() {
        PlayerState s = NewState.create();
        s.petsOwned.add("pet_cat");
        s.pet = "pet_cat";
        s.petLevels.put("pet_cat", 30); // 总等级 30 → 羁绊 1.25
        Map<String, BigDecimal> bonus = PanelCalculator.petBonus(s);
        // petBonus 本身不含羁绊（羁绊在 PanelCalculator 乘区）；直接验面板 PATK 增量
        BigDecimal withBond = PanelCalculator.of(s).PATK();
        s.petLevels.put("pet_cat", 29); // 总等级 29 → 无羁绊
        BigDecimal noBond = PanelCalculator.of(s).PATK();
        assertTrue(withBond.compareTo(noBond) > 0, "羁绊应放大面板");
        assertTrue(bonus.get("PATK").signum() > 0);
    }

    @Test
    void expSharedWithBenchedPets() {
        PlayerState s = NewState.create();
        s.petsOwned.add("pet_cat");
        s.petsOwned.add("pet_dog");
        s.pet = "pet_cat";
        s.petLevels.put("pet_cat", 1);
        s.petLevels.put("pet_dog", 1);
        GameService.gainPetExp(s, 100);
        // 出战猫吃满 100 经验连升；忠犬分享 25 → 升 1 级（门槛 20）余 5
        assertTrue(s.petLevels.get("pet_cat") > 1, "出战宠物应升级");
        assertEquals(2, s.petLevels.get("pet_dog"), "非出战宠物分享 25% 经验也升级");
        assertEquals(5.0, s.petExps.get("pet_dog"));
    }

    @Test
    void petTreatGivesExpAndCounts() {
        PlayerState s = NewState.create();
        s.petsOwned.add("pet_cat");
        s.pet = "pet_cat";
        s.petLevels.put("pet_cat", 1);
        s.potions.put("pet_treat", 2);
        GameService gs = new GameService();
        Map<String, Object> r = gs.usePotion(s, "pet_treat");
        assertEquals(Boolean.TRUE, r.get("ok"));
        assertEquals(1L, s.stats.petTreats);
        assertEquals(1, s.potions.get("pet_treat"));
        // 60 经验：20+30 两级门槛共 50 → 升到 3 级余 10
        assertEquals(3, s.petLevels.get("pet_cat"));
    }
}
