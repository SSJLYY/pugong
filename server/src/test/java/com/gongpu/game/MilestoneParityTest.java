package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第八批第 77 轮：成就里程碑档位三方一致——服务端常量、成就面板镜像（achievements.js）、
 * 后端结算逻辑同档同奖，防单边改档导致前端显示与实发不符。
 */
class MilestoneParityTest {

    @Test
    void tiersMatchAcrossServerAndFrontend() throws Exception {
        assertArrayEquals(new long[]{15, 30, 45, 60, 75, 90, 105, 120, 135}, GameService.ACH_MILESTONE_TIERS);
        assertArrayEquals(new long[]{10_000, 30_000, 80_000, 200_000, 500_000, 1_000_000, 3_000_000, 5_000_000, 10_000_000}, GameService.ACH_MILESTONE_BONUS);
        String js = Files.readString(Path.of("../web/js/achievements.js"));
        assertTrue(js.contains("[[15, 10000], [30, 30000], [45, 80000], [60, 200000], [75, 500000], [90, 1000000], [105, 3000000], [120, 5000000], [135, 10000000]]"),
                "achievements.js 里程碑档位与服务端不一致");
        String svc = Files.readString(Path.of("src/main/java/com/gongpu/game/GameService.java"));
        assertTrue(svc.contains("settleAchMilestone(s)"), "领取路径必须结算里程碑");
    }
}
