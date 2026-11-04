package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十批装备升星：成本、面板三乘区、卖价/评分加成、满星拦截。 */
class StarUpTest {

    private static PlayerState.Item item() {
        PlayerState.Item it = new PlayerState.Item();
        it.name = "测试剑";
        it.slot = "weapon";
        it.quality = 2;
        it.base.put("PATK", BigDecimal.valueOf(100));
        return it;
    }

    private static PlayerState rich() {
        PlayerState s = NewState.create();
        s.gold = BigDecimal.valueOf(1_000_000);
        s.materials.put("star_shard", 100);
        return s;
    }

    @Test
    void starUpCostsAndLevels() {
        PlayerState s = rich();
        PlayerState.Item it = item();
        s.equipBag.add(it);
        Map<String, Object> r = new GameService().starUp(s, 0);
        assertEquals(1, it.star);
        assertEquals(1, ((Number) r.get("starredTo")).intValue());
        // 第 1 星：5000 金 + 1 颗星核碎片
        assertEquals(0, s.gold.compareTo(BigDecimal.valueOf(995_000)));
        assertEquals(99, s.materials.get("star_shard"));
        assertEquals(1L, s.stats.starsDone);
    }

    @Test
    void starScalesPanelBase() {
        PlayerState s = rich();
        PlayerState.Item it = item();
        s.equipped.put("weapon", it);
        BigDecimal base = PanelCalculator.of(s).PATK();
        it.star = 5;
        BigDecimal starred = PanelCalculator.of(s).PATK();
        // 底子 100 × 1.15（5 星） vs × 1.0
        assertEquals(0, starred.subtract(base).compareTo(BigDecimal.valueOf(15)),
                "5 星应使底子 +15%");
    }

    @Test
    void sellPriceAndScoreIncludeStar() {
        PlayerState.Item it = item();
        BigDecimal p0 = EquipFactory.sellPrice(it);
        double s0 = GameService.equipScore(it);
        it.star = 5;
        assertEquals(0, EquipFactory.sellPrice(it).compareTo(p0.multiply(new BigDecimal("1.5"))),
                "5 星卖价 ×1.5");
        assertTrue(GameService.equipScore(it) > s0, "升星应提高评分");
    }

    @Test
    void starUpRejectsAtCapAndWhenPoor() {
        PlayerState s = rich();
        PlayerState.Item it = item();
        it.star = GameConst.STAR_MAX_LV;
        s.equipBag.add(it);
        assertThrows(RuntimeException.class, () -> new GameService().starUp(s, 0));
        PlayerState poor = NewState.create();
        poor.equipBag.add(item());
        assertThrows(RuntimeException.class, () -> new GameService().starUp(poor, 0));
    }
}
