package com.gongpu.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 第七批第 73 轮：滚动备份环——轮转间隔、容量裁剪、坏环重建、解码回退顺序。
 */
class BackupRingTest {

    private static String stateOf(int gen) {
        PlayerState s = NewState.create();
        s.gen = gen;
        return StateCodec.encode(s);
    }

    @Test
    void rotateAppendsAndCapsAtThree() {
        long t0 = 1_000_000L;
        String ring = null;
        for (int i = 1; i <= 5; i++) {
            ring = SaveService.rotateBackupRing(ring, stateOf(i), t0 + i * SaveService.BACKUP_ROTATE_MS);
        }
        // 5 次轮转后只剩最近 3 份（gen 3/4/5），解码取最新 = 5
        PlayerState s = SaveService.decodeFromRing(ring);
        assertNotNull(s);
        assertEquals(5, s.gen);
    }

    @Test
    void withinIntervalIsNoOp() {
        long t0 = 5_000_000L;
        String ring = SaveService.rotateBackupRing(null, stateOf(1), t0);
        String again = SaveService.rotateBackupRing(ring, stateOf(2), t0 + 60_000L);
        assertSame(ring, again, "未满 10 分钟轮转间隔应原样返回（不产生新备份）");
        assertEquals(1, SaveService.decodeFromRing(again).gen);
    }

    @Test
    void corruptRingRebuilds() {
        String ring = SaveService.rotateBackupRing("{not a ring", stateOf(7), 9_000_000L);
        assertEquals(7, SaveService.decodeFromRing(ring).gen, "坏环应被当前状态重建");
    }

    @Test
    void decodeFromRingSkipsCorruptNewest() {
        long t0 = 3_000_000L;
        String ring = SaveService.rotateBackupRing(null, stateOf(3), t0);
        ring = SaveService.rotateBackupRing(ring, "{corrupt state", t0 + SaveService.BACKUP_ROTATE_MS);
        PlayerState s = SaveService.decodeFromRing(ring);
        assertNotNull(s, "最新一份坏了应回退到更旧的一份");
        assertEquals(3, s.gen);
        assertNull(SaveService.decodeFromRing(null));
        assertNull(SaveService.decodeFromRing("{bad ring"));
    }

    @Test
    void decodeOrRecoverPrefersRingOverSnapshot() {
        PlayerState ringState = NewState.create();
        ringState.gen = 9;
        String ring = SaveService.rotateBackupRing(null, StateCodec.encode(ringState), 7_000_000L);
        GameSave gs = new GameSave();
        gs.setStateJson("{bad main");
        gs.setBackupRing(ring);
        gs.setSnapshotJson(stateOf(4));
        PlayerState s = SaveService.decodeOrRecover(gs);
        assertEquals(9, s.gen, "主档损坏时应优先用备份环（比存档点快照更新）");
    }
}
