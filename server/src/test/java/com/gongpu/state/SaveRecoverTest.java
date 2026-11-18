package com.gongpu.state;

import com.gongpu.common.ApiException;
import com.gongpu.config.StateCache;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 第六批第 65 轮：存档解码自愈——主档损坏回退快照，双坏才报 500 SAVE_CORRUPT。 */
class SaveRecoverTest {

    private static GameSave row(String main, String snap) {
        GameSave gs = new GameSave();
        gs.setStateJson(main);
        gs.setSnapshotJson(snap);
        return gs;
    }

    @Test
    void fallsBackToSnapshotWhenMainCorrupt() {
        PlayerState good = NewState.create();
        good.gen = 5;
        PlayerState s = SaveService.decodeOrRecover(row("{bad json", StateCodec.encode(good)));
        assertEquals(5, s.gen, "主档损坏应回退到快照状态");
    }

    @Test
    void mainOkIgnoresSnapshot() {
        PlayerState main = NewState.create();
        main.gen = 2;
        PlayerState s = SaveService.decodeOrRecover(row(StateCodec.encode(main), "{bad"));
        assertEquals(2, s.gen);
    }

    @Test
    void coldLoadDoesNotOverwriteCachePublishedByLockedPersist() {
        GameSaveRepository repo = mock(GameSaveRepository.class);
        StateCache cache = mock(StateCache.class);
        SaveService service = new SaveService(repo, cache);
        PlayerState oldState = NewState.create();
        oldState.gen = 2;
        String oldJson = StateCodec.encode(oldState);
        PlayerState newState = NewState.create();
        newState.gen = 9;
        AtomicReference<String> cachedJson = new AtomicReference<>();

        when(cache.get(1L, 1)).thenAnswer(invocation -> {
            String json = cachedJson.get();
            return json == null ? null : StateCodec.decode(json);
        });
        doAnswer(invocation -> {
            cachedJson.set(invocation.getArgument(2, String.class));
            return null;
        }).when(cache).putJson(eq(1L), eq(1), anyString());
        doAnswer(invocation -> {
            cachedJson.set(StateCodec.encode(invocation.getArgument(2, PlayerState.class)));
            return null;
        }).when(cache).put(eq(1L), eq(1), any(PlayerState.class));
        when(cache.lock(1L, 1, 10)).thenReturn(true);

        GameSave staleRow = mock(GameSave.class);
        GameSave currentRow = row(oldJson, null);
        when(repo.findByUserIdAndSlotNo(1L, 1))
                .thenReturn(Optional.of(staleRow), Optional.of(currentRow));
        // 冷读已取得旧 DB 行，在解码返回前确定性交错一次持锁写入，无需真实线程或外部服务。
        when(staleRow.getStateJson()).thenAnswer(invocation -> {
            assertTrue(cache.lock(1L, 1, 10));
            try {
                service.persist(1L, 1, newState, null);
            } finally {
                cache.unlock(1L, 1);
            }
            assertEquals(9, StateCodec.decode(cachedJson.get()).gen,
                    "并发写路径已发布新状态，冷读随后才完成");
            return oldJson;
        });

        PlayerState coldRead = service.load(1L, 1);

        assertEquals(2, coldRead.gen, "当前冷读可以返回自己读取的旧 DB 快照");
        assertEquals(9, service.load(1L, 1).gen, "后续读取必须仍命中新缓存，不能被旧冷读覆盖");
        verify(repo, times(2)).findByUserIdAndSlotNo(1L, 1);
        verify(repo).save(currentRow);
        verify(cache).putJson(eq(1L), eq(1), anyString());
        verify(cache, never()).put(eq(1L), eq(1), any(PlayerState.class));
        verify(cache).unlock(1L, 1);
    }

    @Test
    void bothCorruptThrowsClearError() {
        ApiException e = assertThrows(ApiException.class,
                () -> SaveService.decodeOrRecover(row("{bad", "{also bad")));
        assertEquals(500, e.getStatus());
        assertEquals("SAVE_CORRUPT", e.getCode());
    }
}
