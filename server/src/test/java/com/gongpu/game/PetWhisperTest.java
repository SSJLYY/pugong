package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第九批第 87 轮：天赋「兽语者」——宠物经验翻倍（gainPetExp 单点，击杀/喂食同享）。 */
class PetWhisperTest {

    @Test
    void whisperDoublesPetExp() {
        PlayerState s = NewState.create();
        s.petsOwned.add("pet_cat");
        s.pet = "pet_cat";
        s.petLevels.put("pet_cat", 1);
        s.petExps.put("pet_cat", 0.0);
        GameService.gainPetExp(s, 10);
        assertEquals(10.0, s.petExps.get("pet_cat"), "无天赋 10 经验");
        s.talent = "pet_whisper";
        GameService.gainPetExp(s, 10);
        // 翻倍后共 30 经验：升到 2 级（耗 20）余 10——无翻倍则只有 20 刚好升级余 0
        assertEquals(2, s.petLevels.get("pet_cat"), "兽语者翻倍后应升到 2 级");
        assertEquals(10.0, s.petExps.get("pet_cat"), "升级余 10 经验证明入账 30");
        assertEquals(15, GameConst.TALENTS.size(), "天赋 12");
    }
}
