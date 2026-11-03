package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import com.gongpu.state.StateCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第七批第 15 轮：洗炼保底——连续 9 次未出史诗后第 10 次必出（计数清零）；
 * 未出史诗时计数 +1（用锁定唯一词条构造确定性无史诗结果）；计数随存档往返。
 */
class RerollPityTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private final GameService game = new GameService();

    private static PlayerState.Item itemWithAffix(String rarity) {
        PlayerState.Item it = new PlayerState.Item();
        it.name = "测试剑";
        it.slot = "weapon";
        it.quality = 0;   // 1 条词条
        PlayerState.AffixInst a = new PlayerState.AffixInst();
        a.name = "力量";
        a.key = "PATK";
        a.type = "flat";
        a.val = Num.of(5);
        a.rarity = rarity;
        it.affixes.add(a);
        return it;
    }

    @Test
    void pityForcesEpicOnTenthReroll() {
        PlayerState s = NewState.create();
        s.gold = Num.of(1_000_000);
        s.rerollPity = GameConst.REROLL_PITY_MAX - 1;
        s.equipBag.add(itemWithAffix("common"));
        Map<String, Object> r = game.reroll(s, 0, -1);
        PlayerState.Item it = s.equipBag.get(0);
        assertTrue(it.affixes.stream().anyMatch(a -> "epic".equals(a.rarity)),
                "保底触发时洗炼结果必须含史诗词条");
        assertEquals(0, s.rerollPity, "出史诗后保底计数清零");
        assertEquals(Boolean.TRUE, r.get("pityTriggered"));
        assertEquals(1, s.stats.rerolls);
    }

    @Test
    void pityIncrementsWhenNoEpic() {
        PlayerState s = NewState.create();
        s.gold = Num.of(1_000_000);
        s.equipBag.add(itemWithAffix("common"));
        // 锁定唯一词条：结果只有被锁的普通词条，确定性地不出史诗
        Map<String, Object> r = game.reroll(s, 0, 0);
        assertEquals(1, s.rerollPity, "未出史诗应计数 +1");
        assertEquals(1, r.get("pity"));
        assertEquals(GameConst.REROLL_PITY_MAX, r.get("pityMax"));
        assertEquals(Boolean.FALSE, r.get("pityTriggered"));
    }

    @Test
    void pitySurvivesCodec() {
        PlayerState s = NewState.create();
        s.rerollPity = 7;
        PlayerState back = StateCodec.decode(StateCodec.encode(s));
        assertEquals(7, back.rerollPity, "保底计数应随存档往返保留");
    }
}
