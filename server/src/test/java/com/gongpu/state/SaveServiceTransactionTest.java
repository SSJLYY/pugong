package com.gongpu.state;

import com.gongpu.config.StateCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SaveServiceTransactionTest {
    private GameSaveRepository repo;
    private StateCache cache;
    private GameSave save;
    private SaveService service;
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        repo = mock(GameSaveRepository.class);
        cache = mock(StateCache.class);
        save = new GameSave();
        save.setUserId(1L);
        save.setSlotNo(1);
        when(repo.findByUserIdAndSlotNo(1L, 1)).thenReturn(Optional.of(save));
        service = new SaveService(repo, cache);
        transaction = new TransactionTemplate(new TestTransactionManager());
    }

    @Test
    void persistPublishesExactlyOnceAfterCommitWithImmutableJson() {
        PlayerState state = NewState.create();
        String[] savedJson = new String[1];
        transaction.executeWithoutResult(status -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            service.persist(1L, 1, state, null);
            savedJson[0] = save.getStateJson();
            state.gold = BigDecimal.TEN;
            verifyNoInteractions(cache);
        });
        verify(cache, times(1)).putJson(1L, 1, savedJson[0]);
        verifyNoMoreInteractions(cache);
    }

    @Test
    void laterFailureRollsBackWithoutPublishing() {
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            service.persist(1L, 1, NewState.create(), null);
            verifyNoInteractions(cache);
            throw new IllegalStateException("模拟外层业务后续写入失败");
        }));
        verify(repo).save(save);
        verifyNoInteractions(cache);
    }

    @Test
    void nonTransactionalPersistPublishesImmediately() {
        service.persist(1L, 1, NewState.create(), null);
        verify(cache).putJson(1L, 1, save.getStateJson());
    }

    @Test
    void synchronizationWithoutRealTransactionPublishesImmediately() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.persist(1L, 1, NewState.create(), null);
            verify(cache).putJson(1L, 1, save.getStateJson());
            assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void snapshotOnlyPublishesAfterCommit() {
        transaction.executeWithoutResult(status -> {
            service.createSnapshot(1L, 1, NewState.create());
            verifyNoInteractions(cache);
        });
        verify(cache).putJson(1L, 1, save.getSnapshotJson());
        assertEquals(save.getSnapshotJson(), save.getStateJson());
    }

    @Test
    void snapshotRollbackDoesNotPublish() {
        transaction.executeWithoutResult(status -> {
            service.createSnapshot(1L, 1, NewState.create());
            status.setRollbackOnly();
        });
        verifyNoInteractions(cache);
    }

    @Test
    void restoredSnapshotOnlyPublishesAfterCommit() {
        PlayerState snapshot = NewState.create();
        snapshot.gold = BigDecimal.TEN;
        save.setSnapshotJson(StateCodec.encode(snapshot));
        transaction.executeWithoutResult(status -> {
            PlayerState restored = service.rollback(1L, 1);
            assertFalse(restored.dead);
            assertEquals(0, BigDecimal.TEN.compareTo(restored.gold));
            verifyNoInteractions(cache);
            restored.gold = BigDecimal.ZERO;
        });
        verify(cache).putJson(1L, 1, save.getStateJson());
        assertEquals(0, BigDecimal.TEN.compareTo(StateCodec.decode(save.getStateJson()).gold));
    }

    @Test
    void restoredSnapshotRollbackDoesNotPublish() {
        save.setSnapshotJson(StateCodec.encode(NewState.create()));
        transaction.executeWithoutResult(status -> {
            service.rollback(1L, 1);
            status.setRollbackOnly();
        });
        verifyNoInteractions(cache);
    }

    @Test
    void multipleWritesPublishInOrderAndLeaveFinalState() {
        String[] json = new String[2];
        transaction.executeWithoutResult(status -> {
            PlayerState state = NewState.create();
            service.persist(1L, 1, state, null);
            json[0] = save.getStateJson();
            state.gold = BigDecimal.TEN;
            service.persist(1L, 1, state, null);
            json[1] = save.getStateJson();
            state.gold = BigDecimal.ZERO;
            verifyNoInteractions(cache);
        });
        var order = inOrder(cache);
        order.verify(cache).putJson(1L, 1, json[0]);
        order.verify(cache).putJson(1L, 1, json[1]);
        order.verifyNoMoreInteractions();
        assertNotEquals(json[0], json[1]);
    }

    @Test
    void deletionEvictsOnlyOnCommit() {
        transaction.executeWithoutResult(status -> {
            service.delete(1L, 1);
            verifyNoInteractions(cache);
            status.setRollbackOnly();
        });
        verifyNoInteractions(cache);
        transaction.executeWithoutResult(status -> {
            service.delete(1L, 1);
            verifyNoInteractions(cache);
        });
        verify(cache).evict(1L, 1);
    }

    @Test
    void rolledBackThrottledWriteDoesNotSuppressNextDatabaseWrite() {
        transaction.executeWithoutResult(status -> {
            service.persistThrottled(1L, 1, NewState.create());
            status.setRollbackOnly();
        });
        verifyNoInteractions(cache);
        service.persistThrottled(1L, 1, NewState.create());
        verify(repo, times(2)).save(save);
        verify(cache).putJson(1L, 1, save.getStateJson());
    }

    @Test
    void throttledCacheOnlyWriteWaitsForCommitAndIgnoresRollback() {
        service.persistThrottled(1L, 1, NewState.create());
        clearInvocations(repo, cache);
        transaction.executeWithoutResult(status -> {
            service.persistThrottled(1L, 1, NewState.create());
            verifyNoInteractions(cache);
            status.setRollbackOnly();
        });
        verifyNoInteractions(cache);
        transaction.executeWithoutResult(status -> {
            PlayerState state = NewState.create();
            state.gold = BigDecimal.TEN;
            service.persistThrottled(1L, 1, state);
            state.gold = BigDecimal.ZERO;
            verifyNoInteractions(cache);
        });
        verify(repo, never()).save(any(GameSave.class));
        var json = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(cache).putJson(eq(1L), eq(1), json.capture());
        assertEquals(0, BigDecimal.TEN.compareTo(StateCodec.decode(json.getValue()).gold));
    }

    @Test
    void deletionOnlyClearsThrottleAfterCommit() {
        service.persistThrottled(1L, 1, NewState.create());
        clearInvocations(repo, cache);
        transaction.executeWithoutResult(status -> {
            service.delete(1L, 1);
            status.setRollbackOnly();
        });
        service.persistThrottled(1L, 1, NewState.create());
        verify(repo, never()).save(any(GameSave.class));
        transaction.executeWithoutResult(status -> service.delete(1L, 1));
        service.persistThrottled(1L, 1, NewState.create());
        verify(repo).save(save);
    }

    @Test
    void commitCallbackRunsBeforeLockIsReleased() {
        AtomicBoolean locked = new AtomicBoolean();
        StateCache lockCache = new StateCache(null, 30) {
            @Override
            public boolean lockRetry(long userId, int slot) {
                locked.set(true);
                return true;
            }

            @Override
            public void unlock(long userId, int slot) {
                locked.set(false);
            }

            @Override
            public void putJson(long userId, int slot, String json) {
                assertTrue(locked.get(), "提交回调发布缓存时必须仍持有槽位锁");
                cache.putJson(userId, slot, json);
            }
        };
        SaveService lockedService = new SaveService(repo, lockCache);
        lockCache.withLock(1L, 1, () -> transaction.executeWithoutResult(status -> {
            lockedService.persist(1L, 1, NewState.create(), null);
            verifyNoInteractions(cache);
        }));
        assertFalse(locked.get());
        verify(cache).putJson(1L, 1, save.getStateJson());
    }

    /** 只提供 Spring 事务生命周期，不连接数据库。 */
    private static class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) { }

        @Override
        protected void doCommit(DefaultTransactionStatus status) { }

        @Override
        protected void doRollback(DefaultTransactionStatus status) { }
    }
}
