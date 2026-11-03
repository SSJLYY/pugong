package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第七批第 3 轮：套装加成进面板——2/4 件增量精确，且沉默不压制装备来源的套装加成。 */
class SetPanelTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private static PlayerState.Item worn(String slot, String code) {
        PlayerState.Item it = new PlayerState.Item();
        it.slot = slot;
        it.code = code;
        it.name = code;
        return it;
    }

    private static void wearFarwalker(PlayerState s, int pieces) {
        String[][] all = {{"head", "leather_cap"}, {"chest", "leather_armor"},
                {"legs", "leather_pants"}, {"boots", "straw_sandals"}};
        for (int i = 0; i < pieces; i++) {
            s.equipped.put(all[i][0], worn(all[i][0], all[i][1]));
        }
    }

    @Test
    void twoPiecesAddFlatBonus() {
        PlayerState s = NewState.create();
        PanelCalculator.Panel base = PanelCalculator.of(s);
        wearFarwalker(s, 2);
        PanelCalculator.Panel p = PanelCalculator.of(s);
        assertEquals(0, p.maxHP().compareTo(Num.add(base.maxHP(), Num.of(150))), "2 件套 +150 生命");
        assertEquals(0, p.REGEN().compareTo(base.REGEN()), "2 件套不动回血");
    }

    @Test
    void fourPiecesStackBothTiers() {
        PlayerState s = NewState.create();
        PanelCalculator.Panel base = PanelCalculator.of(s);
        wearFarwalker(s, 4);
        PanelCalculator.Panel p = PanelCalculator.of(s);
        assertEquals(0, p.maxHP().compareTo(Num.add(base.maxHP(), Num.of(500))), "4 件套 +500 生命");
        assertEquals(0, p.REGEN().compareTo(Num.add(base.REGEN(), Num.of(5))), "4 件套 +5 回血");
    }

    @Test
    void silenceDoesNotSuppressSetBonus() {
        PlayerState s = NewState.create();
        wearFarwalker(s, 4);
        PanelCalculator.Panel normal = PanelCalculator.of(s);
        s.buffs.put("silence", 10.0);
        PanelCalculator.Panel silenced = PanelCalculator.of(s);
        assertEquals(0, silenced.maxHP().compareTo(normal.maxHP()), "套装加成是装备来源，沉默不压制");
        assertEquals(0, silenced.REGEN().compareTo(normal.REGEN()));
    }
}
