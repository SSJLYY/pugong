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

    @Test
    void round12Bedrock() {
        GameConst.Affix a = byName("磐石");
        assertNotNull(a, "磐石应进词条池");
        assertEquals("PDEF", a.key());
        assertEquals("epic", a.rarity());
        assertTrue(a.min() >= 20 && a.max() <= 45, "磐石区间应为 20~45：" + a.min() + "~" + a.max());
    }

    @Test
    void round13Windhunter() {
        GameConst.Affix a = byName("猎风");
        assertNotNull(a, "猎风应进词条池");
        assertEquals("SPD", a.key());
        assertEquals("epic", a.rarity());
        assertTrue(a.min() >= 30 && a.max() <= 60, "猎风区间应为 30~60：" + a.min() + "~" + a.max());
    }

    @Test
    void round14Lifespring() {
        GameConst.Affix a = byName("生命之源");
        assertNotNull(a, "生命之源应进词条池");
        assertEquals("maxHP", a.key());
        assertEquals("epic", a.rarity());
        assertTrue(a.min() >= 300 && a.max() <= 800, "生命之源区间应为 300~800：" + a.min() + "~" + a.max());
    }

    @Test
    void round15Manaspring() {
        GameConst.Affix a = byName("魔力源泉");
        assertNotNull(a, "魔力源泉应进词条池");
        assertEquals("MP", a.key());
        assertEquals("epic", a.rarity());
        assertTrue(a.min() >= 500 && a.max() <= 1200, "魔力源泉区间应为 500~1200：" + a.min() + "~" + a.max());
        assertEquals(21, GameConst.AFFIXES.size(), "词条总数应达 21（16 + 第六批 5）");
    }
}
