package com.gongpu.stats;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

/** 第 9 轮：榜单读取 5 秒短缓存——窗口内重复读取不直打 Redis，行内容口径不变。 */
class LeaderboardCacheTest {

    @Test
    @SuppressWarnings("unchecked")
    void topIsCachedWithinWindow() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ZSetOperations<String, String> zset = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zset);
        ZSetOperations.TypedTuple<String> row = mock(ZSetOperations.TypedTuple.class);
        when(row.getValue()).thenReturn("7|测试玩家|3|123456");
        when(row.getScore()).thenReturn(5.08);
        when(zset.reverseRangeWithScores(anyString(), eq(0L), eq(49L))).thenReturn(Set.of(row));

        LeaderboardService svc = new LeaderboardService(redis, "gongpu:board:maxhp");
        List<Map<String, Object>> first = svc.top(LeaderboardService.BOARD_HP, 50);
        List<Map<String, Object>> second = svc.top(LeaderboardService.BOARD_HP, 50);

        assertSame(first, second, "5 秒窗口内应复用缓存结果");
        assertEquals(1, first.size());
        assertEquals("测试玩家", first.get(0).get("username"));
        assertEquals("123456", first.get(0).get("maxHP"));
        verify(zset, times(1)).reverseRangeWithScores(anyString(), eq(0L), eq(49L));
    }

    /** 第七批第 55 轮：伤害榜读取——走 damage 键，行含 damage/damageText 与世代段。 */
    @Test
    @SuppressWarnings("unchecked")
    void damageBoardParsesRows() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ZSetOperations<String, String> zset = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zset);
        ZSetOperations.TypedTuple<String> row = mock(ZSetOperations.TypedTuple.class);
        when(row.getValue()).thenReturn("9|伤害王|2|987654321");
        when(row.getScore()).thenReturn(8.99);
        when(zset.reverseRangeWithScores(eq("gongpu:board:damage"), eq(0L), eq(49L)))
                .thenReturn(Set.of(row));

        LeaderboardService svc = new LeaderboardService(redis, "gongpu:board:maxhp");
        List<Map<String, Object>> rows = svc.top(LeaderboardService.BOARD_DAMAGE, 50);

        assertEquals(1, rows.size());
        assertEquals("伤害王", rows.get(0).get("username"));
        assertEquals("987654321", rows.get(0).get("damage"));
        assertEquals("2", rows.get(0).get("gen"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void differentBoardsCacheSeparately() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ZSetOperations<String, String> zset = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zset);
        when(zset.reverseRangeWithScores(anyString(), anyLong(), anyLong())).thenReturn(Set.of());

        LeaderboardService svc = new LeaderboardService(redis, "gongpu:board:maxhp");
        svc.top(LeaderboardService.BOARD_HP, 50);
        svc.top(LeaderboardService.BOARD_GOLD, 50);
        verify(zset, times(2)).reverseRangeWithScores(anyString(), anyLong(), anyLong());
    }
}
