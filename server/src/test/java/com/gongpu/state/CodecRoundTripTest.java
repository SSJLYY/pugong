package com.gongpu.state;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第六批第 76 轮：存档编解码往返——第六批新字段（事件日志/自动推进/Boss 连战/管护签名）不丢。 */
class CodecRoundTripTest {

    @Test
    void batch6FieldsSurviveRoundTrip() {
        PlayerState s = NewState.create();
        s.events.add("👑 击败 Boss「测试」");
        s.events.add("📐 掉落图纸（见锻造页）");
        s.autoAdvance = true;
        s.autoBoss = true;
        s.lastMaintainSig = 123456789L;
        s.achClaimed.add("kill_100");
        s.materials.put("pelt", 42);
        s.stats.dismantles = 7;
        s.stats.crafts = 3;
        s.stats.bossKillCount = 2;
        s.stats.totalDamage = new BigDecimal("987654");
        s.stats.bestHit = new BigDecimal("12345");

        PlayerState back = StateCodec.decode(StateCodec.encode(s));
        assertEquals(s.events, back.events, "战斗日志应往返不丢");
        assertTrue(back.autoAdvance && back.autoBoss, "自动推进/连战开关应保留");
        assertEquals(123456789L, back.lastMaintainSig, "管护签名应保留");
        assertTrue(back.achClaimed.contains("kill_100"), "成就已领集合应保留");
        assertEquals(42, back.materials.get("pelt"));
        assertEquals(7, back.stats.dismantles);
        assertEquals(3, back.stats.crafts);
        assertEquals(2, back.stats.bossKillCount);
        assertEquals(0, new BigDecimal("987654").compareTo(back.stats.totalDamage), "终身总伤害应保留");
        assertEquals(0, new BigDecimal("12345").compareTo(back.stats.bestHit), "最高单击应保留");
    }

    @Test
    void legacyJsonWithoutNewFieldsDecodes() {
        // 旧档 JSON（无 events/autoAdvance 等新字段）应以默认值解码，不抛错
        PlayerState s = StateCodec.decode("{\"gen\":3,\"zone\":\"forest\"}");
        assertEquals(3, s.gen);
        assertTrue(s.events != null && s.events.isEmpty(), "旧档 events 应补空表");
        assertTrue(!s.autoAdvance && !s.autoBoss, "旧档新开关应默认关");
    }
}
