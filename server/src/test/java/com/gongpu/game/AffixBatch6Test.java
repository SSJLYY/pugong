package com.gongpu.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第六批词条扩充（第 11~15 轮）：高阶词条逐轮断言——
 * 池中存在、键/区间/稀有度正确（与 gongpu-full.sql 镜像由 DataParityTest 守恒）。
 */
class AffixBatch6Test {

    private GameConst.Affix byName(String name) {
        return GameConst.AFFIXES.stream().filter(a -> a.name().equals(name)).findFirst().orElse(null);
    }

    @Test
    void round11Bloodpact() {
        GameConst.Affix a = byName("血契");
        assertNotNull(a, "血契应进词条池");
        assertEquals("LIFESTEAL", a.key());
        assertEquals("epic", a.rarity());
        assertTrue(a.min() >= 0.03 && a.max() <= 0.06, "血契区间应为 3%~6%：" + a.min() + "~" + a.max());
    }
}
