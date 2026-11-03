package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 第七批第 6 轮：套装与无相位栏（baglord）口径——extra 槽计件，但同名重复件不重复计件；
 * 面板加成对 extra 槽同样生效。
 */
class SetExtraSlotTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private static PlayerState.Item worn(String code) {
        PlayerState.Item it = new PlayerState.Item();
        it.code = code;
        it.name = code;
        it.slot = "head";
        return it;
    }

    @Test
    void extraSlotsCountTowardSet() {
        PlayerState s = NewState.create();
        s.equipped.put("extra_0", worn("leather_cap"));
        s.equipped.put("extra_1", worn("leather_armor"));
        assertEquals(2, Sets.wornCounts(s).get("farwalker"), "无相位栏穿戴应计套装件数");
        PanelCalculator.Panel base = PanelCalculator.of(NewState.create());
        PanelCalculator.Panel p = PanelCalculator.of(s);
        assertEquals(0, p.maxHP().compareTo(Num.add(base.maxHP(), Num.of(150))), "extra 槽套装加成进面板");
    }

    @Test
    void duplicatePiecesDoNotDoubleCount() {
        PlayerState s = NewState.create();
        s.equipped.put("head", worn("leather_cap"));
        s.equipped.put("extra_0", worn("leather_cap"));
        s.equipped.put("extra_1", worn("leather_cap"));
        s.equipped.put("extra_2", worn("leather_cap"));
        assertEquals(1, Sets.wornCounts(s).get("farwalker"), "4 件同款帽子只算 1 件（按不同成员计数）");
    }
}
