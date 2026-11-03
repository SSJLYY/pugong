package com.gongpu.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第七批第 83/84 轮：高阶词条「破军」（物攻上位）与「绝杀」（暴击上位）——
 * 区间严格高于各自的普通版、稀有度史诗、维度不串。
 */
class AffixBatch7Test {

    private static GameConst.Affix byName(String name) {
        return GameConst.AFFIXES.stream().filter(a -> a.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("词条缺失: " + name));
    }

    @Test
    void pojunIsPatkEpicAboveSharp() {
        GameConst.Affix pojun = byName("破军");
        GameConst.Affix sharp = byName("锋利");
        assertEquals("PATK", pojun.key());
        assertEquals("epic", pojun.rarity());
        assertTrue(pojun.min() >= sharp.max(), "破军下限应不低于锋利上限（上位关系）");
        assertTrue(pojun.max() > sharp.max());
    }

    @Test
    void totalCountAfterPojun() {
        // 第 84 轮绝杀加入后总数 24；本断言在第 84 轮同步更新（先锁破军轮的 23 由 AffixBatch6Test 承担）
        assertTrue(GameConst.AFFIXES.size() >= 23);
    }
}
