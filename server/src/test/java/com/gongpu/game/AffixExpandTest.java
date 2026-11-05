package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第四批第 5 轮：新词条「轻盈」（EVA）与「活力」（maxHP）——面板真实生效，
 * 且 GameConst 词条表的键全部被旧档迁移表覆盖（DataConsistencyTest 同口径，此处锁新键）。
 */
class AffixExpandTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState.Item itemWith(String affixName, String key, String val) {
        PlayerState.Item it = new PlayerState.Item();
        it.name = "测试甲";
        it.slot = "chest";
        it.quality = 2;
        PlayerState.AffixInst a = new PlayerState.AffixInst();
        a.name = affixName;
        a.key = key;
        a.type = "flat";
        a.val = new BigDecimal(val);
        a.rarity = "rare";
        it.affixes.add(a);
        return it;
    }

    @Test
    void vitalityAffixRaisesMaxHP() {
        PlayerState s = NewState.create();
        BigDecimal before = PanelCalculator.of(s).maxHP();
        s.equipped.put("chest", itemWith("活力", "maxHP", "200"));
        BigDecimal after = PanelCalculator.of(s).maxHP();
        assertEquals(0, Num.sub(after, before).compareTo(new BigDecimal("200")),
                "活力词条应使生命上限 +200");
    }

    @Test
    void lightnessAffixRaisesEva() {
        PlayerState s = NewState.create();
        BigDecimal before = PanelCalculator.of(s).EVA();
        s.equipped.put("chest", itemWith("轻盈", "EVA", "0.05"));
        BigDecimal after = PanelCalculator.of(s).EVA();
        assertEquals(0, Num.sub(after, before).compareTo(new BigDecimal("0.05")),
                "轻盈词条应使闪避 +5 个百分点");
    }

    @Test
    void newAffixesInTable() {
        assertTrue(GameConst.AFFIXES.stream().anyMatch(a -> "maxHP".equals(a.key())));
        assertTrue(GameConst.AFFIXES.stream().anyMatch(a -> "EVA".equals(a.key())));
        assertEquals(34, GameConst.AFFIXES.size(), "词条表应为 26 条（第八批终版口径）");
    }
}
