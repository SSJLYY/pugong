package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 第 10 轮：图鉴成就——记录 10 种解锁 codex_10；集满可收集总数解锁 codex_all。 */
class CodexAchievementTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void codexTotalCountsKillableMobsAndBosses() {
        int total = GameConst.codexTotal();
        assertTrue(total >= 10, "可收集图鉴总数应不少于 10（现有区域怪种+Boss），实际=" + total);
    }

    @Test
    void tenSpeciesUnlocksCodex10Only() {
        PlayerState s = NewState.create();
        for (int i = 0; i < 10; i++) {
            s.codex.add("mob_" + i);
        }
        List<String> got = Achievements.evaluate(s);
        assertTrue(got.contains("codex_10"));
        assertFalse(got.contains("codex_all"), "未集满不得解锁全图鉴");
    }

    @Test
    void fullCodexUnlocksCodexAll() {
        PlayerState s = NewState.create();
        for (int i = 0; i < GameConst.codexTotal(); i++) {
            s.codex.add("mob_" + i);
        }
        List<String> got = Achievements.evaluate(s);
        assertTrue(got.contains("codex_10"));
        assertTrue(got.contains("codex_all"));
    }
}
