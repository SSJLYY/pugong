package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第六批第 72 轮：词条池解锁线回归——各深度池成员与概率掷骰的分布 sanity。 */
class TraitPoolTest {

    @Test
    void poolsUnlockByDepth() {
        assertEquals(Set.of("frenzy"), new HashSet<>(GameConst.traitPool(1)), "平原仅狂暴");
        assertEquals(Set.of("frenzy", "sunder", "rich", "crystal"),
                new HashSet<>(GameConst.traitPool(2)), "森林池");
        assertTrue(GameConst.traitPool(3).contains("venom"), "矿洞起解锁剧毒");
        assertTrue(GameConst.traitPool(4).contains("stealth"), "沼泽起解锁潜行");
        assertTrue(GameConst.traitPool(4).contains("leech"), "沼泽起解锁嗜血");
        assertEquals(12, GameConst.traitPool(5).size(), "雪山/火山全池 12 条");
        assertTrue(GameConst.traitPool(5).contains("bulwark"), "深区解锁堡垒");
        assertTrue(GameConst.traitPool(4).contains("slow"), "沼泽起解锁时滞");
        assertTrue(GameConst.traitPool(5).contains("thorns"), "深区解锁荆棘");
        // 池内每条都必须有词条定义（防池里写了不存在的 id）
        for (int i = 1; i <= 6; i++) {
            for (String id : GameConst.traitPool(i)) {
                assertTrue(GameConst.MOB_TRAITS.containsKey(id), "池成员应有定义：" + id);
            }
        }
    }

    @Test
    void villageNeverRolls() {
        for (int i = 0; i < 200; i++) {
            assertNull(GameConst.rollMobTrait("village"), "村口永不出词条");
        }
    }

    @Test
    void plainRollsOnlyFrenzy() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 4000; i++) {
            String t = GameConst.rollMobTrait("plain");
            if (t != null) {
                seen.add(t);
            }
        }
        assertEquals(Set.of("frenzy"), seen, "平原掷骰只应出狂暴（概率约 10%）");
    }
}
