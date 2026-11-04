package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第八批第 9 轮：宝石的口径一致性——
 * ① 宝石属装备来源：Boss 沉默压制被动/药剂，但不压制宝石（面板仍生效）；
 * ② equipScore 计入宝石（一阶 +80、二阶 +200），自动换装不会把镶钻装当裸装。
 */
class GemConsistencyTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void gemsSurviveSilence() {
        PlayerState s = NewState.create();
        PlayerState.Item it = new PlayerState.Item();
        it.name = "钻剑";
        it.slot = "weapon";
        it.gems = List.of("gem_ruby");
        s.equipped.put("weapon", it);
        s.buffs.put("silence", 10.0);
        assertTrue(PanelCalculator.isSilenced(s), "前提：应处于沉默");
        assertEquals(0, PanelCalculator.of(s).PATK().compareTo(Num.of(62)),
                "沉默不应压制装备来源的宝石加成（2 基础 + 60）");
    }

    @Test
    void equipScoreCountsGems() {
        PlayerState.Item bare = new PlayerState.Item();
        bare.name = "裸装";
        PlayerState.Item gemmed = new PlayerState.Item();
        gemmed.name = "镶钻";
        gemmed.gems = List.of("gem_ruby", "gem_ruby_2");
        double diff = GameService.equipScore(gemmed) - GameService.equipScore(bare);
        assertEquals(280.0, diff, 0.001, "一阶 +80 与二阶 +200 应计入评分");
    }
}
