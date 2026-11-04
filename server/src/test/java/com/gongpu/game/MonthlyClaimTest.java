package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 第九批·月常领取全链（第 32 轮）：全清奖金与 fullView 本体下发。 */
class MonthlyClaimTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void allClearPaysBonusAndGems() {
        PlayerState s = NewState.create();
        MonthlyQuests.ensure(s);
        GameService g = new GameService();
        Map<String, Object> last = null;
        for (PlayerState.DailyTask t : s.monthlyTasks) {
            t.baseline = "-" + (t.target + 1);
            last = g.claimMonthly(s, t.id);
        }
        assertNotNull(last);
        assertEquals(300_000L, ((Number) last.get("allClearBonus")).longValue());
        assertEquals(3, ((java.util.List<?>) last.get("allClearGems")).size());
        assertTrue(s.monthlyBonusGiven);
    }

    @Test
    void fullViewCarriesMonthlyTasks() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/gongpu/game/GameService.java"));
        int full = src.indexOf("public Map<String, Object> fullView(PlayerState s)");
        assertTrue(src.indexOf("m.put(\"monthlyTasks\", MonthlyQuests.viewOf(s))", full) > full,
                "monthlyTasks 必须挂 fullView 本体");
        String ctrl = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/gongpu/web/GameController.java"));
        int ensure = ctrl.indexOf("MonthlyQuests.ensure(s)");
        int persist = ctrl.indexOf("saves.persist(uid, slot, s, null);", ensure);
        assertTrue(ensure > 0 && persist > ensure, "ensure 必须在落库前（runLocked 内）");
    }
}
