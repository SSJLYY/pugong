package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第六批第 89 轮：新主动「净化」——清除四种负面状态，保留增益，扣蓝进 CD。 */
class CleanseSkillTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void cleansesAllDebuffsKeepsBuffs() {
        PlayerState s = NewState.create();
        s.zone = "swamp";
        s.life.curMP = new BigDecimal("1000");
        s.buffs.put("poison", 8.0);
        s.buffs.put("burn_dot", 8.0);
        s.buffs.put("silence", 5.0);
        s.buffs.put("slow", 4.0);
        s.buffs.put("rapid", 5.0);   // 增益应保留
        Map<String, Object> r = GameService.castSkill(s, "cleanse");
        assertEquals(4, r.get("cleansed"), "应清除 4 种负面状态");
        for (String d : new String[]{"poison", "burn_dot", "silence", "slow"}) {
            assertFalse(s.buffs.containsKey(d), d + " 应被净化");
        }
        assertTrue(s.buffs.containsKey("rapid"), "增益不应被净化");
        assertTrue(Num.lt(s.life.curMP, new BigDecimal("1000")), "净化应扣蓝");
        assertTrue(s.cds.getOrDefault("cleanse", 0.0) > 0, "净化应进 CD");
    }

    @Test
    void cleanseWithNoDebuffsStillCasts() {
        PlayerState s = NewState.create();
        s.life.curMP = new BigDecimal("1000");
        Map<String, Object> r = GameService.castSkill(s, "cleanse");
        assertEquals(0, r.get("cleansed"), "无负面状态时清除数应为 0（仍可施放）");
    }
}
