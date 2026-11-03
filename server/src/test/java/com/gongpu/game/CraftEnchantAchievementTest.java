package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第三批第 10 轮：成就扩充——first_craft / first_enchant 里程碑。
 * 合成计数在 CraftServiceTest 已锁（成功 +1）；此处锁附魔计数与两成就的评估点亮。
 */
class CraftEnchantAchievementTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void enchantIncrementsCounterAndLightsAchievement() {
        PlayerState s = NewState.create();
        PlayerState.Item w = new PlayerState.Item();
        w.name = "铁剑";
        w.slot = "weapon";
        w.quality = 1;
        s.equipBag.add(w);
        s.materials.put("scroll_fire", 1);
        new GameService().enchant(s, "scroll_fire", 0);
        assertTrue(s.stats.enchants == 1, "附魔成功应计 +1");
        List<String> got = Achievements.evaluate(s);
        assertTrue(got.contains("first_enchant"), "首次附魔应点亮成就");
    }

    @Test
    void craftCounterLightsAchievement() {
        PlayerState s = NewState.create();
        assertTrue(!Achievements.evaluate(s).contains("first_craft"), "未合成不应点亮");
        s.stats.crafts = 1;
        assertTrue(Achievements.evaluate(s).contains("first_craft"), "合成计数 ≥1 应点亮");
    }
}
