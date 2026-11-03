package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第四批第 10 轮：品药成就——按「用过的药剂种类数」评估（potion_5 / potion_15），
 * 同一种喝再多次也只算一种；usedPotions 随 usePotion 记录。
 */
class PotionAchievementTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void fiveDistinctKindsGrantPotion5() {
        PlayerState s = NewState.create();
        GameService g = new GameService();
        for (String id : List.of("hp_s", "mp", "cure", "spd", "food")) {
            s.potions.put(id, 1);
            g.usePotion(s, id);
        }
        List<String> got = Achievements.evaluate(s);
        assertTrue(got.contains("potion_5"), "5 种不同药剂应点亮品鉴五味");
        assertFalse(got.contains("potion_15"));
    }

    @Test
    void sameKindRepeatedlyDoesNotCount() {
        PlayerState s = NewState.create();
        GameService g = new GameService();
        s.potions.put("hp_s", 6);
        for (int i = 0; i < 6; i++) {
            g.usePotion(s, "hp_s");
        }
        assertFalse(Achievements.evaluate(s).contains("potion_5"),
                "同一种喝 6 次仍是 1 种，不应点亮");
    }
}
