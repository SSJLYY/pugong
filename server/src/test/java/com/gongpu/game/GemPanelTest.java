package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 第八批第 1 轮：宝石镶嵌的面板口径——已穿装备的宝石 flat 加成并入面板，
 * 背包里的不算；未知宝石 code 静默跳过；六种宝石定义齐全。
 */
class GemPanelTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void gemDefinitionsComplete() {
        assertEquals(6, GameConst.GEMS.size());
        assertEquals(3, GameConst.GEM_SOCKET_MAX);
    }

    @Test
    void socketedGemsBoostPanel() {
        PlayerState s = NewState.create();
        PlayerState.Item it = new PlayerState.Item();
        it.name = "试作剑";
        it.slot = "weapon";
        it.gems = List.of("gem_ruby", "gem_topaz", "gem_opal");
        s.equipped.put("weapon", it);
        PanelCalculator.Panel p = PanelCalculator.of(s);
        PanelCalculator.Panel base = PanelCalculator.of(NewState.create());
        // 基础 PATK 来自 Life（2）+ 红宝石 60
        assertEquals(0, p.PATK().compareTo(Num.of(62)), "红宝石应 +60 物攻");
        assertEquals(0, p.CRIT().compareTo(Num.add(base.CRIT(), Num.of(0.03))), "黄玉应 +3% 暴击");
        assertEquals(0, p.PDEF().compareTo(Num.of(42)), "蛋白石 +40 与基础物防 2");
    }

    @Test
    void bagItemGemsDoNotCount() {
        PlayerState s = NewState.create();
        PlayerState.Item it = new PlayerState.Item();
        it.name = "背包剑";
        it.gems = List.of("gem_ruby", "gem_unknown");
        s.equipBag.add(it);
        PanelCalculator.Panel p = PanelCalculator.of(s);
        assertEquals(0, p.PATK().compareTo(Num.of(2)), "背包装备的宝石不应生效");
    }
}
