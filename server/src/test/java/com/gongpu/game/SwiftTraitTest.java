package com.gongpu.game;

import com.gongpu.common.Num;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第五批第 4 轮：怪物词条「⚡疾影」——攻速常驻 ×1.5（矿洞 idx≥3 入池）；
 * 狂暴残血加速并入 traitHasteMul 后行为不变，一并锁定。
 */
class SwiftTraitTest {

    private PlayerState.MobState mob(String trait, long cur, long max) {
        PlayerState.MobState m = new PlayerState.MobState();
        m.trait = trait;
        m.curHP = Num.of(cur);
        m.maxHP = Num.of(max);
        return m;
    }

    @Test
    void hasteMulMatrix() {
        assertEquals(1.5, GameEngine.traitHasteMul(mob("swift", 100, 100)), 1e-9, "疾影满血也加速");
        assertEquals(1.5, GameEngine.traitHasteMul(mob("frenzy", 20, 100)), 1e-9, "狂暴残血加速");
        assertEquals(1.0, GameEngine.traitHasteMul(mob("frenzy", 80, 100)), 1e-9, "狂暴高血不加速");
        assertEquals(1.0, GameEngine.traitHasteMul(mob("rich", 20, 100)), 1e-9, "富饶不影响攻速");
        assertEquals(1.0, GameEngine.traitHasteMul(mob(null, 20, 100)), 1e-9, "无词条 ×1");
    }

    @Test
    void poolMembershipByZone() {
        Set<String> forest = new HashSet<>();
        for (int i = 0; i < 6000; i++) {
            String t = GameConst.rollMobTrait("forest");
            if (t != null) {
                forest.add(t);
            }
        }
        assertFalse(forest.contains("swift"), "森林池不应有疾影");
        Set<String> mine = new HashSet<>();
        for (int i = 0; i < 8000; i++) {
            String t = GameConst.rollMobTrait("mine");
            if (t != null) {
                mine.add(t);
            }
        }
        assertTrue(mine.contains("swift"), "矿洞池应能掷出疾影");
        assertTrue(GameConst.MOB_TRAITS.containsKey("swift"), "词条定义应注册");
    }
}
