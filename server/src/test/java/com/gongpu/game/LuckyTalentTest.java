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
 * 第七批第 87 轮：天赋「幸运儿」——掉落率 +15%，在 dropLuckMul 单点与鸿运药剂/幸运星乘算；
 * 天赋池扩至 9 个，rollTalent 常规池可抽中幸运儿。
 */
class LuckyTalentTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void luckyTalentMultipliesDropLuck() {
        PlayerState s = NewState.create();
        assertEquals(1.0, GameEngine.dropLuckMul(s), 1e-9);
        s.talent = "lucky";
        assertEquals(1.15, GameEngine.dropLuckMul(s), 1e-9, "幸运儿应 +15% 掉落");
        s.buffs.put("luck_pot", 300.0);
        assertEquals(1.5 * 1.15, GameEngine.dropLuckMul(s), 1e-9, "与鸿运药剂乘算");
        s.passives.add("lucky_star");
        assertEquals(1.5 * 1.15 * GameConst.LUCKY_STAR_MUL, GameEngine.dropLuckMul(s), 1e-9,
                "与幸运星被动继续乘算");
    }

    @Test
    void talentRegisteredAndRollable() {
        assertNotNull(GameConst.TALENTS.get("lucky"));
        assertEquals(9, GameConst.TALENTS.size(), "天赋总数应达 9");
        int hits = 0;
        for (int i = 0; i < 20000; i++) {
            if ("lucky".equals(GameService.rollTalent())) {
                hits++;
            }
        }
        assertTrue(hits > 100, "常规池应能抽中幸运儿（期望约 333 次），实际 " + hits);
    }
}
