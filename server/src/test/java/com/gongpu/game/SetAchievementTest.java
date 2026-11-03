package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第七批第 8 轮：套装成就——集齐四件套解锁 set_4、双套各 2 件解锁 set_dual，进度口径一致。 */
class SetAchievementTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private static PlayerState.Item worn(String slot, String code) {
        PlayerState.Item it = new PlayerState.Item();
        it.slot = slot;
        it.code = code;
        it.name = code;
        return it;
    }

    private static void wearFarwalker(PlayerState s, int pieces) {
        String[][] all = {{"head", "leather_cap"}, {"chest", "leather_armor"},
                {"legs", "leather_pants"}, {"boots", "straw_sandals"}};
        for (int i = 0; i < pieces; i++) {
            s.equipped.put(all[i][0], worn(all[i][0], all[i][1]));
        }
    }

    @Test
    void threePiecesUnlockNothing() {
        PlayerState s = NewState.create();
        wearFarwalker(s, 3);
        List<String> newly = Achievements.evaluate(s);
        assertFalse(newly.contains("set_4"), "3 件不应解锁整装待发");
        assertFalse(newly.contains("set_dual"), "单套不应解锁双套加身");
        assertArrayEquals(new long[]{3, 4}, Achievements.progressOf(s, "set_4"));
    }

    @Test
    void fourPiecesUnlockSet4() {
        PlayerState s = NewState.create();
        wearFarwalker(s, 4);
        assertTrue(Achievements.evaluate(s).contains("set_4"));
    }

    @Test
    void twoSetsUnlockDual() {
        PlayerState s = NewState.create();
        wearFarwalker(s, 2);
        s.equipped.put("extra_0", worn("head", "miner_helm"));
        s.equipped.put("extra_1", worn("chest", "chain_mail"));
        List<String> newly = Achievements.evaluate(s);
        assertTrue(newly.contains("set_dual"), "两套各 2 件应解锁双套加身");
        assertFalse(newly.contains("set_4"));
        assertArrayEquals(new long[]{2, 2}, Achievements.progressOf(s, "set_dual"));
    }
}
