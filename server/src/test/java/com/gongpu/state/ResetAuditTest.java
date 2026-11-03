package com.gongpu.state;

import com.gongpu.cfg.GameData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 第 5 轮：死亡重开/转生共用重置（resetForNewLife）字段审计——
 * 当世量必须清零（重点回归：护盾是 shield_pot buff 附属量，buff 清空时曾漏清跨世残留），
 * 传承与账号级积累必须保留。
 */
class ResetAuditTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void shieldClearedWithBuffsOnNewLife() {
        PlayerState s = NewState.create();
        s.buffs.put("shield_pot", 30.0);
        s.shield = new BigDecimal("9999");
        SaveService.resetForNewLife(s);
        assertEquals(0, s.shield.compareTo(BigDecimal.ZERO), "buff 清空后护盾不得跨世残留");
        assertTrue(s.buffs.isEmpty());
    }

    @Test
    void currentLifeFieldsResetHeritageKept() {
        PlayerState s = NewState.create();
        s.gold = new BigDecimal("12345");
        s.equipBag.add(new PlayerState.Item());
        s.potions.put("hp_l", 9);
        s.activeQuests.put("q1", BigDecimal.TEN);
        s.stats.punches = new BigDecimal("777");
        s.stats.kills = new BigDecimal("55");
        // 传承/积累类：赋能、天赋、图鉴、成就、已完成委托、无相位栏、终身统计
        s.blessings.put("hp", new PlayerState.Bless(2));
        s.talent = "eternal";
        s.codex.add("rabbit");
        s.achievements.add("first_kill");
        s.doneQuests.add("q0");
        s.extraSlots = 3;
        s.stats.lifetimePunches = new BigDecimal("10000");
        s.stats.deaths = 2;

        SaveService.resetForNewLife(s);

        assertEquals(0, s.gold.compareTo(BigDecimal.ZERO));
        assertTrue(s.equipBag.isEmpty());
        assertFalse(s.potions.containsKey("hp_l"));
        assertTrue(s.activeQuests.isEmpty(), "进行中委托随 kills 基线清零（基线已失效）");
        assertEquals(0, s.stats.punches.compareTo(BigDecimal.ZERO));
        assertEquals(0, s.stats.kills.compareTo(BigDecimal.ZERO));

        assertTrue(s.blessings.containsKey("hp"), "赋能是万世传承，必须保留");
        assertEquals("eternal", s.talent);
        assertTrue(s.codex.contains("rabbit"));
        assertTrue(s.achievements.contains("first_kill"));
        assertTrue(s.doneQuests.contains("q0"), "已完成委托记录保留（不可重复接取语义跨世成立）");
        assertEquals(3, s.extraSlots);
        assertEquals(0, s.stats.lifetimePunches.compareTo(new BigDecimal("10000")));
        assertEquals(2, s.stats.deaths);
    }
}
