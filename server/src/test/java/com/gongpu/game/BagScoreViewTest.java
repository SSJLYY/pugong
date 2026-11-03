package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 第五批第 8 轮：背包评分下发——bagScores 与 bag 同序、equippedScores 按部位，
 * 数值与 equipScore 单一口径（自动换装/整理共用）。
 */
class BagScoreViewTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState.Item item(String slot, int quality, String baseVal) {
        PlayerState.Item it = new PlayerState.Item();
        it.name = "测试装";
        it.slot = slot;
        it.quality = quality;
        it.base.put("PATK", Num.of(Long.parseLong(baseVal)));
        return it;
    }

    @Test
    @SuppressWarnings("unchecked")
    void scoresAlignWithBagAndEquipped() {
        PlayerState s = NewState.create();
        PlayerState.Item weak = item("weapon", 0, "10");
        PlayerState.Item strong = item("weapon", 2, "50");
        s.equipBag.add(weak);
        s.equipBag.add(strong);
        s.equipped.put("weapon", item("weapon", 1, "30"));
        Map<String, Object> v = new GameService().inventoryView(s);
        List<Double> scores = (List<Double>) v.get("bagScores");
        assertEquals(2, scores.size());
        assertEquals(GameService.equipScore(weak), scores.get(0), 1e-9);
        assertEquals(GameService.equipScore(strong), scores.get(1), 1e-9);
        Map<String, Double> eq = (Map<String, Double>) v.get("equippedScores");
        assertEquals(GameService.equipScore(s.equipped.get("weapon")), eq.get("weapon"), 1e-9);
    }

    /** 第七批第 65 轮：可售总价预览——未锁定件按 sellPrice 汇总，锁定件不计。 */
    @Test
    void sellTotalExcludesLocked() {
        PlayerState s = NewState.create();
        PlayerState.Item a = item("weapon", 0, "10");
        PlayerState.Item b = item("weapon", 2, "50");
        PlayerState.Item locked = item("weapon", 4, "999");
        locked.locked = true;
        s.equipBag.add(a);
        s.equipBag.add(b);
        s.equipBag.add(locked);
        Map<String, Object> v = new GameService().inventoryView(s);
        java.math.BigDecimal expected = EquipFactory.sellPrice(a).add(EquipFactory.sellPrice(b));
        assertEquals(0, new java.math.BigDecimal(String.valueOf(v.get("bagSellTotal"))).compareTo(expected),
                "可售总价应为未锁定件 sellPrice 之和");
    }

    @Test
    void scoreFormulaSanity() {
        // 品质×1000 + 词条数×100 + 底子和，强化 +5%/级
        PlayerState.Item it = item("weapon", 1, "100");
        assertEquals(2100.0, GameService.equipScore(it), 1e-9);
        it.enhance = 2;
        assertEquals(2100.0 * 1.1, GameService.equipScore(it), 1e-9);
    }
}
