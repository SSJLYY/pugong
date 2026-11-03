package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第六批第 9 轮：自动解毒补给——autoCure 在解毒剂断粮时自动买一瓶再解毒
 * （与 useAutoPotion 的自动买药同语义，修界面标注与实现不一致）。
 * autoPotion 是引擎私有方法，经 advance 驱动：中毒 + 血线安全时一帧内应完成买药解毒。
 */
class AutoCureRestockTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void cureAutoBuysWhenEmpty() {
        PlayerState s = NewState.create();
        s.zone = "village";   // 木桩无威胁，专测解毒分支
        s.autoCure = true;
        s.potions.remove("cure");
        s.gold = Num.of(10000);
        s.buffs.put("poison", 30.0);
        long t = System.currentTimeMillis();
        s.lastTickAt = t - 1000;   // advance 按时间戳差推秒数，同毫秒调用会被 seconds<=0 早退
        GameEngine.advance(s, t, 1.0, false);
        assertFalse(PanelCalculator.active(s, "poison"),
                "断粮时应自动买解毒剂并清毒，poison 仍在=" + s.buffs.keySet());
    }

    @Test
    void noGoldNoCure() {
        PlayerState s = NewState.create();
        s.zone = "village";
        s.autoCure = true;
        s.potions.remove("cure");
        s.gold = Num.ZERO;
        s.buffs.put("poison", 30.0);
        long t = System.currentTimeMillis();
        s.lastTickAt = t - 1000;
        GameEngine.advance(s, t, 1.0, false);
        assertTrue(PanelCalculator.active(s, "poison"), "金币不足时买不到药，毒应保留");
    }
}
