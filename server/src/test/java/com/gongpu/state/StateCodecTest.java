package com.gongpu.state;


import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 存档序列化测试:大数走字符串、往返无损、坏档/旧档 normalize 兜底。
 */
class StateCodecTest {

    @Test
    void roundtripPreservesBigNumbers() {
        PlayerState s = NewState.create();
        s.life.maxHP = new BigDecimal("1" + "0".repeat(44));   // 1e44
        s.gold = new BigDecimal("12345678901234567890123456789");
        s.stats.punches = new BigDecimal("9876543210.5");

        String json = StateCodec.encode(s);
        assertTrue(json.contains("1" + "0".repeat(44)), "maxHP 必须明文序列化");
        assertFalse(json.matches(".*[0-9]E[+-][0-9].*"), "不得出现科学计数法");

        PlayerState back = StateCodec.decode(json);
        assertEquals(0, s.life.maxHP.compareTo(back.life.maxHP));
        assertEquals(0, s.gold.compareTo(back.gold));
        assertEquals(0, s.stats.punches.compareTo(back.stats.punches));
    }

    @Test
    void decodeFillsMissingFields() {
        // 极简旧档:所有结构缺省,decode 不得抛异常且字段齐备
        String legacy = "{}";
        PlayerState s = StateCodec.decode(legacy);
        assertNotNull(s.life, "life 缺失应回落 fresh()");
        assertNotNull(s.fixed);
        assertNotNull(s.blessings);
        assertTrue(s.blessings.containsKey("HP"), "空 blessings 应补 HP 赋能");
        assertNotNull(s.equipBag);
        assertNotNull(s.mastery);
        assertNotNull(s.potions);
        assertNotNull(s.buffs);
        assertNotNull(s.cds);
        assertEquals("village", s.zone, "非法/缺失区域应回落村口");
        assertTrue(s.lastTickAt > 0);
    }

    @Test
    void decodeUnknownFieldsIgnored() {
        // 前向兼容:新版本字段在旧服务端解码时应被忽略而不是报错
        String future = "{\"someFutureField\":123,\"zone\":\"village\"}";
        PlayerState s = StateCodec.decode(future);
        assertEquals("village", s.zone);
    }

    @Test
    void decodeInvalidZoneFallsBack() {
        PlayerState s = StateCodec.decode("{\"zone\":\"no_such_zone\"}");
        assertEquals("village", s.zone);
    }

    @Test
    void aliveSecondsNonNegative() {
        PlayerState s = NewState.create();
        s.stats.born = System.currentTimeMillis() + 60_000;   // 未来出生钟(时钟漂移容错)
        assertTrue(StateCodec.aliveSeconds(s) >= 0, "出生钟在未来时存活秒数应为 0 而非负数");
    }
}
