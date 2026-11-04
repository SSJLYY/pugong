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

/** 第十一批集群五：装备觉醒（满星门槛、四乘区、成本、吞噬取 max、卖价/评分）。 */
class AwakenTest {

    private final GameService game = new GameService();

    private static PlayerState.Item item(int star) {
        PlayerState.Item it = new PlayerState.Item();
        it.name = "测试剑";
        it.slot = "weapon";
        it.quality = 3;
        it.star = star;
        it.base.put("PATK", new BigDecimal("1000"));
        return it;
    }

    private PlayerState rich() {
        PlayerState s = NewState.create();
        s.gold = new BigDecimal("10000000");
        s.materials.put("chaos_core", 20);
        return s;
    }

    @Test
    void awakenFlowAndCosts() {
        PlayerState s = rich();
        PlayerState.Item it = item(5);
        s.equipBag.add(it);
        Map<String, Object> r = game.awaken(s, 0);
        assertEquals(1, it.awaken);
        assertEquals(1, r.get("awakenedTo"));
        assertEquals(1, s.stats.awakens, "觉醒计数 +1");
        // 成本精确：一级 = 100000 金 + 混沌核心 1
        assertEquals(0, new BigDecimal("9900000").compareTo(s.gold));
        assertEquals(19, s.materials.get("chaos_core"));
        game.awaken(s, 0);
        game.awaken(s, 0);
        assertEquals(3, it.awaken, "觉醒满级 3");
        assertThrows(ApiException.class, () -> game.awaken(s, 0), "满级再觉醒应被拒");
    }

    @Test
    void awakenRequiresMaxStar() {
        PlayerState s = rich();
        s.equipBag.add(item(4));
        assertThrows(ApiException.class, () -> game.awaken(s, 0), "未满星不可觉醒");
        PlayerState poor = NewState.create();
        poor.equipBag.add(item(5));
        assertThrows(ApiException.class, () -> game.awaken(poor, 0), "材料/金币不足不可觉醒");
    }

    @Test
    void awakenMultipliesPanelStats() {
        PlayerState s = rich();
        PlayerState.Item it = item(5);
        s.equipped.put("weapon", it);
        BigDecimal before = PanelCalculator.of(s).PATK();
        it.awaken = 3;
        BigDecimal after = PanelCalculator.of(s).PATK();
        assertTrue(after.compareTo(before) > 0, "觉醒应提升面板");
        // 觉醒 3 级因子 ×1.15（double 尾数误差内）：增量 / 原值 ≈ 0.15
        double ratio = after.subtract(before).divide(before, 6, java.math.RoundingMode.HALF_UP).doubleValue();
        assertTrue(Math.abs(ratio - 0.15) < 0.001, "觉醒 3 级增量比应 ≈0.15，实际 " + ratio);
    }

    @Test
    void awakenAchievements() {
        PlayerState s = rich();
        PlayerState.Item it = item(5);
        it.awaken = 1;
        s.equipBag.add(it);
        assertTrue(Achievements.evaluate(s).contains("awaken_1"));
        it.awaken = 3;
        assertTrue(Achievements.evaluate(s).contains("awaken_3"));
        assertEquals(3, Achievements.maxAwaken(s));
    }

    @Test
    void awakenRaisesSellPrice() {
        PlayerState.Item a = item(5);
        PlayerState.Item b = item(5);
        b.awaken = 2;
        assertTrue(EquipFactory.sellPrice(b).compareTo(EquipFactory.sellPrice(a)) > 0, "觉醒件卖价更高");
    }
}
