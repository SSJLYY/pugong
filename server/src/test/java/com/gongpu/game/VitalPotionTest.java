package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 第六批第 1 轮：生命药剂——120s 内生命上限 ×1.3（面板乘区，受沉默压制）。
 */
class VitalPotionTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void maxHpMultiplied() {
        PlayerState s = NewState.create();
        java.math.BigDecimal base = PanelCalculator.of(s).maxHP();
        s.buffs.put("vital_pot", 120.0);
        java.math.BigDecimal boosted = PanelCalculator.of(s).maxHP();
        assertEquals(0, boosted.compareTo(Num.mul(base, Num.of(1.3))),
                "生命药剂应把生命上限 ×1.3：" + base + " → " + boosted);
    }

    @Test
    void silencedSuppresses() {
        PlayerState s = NewState.create();
        java.math.BigDecimal base = PanelCalculator.of(s).maxHP();
        s.buffs.put("vital_pot", 120.0);
        s.buffs.put("silence", 10.0);
        assertEquals(0, PanelCalculator.of(s).maxHP().compareTo(base), "沉默时药剂乘区应失效");
    }
}
