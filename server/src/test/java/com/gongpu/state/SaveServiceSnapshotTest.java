package com.gongpu.state;

import com.gongpu.config.StateCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 村口自动快照语义锁(SaveService.persist 内分支,设计 §3.2"回安全区自动存主槽"):
 * <b>只有「村口 + 未死亡 + 距上次快照 ≥5 分钟」才自动刷回档点</b>;state_json 则每帧照常滚动。
 * <p>persist 依赖 repo/cache:仓库用 Mockito 假件(只存于测试),缓存用无操作子类,
 * 快照分支的落点直接断言被持久化的 GameSave 实体(真实对象,persist 原地改写)。
 * 不为此改任何产品代码。</p>
 */
class SaveServiceSnapshotTest {

    private GameSaveRepository repo;
    private GameSave gs;
    private SaveService service;

    @BeforeEach
    void setUp() {
        gs = new GameSave();
        gs.setUserId(1L);
        gs.setSlotNo(1);
        repo = mock(GameSaveRepository.class);
        when(repo.findByUserIdAndSlotNo(anyLong(), anyInt())).thenReturn(Optional.of(gs));
        // 无操作缓存:StateCache 构造只赋字段,不触碰 Redis;put/get/evict 全部短路
        StateCache noopCache = new StateCache(null, 30) {
            @Override
            public void put(long userId, int slot, PlayerState state) { }
            @Override
            public PlayerState get(long userId, int slot) { return null; }
            @Override
            public void evict(long userId, int slot) { }
        };
        service = new SaveService(repo, noopCache);
    }

    private PlayerState stateAt(String zone, boolean dead) {
        PlayerState s = NewState.create();
        s.zone = zone;
        s.dead = dead;
        return s;
    }

    /** 村口 + 存活 + 从未快照过(savedAt=null)→ 必须写快照,内容为当前进度的完整 JSON。 */
    @Test
    void villageAliveWritesSnapshotWhenNeverSaved() {
        PlayerState s = stateAt("village", false);
        assertNull(gs.getSnapshotJson(), "前置:槽位尚无快照");

        service.persist(1L, 1, s, null);

        assertNotNull(gs.getSnapshotJson(), "村口存活且从未快照过 → 必须自动刷回档点(保命底线)");
        assertNotNull(gs.getSavedAt(), "快照时间必须同步刷新(下一次 5 分钟窗口的起点)");
        assertNotNull(gs.getStateJson(), "state_json 常规落库不受快照分支影响");
        assertEquals(0, StateCodec.decode(gs.getSnapshotJson()).life.maxHP.compareTo(s.life.maxHP),
                "快照必须是含当前进度的完整可回档 JSON");
    }

    /** 村口 + 存活 + 距上次快照 <5 分钟 → 不覆盖回档点(回档不倒扣超过 5 分钟进度)。 */
    @Test
    void villageAliveSkipsWithinFiveMinutes() {
        PlayerState s = stateAt("village", false);
        gs.setSnapshotJson("OLD_SNAPSHOT");
        gs.setSavedAt(Instant.now().minusSeconds(4 * 60));

        service.persist(1L, 1, s, null);

        assertEquals("OLD_SNAPSHOT", gs.getSnapshotJson(), "距上次快照 <5min 不得刷快照");
        assertNotNull(gs.getStateJson(), "state_json 仍应照常滚动落库");
    }

    /** 边界:恰好 ≥5 分钟(5min+1s)→ 重新写快照。 */
    @Test
    void villageAliveRewritesAfterFiveMinutes() {
        PlayerState s = stateAt("village", false);
        gs.setSnapshotJson("OLD_SNAPSHOT");
        gs.setSavedAt(Instant.now().minusSeconds(5 * 60 + 1));

        service.persist(1L, 1, s, null);

        assertNotEquals("OLD_SNAPSHOT", gs.getSnapshotJson(), "村口距上次快照 ≥5min 应刷新回档点");
        assertNotNull(gs.getSavedAt());
    }

    /** 非村口:即使从未快照过也不写(回档点只属于安全区)。 */
    @Test
    void nonVillageNeverWritesSnapshot() {
        PlayerState s = stateAt("plain", false);
        assertNull(gs.getSavedAt(), "前置:从未快照过");

        service.persist(1L, 1, s, null);

        assertNull(gs.getSnapshotJson(), "非村口不得自动写快照");
        assertNotNull(gs.getStateJson(), "state_json 照常滚动落库");
    }

    /** 死亡态:即使在村口也不写快照(死亡正是要用旧快照回档的时刻,写了=白嫖复活)。 */
    @Test
    void deadStateNeverWritesSnapshot() {
        PlayerState s = stateAt("village", true);
        assertNull(gs.getSavedAt(), "前置:从未快照过");

        service.persist(1L, 1, s, null);

        assertNull(gs.getSnapshotJson(), "死亡态不得自动写快照");
    }
}
