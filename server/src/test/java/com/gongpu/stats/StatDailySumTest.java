package com.gongpu.stats;

import com.gongpu.account.UserRepository;
import com.gongpu.state.GameSaveRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 第 1 轮：运营统计改走冗余列 SUM——collect 口径与旧 JSON 聚合一致、空表回零。 */
class StatDailySumTest {

    private StatDailyAggregator aggregator(GameSaveRepository saves) {
        StatDailyRepository stats = mock(StatDailyRepository.class);
        when(stats.findById(LocalDate.now().minusDays(1))).thenReturn(Optional.empty());
        return new StatDailyAggregator(stats, saves, mock(UserRepository.class));
    }

    @Test
    void collectReadsSumRow() {
        GameSaveRepository saves = mock(GameSaveRepository.class);
        when(saves.sumStatsTotals()).thenReturn(new Object[]{
                new BigDecimal("12345678901234567890"), new BigDecimal("42000"), 17L, 3L});
        StatDailyAggregator.Snapshot snap = aggregator(saves).collect();
        assertEquals("12345678901234567890", snap.punches());
        assertEquals("42000", snap.kills());
        assertEquals(17L, snap.deaths());
        assertEquals(3L, snap.rebirths());
    }

    @Test
    void collectToleratesNumericTypesAndNulls() {
        GameSaveRepository saves = mock(GameSaveRepository.class);
        // 不同驱动可能把 SUM 返回成 BigInteger/Double 等 Number，或整行全 NULL（空表 COALESCE 后为 0，此处模拟 null 兜底）
        when(saves.sumStatsTotals()).thenReturn(new Object[]{null, null, null, null});
        StatDailyAggregator.Snapshot snap = aggregator(saves).collect();
        assertEquals("0", snap.punches());
        assertEquals("0", snap.kills());
        assertEquals(0L, snap.deaths());
        assertEquals(0L, snap.rebirths());
    }

    @Test
    void collectFailureReturnsZeroSnapshot() {
        GameSaveRepository saves = mock(GameSaveRepository.class);
        when(saves.sumStatsTotals()).thenThrow(new RuntimeException("db down"));
        StatDailyAggregator.Snapshot snap = aggregator(saves).collect();
        assertEquals("0", snap.punches());
        assertEquals(0L, snap.deaths());
    }
}
