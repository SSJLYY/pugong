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

    /** 第九批第 65 轮：极端数值帧守卫——虚空界 + 攻速拉满的深渊号，帧结算不爆不 NaN。 */
    @Test
    void extremeValueFrameStaysBounded() {
        PlayerState s = richState();
        s.zone = "void";
        s.fixed.ASPD = new java.math.BigDecimal("100000");
        long now = System.currentTimeMillis();
        s.lastTickAt = now - 1000;
        GameEngine.advance(s, now, 1.0, false); // 热身
        s.lastTickAt = now - 1000;
        long t0 = System.nanoTime();
        GameEngine.advance(s, now, 60.0, true);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 5000, "极端数值 60 秒帧应 <5s，实际 " + ms + "ms");
        assertTrue(s.stats.totalDamage != null && s.stats.totalDamage.signum() >= 0,
                "总伤害不得为负/NaN");
    }

    /** 第十批：混沌界极端数值帧守卫——最深区域 + 镜像词条怪 + 攻速拉满，帧结算不爆不 NaN。 */
    @Test
    void chaosExtremeFrameStaysBounded() {
        PlayerState s = richState();
        s.zone = "chaos";
        s.fixed.ASPD = new java.math.BigDecimal("100000");
        s.life.maxHP = new java.math.BigDecimal("10000000000");
        s.life.curHP = new java.math.BigDecimal("10000000000");
        long now = System.currentTimeMillis();
        s.lastTickAt = now - 1000;
        GameEngine.advance(s, now, 1.0, false); // 热身
        if (s.mob != null) {
            s.mob.trait = "mirror";
        }
        s.lastTickAt = now - 1000;
        long t0 = System.nanoTime();
        GameEngine.advance(s, now, 60.0, true);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 5000, "混沌界极端数值 60 秒帧应 <5s，实际 " + ms + "ms");
        assertTrue(s.stats.totalDamage != null && s.stats.totalDamage.signum() >= 0,
                "总伤害不得为负/NaN");
    }

    @Test
    void hongmengImmortalFrameStaysBounded() {
        PlayerState s = richState();
        s.zone = "hongmeng";
        s.fixed.ASPD = new java.math.BigDecimal("100000");
        s.life.maxHP = new java.math.BigDecimal("100000000000");
        s.life.curHP = new java.math.BigDecimal("100000000000");
        long now = System.currentTimeMillis();
        s.lastTickAt = now - 1000;
        GameEngine.advance(s, now, 1.0, false); // 热身
        if (s.mob != null) {
            s.mob.trait = "immortal";
        }
        s.lastTickAt = now - 1000;
        long t0 = System.nanoTime();
        GameEngine.advance(s, now, 60.0, true);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 5000, "鸿蒙界不朽怪极端数值 60 秒帧应 <5s，实际 " + ms + "ms");
        assertTrue(s.stats.totalDamage != null && s.stats.totalDamage.signum() >= 0,
                "总伤害不得为负/NaN");
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
