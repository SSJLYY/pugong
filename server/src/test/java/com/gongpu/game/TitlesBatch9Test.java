package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第九批第 74 轮：新称号三条线——petView 须下发 levels 全表（称号/宠物卡共用），
 * 前端 computeTitles 须含驭兽宗师/虚空行者/月度达人且签名守卫覆盖新依赖字段。
 */
class TitlesBatch9Test {

    @Test
    void petViewCarriesAllLevels() {
        PlayerState s = NewState.create();
        s.petsOwned.add("pet_cat");
        s.petsOwned.add("pet_hawk");
        s.pet = "pet_cat";
        s.petLevels.put("pet_cat", 12);
        s.petLevels.put("pet_hawk", 50);
        Map<String, Object> v = GameService.petView(s);
        @SuppressWarnings("unchecked")
        Map<String, Integer> levels = (Map<String, Integer>) v.get("levels");
        assertEquals(50, levels.get("pet_hawk"));
        assertEquals(12, levels.get("pet_cat"));
    }

    @Test
    void frontendTitlesWired() throws Exception {
        String js = Files.readString(Path.of("../web/js/app.js"));
        assertTrue(js.contains("驭兽宗师"), "缺驭兽宗师称号");
        assertTrue(js.contains("虚空行者"), "缺虚空行者称号");
        assertTrue(js.contains("月度达人"), "缺月度达人称号");
        assertTrue(js.contains("boss_void"), "虚空行者须以 boss_void 为条件");
        assertTrue(js.contains("monthlyClaims"), "月度达人须以 monthlyClaims 为条件");
    }
}
