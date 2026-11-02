package com.gongpu.game;

import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 结算引擎核心:普攻成长(每拳永久+生命上限)——本项目的立身之本。
 */
class GameEngineTest {

    @Test
    void growIncreasesMaxHpPermanently() {
        PlayerState s = NewState.create();
        BigDecimal before = s.life.maxHP;
        // 期望增量 = 拳数 × 命中率(ACC) × 村口加成2 × HP步长(1)
        BigDecimal acc = PanelCalculator.of(s).ACC();
        BigDecimal expectedGain = Num.mul(Num.mul(Num.of(10), acc), GameConst.VILLAGE_GROWTH_MUL);
        GameEngine.grow(s, Num.of(10));
        assertTrue(Num.gt(s.life.maxHP, before), "普攻后生命上限必须永久增长");
        assertEquals(0, s.life.maxHP.compareTo(Num.add(before, expectedGain)),
                "村口 10 拳 HP 增量应 = 10×ACC×2,实际 " + Num.plain(s.life.maxHP));
        // curHP 同步增长(上限涨,当前血跟着涨)
        assertEquals(0, s.life.curHP.compareTo(s.life.maxHP), "满血状态成长后 curHP 应等于 maxHP");
    }

    @Test
    void growZeroPunchesNoChange() {
        PlayerState s = NewState.create();
        BigDecimal before = s.life.maxHP;
        GameEngine.grow(s, Num.ZERO);
        assertEquals(0, s.life.maxHP.compareTo(before), "0 拳不应成长");
    }

    @Test
    void bigPunchCountNoOverflow() {
        PlayerState s = NewState.create();
        // 1e44 拳:村口步长 1 ×2 → maxHP ≈ 2e44,全程 BigDecimal 不得溢出/丢量级
        GameEngine.grow(s, new BigDecimal("1e44"));
        assertTrue(Num.gt(s.life.maxHP, new BigDecimal("1e40")), "大数成长必须保住量级");
    }

    @Test
    void spawnMobPopulatesMob() {
        PlayerState s = NewState.create();
        s.mob = null;
        // 第三参是 keepProgress(不是 isBoss)
        GameEngine.spawnMob(s, "plain");
        assertNotNull(s.mob, "平原必须刷出怪物");
        assertNotNull(s.mob.id);
        assertFalse(s.mob.boss, "普通刷怪不应刷出 Boss");
    }

    @Test
    void spawnBossIsBoss() {
        PlayerState s = NewState.create();
        s.zone = "plain";
        GameEngine.spawnBoss(s);
        assertNotNull(s.mob);
        assertTrue(s.mob.boss, "spawnBoss 必须刷出 Boss");
    }
}
