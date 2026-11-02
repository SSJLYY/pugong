package com.gongpu.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 大数运算核心 Num 的单元测试。
 * 游戏后期数值 1e44+,任何精度/格式回归都是丢档级事故,必须锁死。
 */
class NumTest {

    @Test
    void ofLongAndDouble() {
        assertEquals(new BigDecimal("5"), Num.of(5L));
        assertEquals(new BigDecimal("0.5"), Num.of(0.5));
        assertThrows(IllegalArgumentException.class, () -> Num.of(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Num.of(Double.POSITIVE_INFINITY));
    }

    @Test
    void parseFallback() {
        assertEquals(Num.of(7), Num.parse("7", Num.ZERO));
        assertEquals(Num.ZERO, Num.parse("", Num.ZERO));
        assertEquals(Num.ZERO, Num.parse(null, Num.ZERO));
        assertEquals(Num.ZERO, Num.parse("abc", Num.ZERO));  // 坏档容错
        assertEquals(new BigDecimal("1.5"), Num.parse(" 1.5 ", Num.ZERO));
    }

    @Test
    void arithmetic() {
        assertEquals(new BigDecimal("3"), Num.add(Num.of(1), Num.of(2)));
        assertEquals(new BigDecimal("-1"), Num.sub(Num.of(1), Num.of(2)));
        assertEquals(new BigDecimal("6"), Num.mul(Num.of(2), Num.of(3)));
        assertEquals(new BigDecimal("2"), Num.div(Num.of(8), Num.of(4)));
        assertEquals(Num.ZERO, Num.div(Num.of(8), Num.ZERO));  // 除零不抛
    }

    @Test
    void bigNumberPrecision1e44() {
        // 28 位有效数字的设计口径:1e44 相对量级 1e16 以内的增量会被吸收
        BigDecimal a = new BigDecimal("1" + "0".repeat(44));
        BigDecimal absorbed = Num.add(a, Num.of(1000));
        assertEquals(0, absorbed.compareTo(a), "1e44+1000 在 28 位精度下应被吸收(设计行为)");
        // 相对量级 1e14 的增量(1e30)必须可分辨
        BigDecimal b = Num.add(a, new BigDecimal("1e30"));
        assertTrue(Num.gt(b, a), "1e44 + 1e30 应严格大于 1e44");
        assertEquals(0, Num.sub(b, a).compareTo(new BigDecimal("1e30")), "相减应还原增量");
    }

    @Test
    void compareHelpers() {
        assertTrue(Num.gt(Num.of(2), Num.of(1)));
        assertTrue(Num.gte(Num.of(1), Num.of(1)));
        assertTrue(Num.lt(Num.of(1), Num.of(2)));
        assertTrue(Num.lte(Num.of(2), Num.of(2)));
    }

    @Test
    void floorAndNonNegative() {
        assertEquals(new BigDecimal("3"), Num.floor(new BigDecimal("3.99")));
        assertEquals(new BigDecimal("-3"), Num.floor(new BigDecimal("-2.01")));  // 向下取整
        assertEquals(Num.ZERO, Num.nonNegative(new BigDecimal("-5")));
        assertEquals(Num.of(7), Num.nonNegative(Num.of(7)));
    }

    @Test
    void plainNoScientificNotation() {
        // 1e44 必须输出 45 位明文,不能是 "1E+44"(前端 Decimal 与 JSON 约定)
        String s = Num.plain(new BigDecimal("1e44"));
        assertEquals(45, s.length());
        assertFalse(s.contains("E"), "plain() 不得输出科学计数法");
        assertEquals("0", Num.plain(null));
    }

    @Test
    void cnUnits() {
        assertEquals("9999", Num.cn(Num.of(9999)));
        assertEquals("1万", Num.cn(Num.of(10000)));
        assertEquals("1.5万", Num.cn(Num.of(15000)));
        assertEquals("1亿", Num.cn(new BigDecimal("100000000")));
    }

    @Test
    void cnBeyondLastUnitFallsBackToSci() {
        // 超出单位表(1e48 以上)不能越界,回退科学计数
        String s = Num.cn(new BigDecimal("1e60"));
        assertNotNull(s);
        assertFalse(s.isEmpty());
    }
}
