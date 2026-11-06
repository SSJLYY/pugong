package com.gongpu.state;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第十四批修复（2026-11-06）：冗余统计列写入钳制。
 * 事故回放：玩家终身击杀指数增长冲破 DECIMAL(65,0) 上限（10^65-1），
 * game_save 整批 update 报 Out of range → 登录/存档 500。
 * 本测试锁死 capStatsColumn/capVarchar 的边界行为，防止回归。
 */
class SaveColumnCapTest {

    @Test
    void normalValuesPassThrough() {
        assertEquals(0, new BigDecimal("12345").compareTo(SaveService.capStatsColumn(new BigDecimal("12345"))));
        assertEquals(0, new BigDecimal("106318243354453158731292293500000000000000000000000000000000")
                .compareTo(SaveService.capStatsColumn(new BigDecimal("106318243354453158731292293500000000000000000000000000000000"))));
    }

    @Test
    void nullNormalizesToZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(SaveService.capStatsColumn(null)));
    }

    @Test
    void beyondDecimal65SaturatesAt1e63() {
        BigDecimal cap = BigDecimal.ONE.scaleByPowerOfTen(63);
        // 10^70：超出 DECIMAL(65,0) 可存范围，必须封顶而不是抛异常
        BigDecimal huge = new BigDecimal("1").scaleByPowerOfTen(70);
        assertEquals(0, cap.compareTo(SaveService.capStatsColumn(huge)));
        // 负向同理
        assertEquals(0, cap.negate().compareTo(SaveService.capStatsColumn(huge.negate())));
        // 封顶后仍在 DECIMAL(65,0) 范围内（65 位以内）
        assertTrue(SaveService.capStatsColumn(huge).precision() <= 65);
    }

    @Test
    void scientificAndFractionalInputsNormalize() {
        // E 记法与带小数的输入都应规整为整数刻度
        BigDecimal out = SaveService.capStatsColumn(new BigDecimal("9.9E+40"));
        assertEquals(0, out.scale());
        assertEquals(0, new BigDecimal("99").scaleByPowerOfTen(39).compareTo(out));
        BigDecimal frac = SaveService.capStatsColumn(new BigDecimal("123.9"));
        assertEquals(0, new BigDecimal("123").compareTo(frac));
    }

    @Test
    void maxHpVarcharTruncatedToColumnWidth() {
        assertEquals("100", SaveService.capVarchar("100", 80));
        // 列可空；Num.plain(null) 已返回 "0"，这里 null 直通
        assertNull(SaveService.capVarchar(null, 80));
        // 1E+80 的 plain 串是 81 字符，超过 varchar(80) 必须截断
        String wide = BigDecimal.ONE.scaleByPowerOfTen(80).toPlainString();
        assertEquals(80, SaveService.capVarchar(wide, 80).length());
    }
}
