package com.gongpu.state;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第十批字段编解码往返 + 星级钳制。 */
class CodecBatch10Test {

    @Test
    void starAndCountersSurviveRoundTrip() {
        PlayerState s = NewState.create();
        PlayerState.Item it = new PlayerState.Item();
        it.name = "星剑";
        it.slot = "weapon";
        it.star = 3;
        it.base.put("PATK", BigDecimal.valueOf(100));
        s.equipBag.add(it);
        s.stats.starsDone = 9;
        s.stats.petTreats = 4;
        s.stats.mirrorKills = 6;
        s.stats.weakenKills = 5;
        s.stats.frenzyKills = 2;
        PlayerState back = StateCodec.decode(StateCodec.encode(s));
        assertEquals(3, back.equipBag.get(0).star);
        assertEquals(9L, back.stats.starsDone);
        assertEquals(4L, back.stats.petTreats);
        assertEquals(6L, back.stats.mirrorKills);
        assertEquals(5L, back.stats.weakenKills);
        assertEquals(2L, back.stats.frenzyKills);
    }

    @Test
    void starClampedOnDecode() {
        PlayerState s = NewState.create();
        PlayerState.Item it = new PlayerState.Item();
        it.name = "坏档剑";
        it.slot = "weapon";
        it.star = 99;
        s.equipBag.add(it);
        PlayerState back = StateCodec.decode(StateCodec.encode(s));
        assertEquals(5, back.equipBag.get(0).star, "星级越界应钳到满星");
        it.star = -3;
        back = StateCodec.decode(StateCodec.encode(s));
        assertEquals(0, back.equipBag.get(0).star, "负星级应钳到 0");
    }
}
