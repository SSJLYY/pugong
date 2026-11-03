package com.gongpu.state;

import com.gongpu.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void bothCorruptThrowsClearError() {
        ApiException e = assertThrows(ApiException.class,
                () -> SaveService.decodeOrRecover(row("{bad", "{also bad")));
        assertEquals(500, e.getStatus());
        assertEquals("SAVE_CORRUPT", e.getCode());
    }
}
