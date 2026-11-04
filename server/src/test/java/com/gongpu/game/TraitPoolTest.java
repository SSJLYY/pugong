package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals(16, GameConst.traitPool(5).size(), "雪山/火山全池 16 条");
        assertTrue(GameConst.traitPool(5).contains("tenacity"), "深区解锁坚韧");
        assertFalse(GameConst.traitPool(4).contains("tenacity"), "沼泽池不应含坚韧");
        assertTrue(GameConst.traitPool(4).contains("regen"), "沼泽起解锁再生");
        assertFalse(GameConst.traitPool(3).contains("regen"), "矿洞池不应含再生");
        assertTrue(GameConst.traitPool(5).contains("bulwark"), "深区解锁堡垒");
        assertTrue(GameConst.traitPool(4).contains("curse"), "沼泽起解锁诅咒");
        assertFalse(GameConst.traitPool(3).contains("curse"), "矿洞池不应含诅咒");
        assertTrue(GameConst.traitPool(5).contains("frost"), "雪原起解锁寒霜");
        assertFalse(GameConst.traitPool(4).contains("frost"), "沼泽池不应含寒霜");
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
