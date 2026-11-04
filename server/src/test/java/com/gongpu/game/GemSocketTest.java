package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.ApiException;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 第八批第 2 轮：镶嵌/拆卸动作——消耗材料仓宝石、孔位上限、拆卸 200 金返还；
 * 无宝石/越界/金币不足均拒绝。
 */
class GemSocketTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState withItem() {
        PlayerState s = NewState.create();
        PlayerState.Item it = new PlayerState.Item();
        it.name = "孔剑";
        s.equipBag.add(it);
        return s;
    }

    @Test
    void socketConsumesAndCaps() {
        PlayerState s = withItem();
        GameService g = new GameService();
        s.materials.put("gem_ruby", 4);
        g.socketGem(s, 0, "gem_ruby");
        g.socketGem(s, 0, "gem_ruby");
        g.socketGem(s, 0, "gem_ruby");
        assertEquals(1, s.materials.get("gem_ruby"));
        assertEquals(3, s.equipBag.get(0).gems.size());
        assertEquals(3, s.stats.gemsSocketed);
        assertThrows(ApiException.class, () -> g.socketGem(s, 0, "gem_ruby"), "第四孔应拒绝");
    }

    @Test
    void socketRejectsMissingGemAndBadIndex() {
        PlayerState s = withItem();
        GameService g = new GameService();
        assertThrows(ApiException.class, () -> g.socketGem(s, 0, "gem_ruby"), "无宝石应拒绝");
        assertThrows(ApiException.class, () -> g.socketGem(s, 5, "gem_ruby"), "越界应拒绝");
        assertThrows(ApiException.class, () -> g.socketGem(s, 0, "gem_fake"), "假宝石应拒绝");
    }

    @Test
    void fuseThreeIntoTier2() {
        PlayerState s = withItem();
        GameService g = new GameService();
        s.materials.put("gem_ruby", 5);
        java.util.Map<String, Object> r = g.fuseGem(s, "gem_ruby");
        assertEquals("gem_ruby_2", r.get("gem"));
        assertEquals(2, s.materials.get("gem_ruby"));
        assertEquals(1, s.materials.get("gem_ruby_2"));
        // 二阶属性 ×2.5：红宝石 60 → 150
        assertEquals(0, GameConst.GEMS.get("gem_ruby_2").stats().get("PATK")
                .compareTo(new java.math.BigDecimal("150")));
        assertThrows(ApiException.class, () -> g.fuseGem(s, "gem_ruby_2"), "二阶不可再融合");
        assertThrows(ApiException.class, () -> g.fuseGem(s, "gem_ruby"), "不足 3 颗应拒绝");
    }

    @Test
    void unsocketRefundsForGold() {
        PlayerState s = withItem();
        GameService g = new GameService();
        s.materials.put("gem_topaz", 1);
        s.gold = Num.of(1000);
        g.socketGem(s, 0, "gem_topaz");
        g.unsocketGem(s, 0, 0);
        assertEquals(1, s.materials.get("gem_topaz"), "拆卸应返还宝石");
        assertEquals(0, s.equipBag.get(0).gems.size());
        assertEquals(0, s.gold.compareTo(Num.of(800)), "拆卸应扣 200 金");
        assertThrows(ApiException.class, () -> g.unsocketGem(s, 0, 0), "空孔拆卸应拒绝");
    }
}
