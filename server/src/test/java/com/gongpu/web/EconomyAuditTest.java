package com.gongpu.web;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 第七批第 76 轮：经济审计行格式锁——uid/slot/action/带符号 delta/余额后值，
 * 大数走 Num.plain 全精度（审计不能用科学计数法糊弄）。
 */
class EconomyAuditTest {

    @Test
    void lineFormatsGainAndLoss() {
        assertEquals("uid=6 slot=1 action=market-buy delta=-240 gold=9760",
                EconomyAudit.line(6, 1, "market-buy", new BigDecimal("10000"), new BigDecimal("9760")));
        assertEquals("uid=6 slot=2 action=ach-claim-all delta=+7000 gold=7000",
                EconomyAudit.line(6, 2, "ach-claim-all", BigDecimal.ZERO, new BigDecimal("7000")));
    }

    @Test
    void lineKeepsBigNumberPrecision() {
        String line = EconomyAudit.line(1, 1, "sell",
                new BigDecimal("123456789012345678901234567890"),
                new BigDecimal("123456789012345678901234567891"));
        assertEquals("uid=1 slot=1 action=sell delta=+1 gold=123456789012345678901234567891", line);
    }
}
