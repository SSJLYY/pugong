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

/** 第十二批集群一：宝石三阶（融合链、级联批量、面板计入、评分与售价）。 */
class GemTier3Test {

    private final GameService game = new GameService();

    @Test
    void tier3DefsExist() {
        assertEquals(18, GameConst.GEMS.size(), "宝石 18 种");
        assertEquals(6, GameConst.gemTier1Ids().size(), "掉落池仍只有一阶 6 种");
        assertEquals(6, GameConst.gemTier2Ids().size());
        GameConst.GemDef t3 = GameConst.GEMS.get("gem_ruby_3");
        assertEquals(0, t3.stats().get("PATK").compareTo(new BigDecimal("330")), "三阶红宝石 = 60 × 5.5");
        assertEquals(7200, GameConst.GEM_SELL_PRICE.get("gem_topaz_3"), "三阶黄玉售价 = 一阶 ×9");
    }

    @Test
    void fuseChainTier1ToTier3() {
        PlayerState s = NewState.create();
        s.materials.put("gem_ruby", 9);
        game.fuseGem(s, "gem_ruby");
        game.fuseGem(s, "gem_ruby");
        game.fuseGem(s, "gem_ruby");
        assertEquals(3, s.materials.get("gem_ruby_2"));
        Map<String, Object> r = game.fuseGem(s, "gem_ruby_2");
        assertEquals("gem_ruby_3", r.get("gem"));
        assertEquals(1, s.materials.get("gem_ruby_3"));
        assertEquals(1, s.stats.gemTier3Fused);
        assertThrows(ApiException.class, () -> game.fuseGem(s, "gem_ruby_3"), "三阶不可再融合");
        PlayerState poor = NewState.create();
        poor.materials.put("gem_ruby_2", 2);
        assertThrows(ApiException.class, () -> game.fuseGem(poor, "gem_ruby_2"), "二阶不足 3 颗不可融合");
    }

    @Test
    void fuseAllCascadesToTier3() {
        PlayerState s = NewState.create();
        s.materials.put("gem_emerald", 27);
        Map<String, Object> r = game.fuseAllGems(s);
        assertEquals(9, r.get("fused"), "27 颗一阶融 9 颗二阶");
        assertEquals(3, r.get("fusedTier3"), "9 颗二阶级联融 3 颗三阶");
        assertEquals(3, s.materials.get("gem_emerald_3"));
        assertEquals(3, s.stats.gemTier3Fused);
    }

    @Test
    void tier3GemAppliesToPanelAndScore() {
        PlayerState s = NewState.create();
        PlayerState.Item it = new PlayerState.Item();
        it.name = "测试剑";
        it.slot = "weapon";
        it.quality = 3;
        it.base.put("PATK", new BigDecimal("100"));
        it.gems.add("gem_ruby_3");
        s.equipped.put("weapon", it);
        // 面板 PATK 应含三阶红宝石 +330（装备来源）
        assertTrue(PanelCalculator.of(s).PATK().compareTo(new BigDecimal("400")) > 0,
                "三阶宝石应计入面板，PATK=" + PanelCalculator.of(s).PATK());
        PlayerState.Item bare = new PlayerState.Item();
        bare.name = "测试剑";
        bare.slot = "weapon";
        bare.quality = 3;
        bare.base.put("PATK", new BigDecimal("100"));
        assertTrue(GameService.equipScore(it) > GameService.equipScore(bare) + 400,
                "三阶宝石评分 +500 应显著拉开");
    }

    @Test
    void tier3Achievement() {
        PlayerState s = NewState.create();
        s.stats.gemTier3Fused = 1;
        assertTrue(Achievements.evaluate(s).contains("tier3_1"));
    }
}
