package com.gongpu.web;

import com.gongpu.config.StateCache;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

/** 第 6 轮：/health 探活 2 秒短缓存——高频轮询不重复打 Redis/DB，响应形状不变。 */
class HealthCacheTest {

    @Test
    void healthProbesOnceWithinWindow() throws Exception {
        StateCache cache = mock(StateCache.class);
        when(cache.ping()).thenReturn(true);
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.isValid(2)).thenReturn(true);

        MetaController c = new MetaController(cache, ds);
        Map<String, Object> first = c.health();
        Map<String, Object> second = c.health();

        assertSame(first, second, "窗口内应直接复用缓存结果");
        assertEquals(Boolean.TRUE, first.get("ok"));
        assertEquals("up", first.get("redis"));
        assertEquals("up", first.get("mysql"));
        verify(ds, times(1)).getConnection();
        verify(cache, times(1)).ping();
        verify(conn).close();
    }

    @Test
    void healthReportsDownWithoutThrowing() throws Exception {
        StateCache cache = mock(StateCache.class);
        when(cache.ping()).thenReturn(false);
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new RuntimeException("db unreachable"));

        MetaController c = new MetaController(cache, ds);
        Map<String, Object> m = c.health();
        assertEquals("down", m.get("redis"));
        assertEquals("down", m.get("mysql"));
    }
}
