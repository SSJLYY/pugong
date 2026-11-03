package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.ApiException;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第七批第 17 轮：洗炼双锁——锁定两条（须 ≥3 条词条、两条不同），费用 ×5，
 * 锁定词条原样保留在前列；与保底叠加时强制史诗落在未锁定区。
 */
class RerollDualLockTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private final GameService game = new GameService();

    private static PlayerState.AffixInst affix(String name, int val) {
        PlayerState.AffixInst a = new PlayerState.AffixInst();
        a.name = name;
        a.key = "PATK";
        a.type = "flat";
        a.val = Num.of(val);
        a.rarity = "common";
        return a;
    }

    private static PlayerState.Item item(int quality, String... names) {
        PlayerState.Item it = new PlayerState.Item();
        it.name = "测试装";
        it.slot = "weapon";
        it.quality = quality;
        int v = 10;
        for (String n : names) {
            it.affixes.add(affix(n, v));
            v += 10;
        }
        return it;
    }

    @Test
    void dualLockKeepsBothAndChargesTimesFive() {
        PlayerState s = NewState.create();
        s.gold = Num.of(1_000_000);
        PlayerState.Item it = item(3, "测试甲", "测试乙", "测试丙", "测试丁");
        s.equipBag.add(it);
        Map<String, Object> r = game.reroll(s, 0, 0, 2);
        assertEquals(4, it.affixes.size(), "史诗品质应保持 4 条词条");
        assertEquals("测试甲", it.affixes.get(0).name, "锁定词条应原样保留（原序置前）");
        assertEquals(0, it.affixes.get(0).val.compareTo(Num.of(10)), "锁定词条数值不变");
        assertEquals("测试丙", it.affixes.get(1).name);
        assertEquals(0, it.affixes.get(1).val.compareTo(Num.of(30)));
        assertEquals(2, r.get("lockedCount"));
        // 基础价 200×(3+1)×(0+1)=800，双锁 ×5 = 4000
        assertEquals(0, s.gold.compareTo(Num.of(1_000_000 - 4000)), "双锁费用应为基础 ×5");
    }

    @Test
    void dualLockValidation() {
        PlayerState s = NewState.create();
        s.gold = Num.of(1_000_000);
        s.equipBag.add(item(3, "测试甲", "测试乙", "测试丙", "测试丁"));
        assertThrows(ApiException.class, () -> game.reroll(s, 0, 1, 1), "两条相同应拒绝");
        assertThrows(ApiException.class, () -> game.reroll(s, 0, 0, 9), "越界锁定应拒绝");

        PlayerState s2 = NewState.create();
        s2.gold = Num.of(1_000_000);
        s2.equipBag.add(item(1, "测试甲", "测试乙"));   // 2 条词条（精良）
        assertThrows(ApiException.class, () -> game.reroll(s2, 0, 0, 1), "不足 3 条词条不能双锁");
    }

    @Test
    void dualLockWithPityForcesEpicIntoFreeSlot() {
        PlayerState s = NewState.create();
        s.gold = Num.of(1_000_000);
        s.rerollPity = GameConst.REROLL_PITY_MAX - 1;
        PlayerState.Item it = item(3, "测试甲", "测试乙", "测试丙", "测试丁");
        s.equipBag.add(it);
        game.reroll(s, 0, 0, 2);
        assertTrue(it.affixes.stream().anyMatch(a -> "epic".equals(a.rarity)),
                "保底应把史诗词条放进未锁定区");
        assertEquals("common", it.affixes.get(0).rarity, "锁定词条不被保底替换");
        assertEquals(0, s.rerollPity);
    }
}
