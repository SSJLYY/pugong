package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第七批第 77 轮：宽松性能守卫——只拦数量级回归（如面板计算被改成指数级、长帧结算失控），
 * 阈值留足 10 倍余量，慢 CI 也不误报。热身后计段，避免 JIT 冷启动污染。
 */
class PerfGuardTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private static PlayerState richState() {
        PlayerState s = NewState.create();
        s.zone = "volcano";
        for (String slot : new String[]{"weapon", "helmet", "armor", "pants", "boots", "shield"}) {
            PlayerState.Item it = new PlayerState.Item();
            it.name = "守卫测试装";
            it.slot = slot;
            it.quality = 3;
            it.enhance = 10;
            it.temper = 5;
            it.base.put("PATK", com.gongpu.common.Num.of(500));
            PlayerState.AffixInst a1 = new PlayerState.AffixInst();
            a1.name = "锋利";
            a1.key = "PATK";
            a1.type = "flat";
            a1.val = com.gongpu.common.Num.of(50);
            a1.rarity = "rare";
            it.affixes.add(a1);
            PlayerState.AffixInst a2 = new PlayerState.AffixInst();
            a2.name = "鹰眼";
            a2.key = "CRIT";
            a2.type = "pct";
            a2.val = new java.math.BigDecimal("0.05");
            a2.rarity = "epic";
            it.affixes.add(a2);
            s.equipped.put(slot, it);
        }
        return s;
    }

    @Test
    void panelCalcStaysCheap() {
        PlayerState s = richState();
        for (int i = 0; i < 200; i++) {
            PanelCalculator.of(s); // 热身
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < 5000; i++) {
            PanelCalculator.of(s);
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 5000, "5000 次面板计算应 <5s，实际 " + ms + "ms（疑似数量级回归）");
    }

    @Test
    void longOfflineFrameStaysBounded() {
        PlayerState s = richState();
        long now = System.currentTimeMillis();
        s.lastTickAt = now - 1000;
        GameEngine.advance(s, now, 1.0, false); // 热身一帧
        s.lastTickAt = now - 1000;
        long t0 = System.nanoTime();
        GameEngine.advance(s, now, 3600.0, true); // 1 小时离线帧
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 5000, "1 小时离线结算应 <5s，实际 " + ms + "ms");
    }
}
