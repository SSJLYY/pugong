package com.gongpu.state;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第八批第 68 轮：第八批新字段编解码往返——周常/金币里程碑/连环掌/宝石字段不丢。 */
class CodecBatch8Test {

    @Test
    void batch8FieldsSurviveRoundTrip() {
        PlayerState s = NewState.create();
        s.weeklyWeek = "2026-W45";
        s.weeklyBonusGiven = true;
        PlayerState.DailyTask t = new PlayerState.DailyTask();
        t.id = "hunt";
        t.target = 2000;
        t.rewardGold = 40000;
        t.baseline = "12345";
        t.claimed = true;
        s.weeklyTasks = List.of(t);
        s.goldMilestone = 9;
        s.flurryHits = 17;
        s.stats.weeklyClaims = 5;
        s.stats.gemsBought = 3;
        s.stats.gemsSocketed = 4;
        s.stats.flurryExtra = new java.math.BigDecimal("2222");
        PlayerState.Item it = new PlayerState.Item();
        it.code = "star_blade";
        it.name = "星陨之刃";
        it.slot = "weapon";
        it.gems = new java.util.ArrayList<>(List.of("gem_ruby", "gem_opal_2"));
        s.equipBag.add(it);

        PlayerState back = StateCodec.decode(StateCodec.encode(s));
        assertEquals("2026-W45", back.weeklyWeek);
        assertTrue(back.weeklyBonusGiven);
        assertEquals(1, back.weeklyTasks.size());
        assertEquals("hunt", back.weeklyTasks.get(0).id);
        assertEquals("12345", back.weeklyTasks.get(0).baseline);
        assertTrue(back.weeklyTasks.get(0).claimed);
        assertEquals(9, back.goldMilestone);
        assertEquals(17, back.flurryHits);
        assertEquals(5, back.stats.weeklyClaims);
        assertEquals(3, back.stats.gemsBought);
        assertEquals(4, back.stats.gemsSocketed);
        assertEquals(0, back.stats.flurryExtra.compareTo(new java.math.BigDecimal("2222")));
        assertEquals(List.of("gem_ruby", "gem_opal_2"), back.equipBag.get(0).gems, "装备宝石列表应往返不丢");
    }
}
