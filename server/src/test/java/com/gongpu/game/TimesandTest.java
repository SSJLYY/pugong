package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 第七批第 82 轮：时之砂——平直攻速 +0.2（进乘区前加算，与疾风乘区叠乘），沉默压制。
 */
class TimesandTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void sandAddsFlatAspd() {
        PlayerState s = NewState.create();
        s.fixed.ASPD = Num.of(1);
        assertEquals(0, PanelCalculator.of(s).ASPD().compareTo(Num.of(1)));
        s.buffs.put("sand_pot", 120.0);
        assertEquals(0, PanelCalculator.of(s).ASPD().compareTo(Num.of(1.2)),
                "时之砂应平直 +0.2 攻速");
        // 与疾风药剂（×1.5 乘区）叠乘：(1 + 0.2) × 1.5 = 1.8
        s.buffs.put("aspd_pot", 60.0);
        assertEquals(0, PanelCalculator.of(s).ASPD().compareTo(Num.of(1.8)));
    }

    @Test
    void sandSuppressedBySilence() {
        PlayerState s = NewState.create();
        s.fixed.ASPD = Num.of(1);
        s.buffs.put("sand_pot", 120.0);
        s.buffs.put("silence", 5.0);
        assertEquals(0, PanelCalculator.of(s).ASPD().compareTo(Num.of(1)));
    }
}
