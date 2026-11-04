package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第八批第 76 轮：成就全量覆盖复核——每个成就都必须有进度口径（progressOf 非空、
 * 目标为正）与正奖励（rewardOf > 0），防新成就漏挂任一处开关。
 */
class AchievementCoverageTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    /** 二值成就（无进度条口径，progressOf 按设计返回 null）：一次性事件类，共 7 项。 */
    private static final java.util.Set<String> BINARY = java.util.Set.of(
            "boss_win", "first_death", "first_kill", "first_punch", "gen_1", "talent", "wear_epic",
            "zone_volcano");

    @Test
    void everyAchievementHasProgressAndReward() {
        PlayerState fresh = NewState.create();
        for (Achievements.Def d : Achievements.ALL) {
            long[] pr = Achievements.progressOf(fresh, d.id());
            if (BINARY.contains(d.id())) {
                org.junit.jupiter.api.Assertions.assertNull(pr,
                        "二值成就 " + d.id() + " 不应有进度口径（若新加了口径请同步移出 BINARY 集）");
            } else {
                assertNotNull(pr, "成就 " + d.id() + " 缺 progressOf 口径（非二值成就必须有进度条）");
                assertEquals(2, pr.length);
                assertTrue(pr[1] > 0, "成就 " + d.id() + " 目标应为正：" + pr[1]);
                assertTrue(pr[0] >= 0, "成就 " + d.id() + " 当前值不应为负");
            }
            assertTrue(Achievements.rewardOf(d.id()) > 0, "成就 " + d.id() + " 奖励应为正");
        }
        assertEquals(80, Achievements.ALL.size(), "成就总数应为 80");
    }
}
