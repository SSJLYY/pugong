package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第八批第 11 轮：怪物词条「再生」——交战中每秒回上限 3%，封顶不超上限；
 * 无词条怪不回。
 */
class TraitRegenTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fighter(String trait) {
        PlayerState s = NewState.create();
        s.zone = "swamp";
        s.fixed.ASPD = BigDecimal.ZERO; // 玩家不出拳，纯看怪回复
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "toad";
        m.name = "测试怪";
        m.maxHP = new BigDecimal("10000");
        m.curHP = new BigDecimal("5000");
        m.trait = trait;
        s.mob = m;
        s.lastTickAt = System.currentTimeMillis() - 3000;
        return s;
    }

    @Test
    void regenHealsThreePercentPerSecond() {
        PlayerState s = fighter("regen");
        GameEngine.advance(s, System.currentTimeMillis(), 3.0, false);
        // 3 秒 × 3% × 10000 = 900 → 5900（怪不攻击玩家致死：PATK 默认 0）
        assertTrue(s.mob.curHP.compareTo(new BigDecimal("5900")) == 0,
                "再生 3 秒应回 900，实际 " + s.mob.curHP);
    }

    @Test
    void regenCapsAtMaxAndPlainMobDoesNotHeal() {
        PlayerState s = fighter("regen");
        s.mob.curHP = new BigDecimal("9950");
        GameEngine.advance(s, System.currentTimeMillis(), 3.0, false);
        assertEquals(0, s.mob.curHP.compareTo(new BigDecimal("10000")), "再生不应超过上限");
        PlayerState plain = fighter(null);
        GameEngine.advance(plain, System.currentTimeMillis(), 3.0, false);
        assertEquals(0, plain.mob.curHP.compareTo(new BigDecimal("5000")), "无词条怪不应回复");
    }
}
