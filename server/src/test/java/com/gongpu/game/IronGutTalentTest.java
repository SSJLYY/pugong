package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 第七批第 88 轮：天赋「铁胃」——食物回血 20→30/秒、时长 10→15 秒（手动服用与面板同口径）；
 * 无天赋时一切照旧。天赋池扩至 10 个。
 */
class IronGutTalentTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void ironGutBoostsFoodRegenAndDuration() {
        PlayerState s = NewState.create();
        s.talent = "iron_gut";
        s.potions.put("food", 2);
        new GameService().usePotion(s, "food");
        assertEquals(15.0, s.buffs.get("regen_food"), 1e-9, "铁胃应把食物时长 10s 拉到 15s");
        assertEquals(0, PanelCalculator.of(s).REGEN().compareTo(Num.of(32)),
                "铁胃应把饱食回血 20/秒提到 30/秒（面板含基础回血 2）");
    }

    @Test
    void normalStomachUnchanged() {
        PlayerState s = NewState.create();
        s.potions.put("food", 1);
        new GameService().usePotion(s, "food");
        assertEquals(10.0, s.buffs.get("regen_food"), 1e-9);
        assertEquals(0, PanelCalculator.of(s).REGEN().compareTo(Num.of(22)));
        assertEquals(15.0, GameEngine.foodDur(mkTalent(), 10.0), 1e-9);
        assertEquals(10.0, GameEngine.foodDur(NewState.create(), 10.0), 1e-9);
    }

    private static PlayerState mkTalent() {
        PlayerState s = NewState.create();
        s.talent = "iron_gut";
        return s;
    }

    @Test
    void talentRegistered() {
        assertNotNull(GameConst.TALENTS.get("iron_gut"));
        assertEquals(15, GameConst.TALENTS.size(), "天赋总数应达 11（第八批终版口径）");
    }
}
