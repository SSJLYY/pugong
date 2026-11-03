package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第七批第 22 轮：深渊实战链路——击杀入图鉴、晶化怪必掉深渊结晶、Boss 首杀登记。 */
class CombatAbyssTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private static PlayerState strongInAbyss() {
        PlayerState s = NewState.create();
        s.zone = "abyss";
        s.life.maxHP = new BigDecimal("5000000");
        s.life.curHP = new BigDecimal("5000000");
        s.life.set("PATK", new BigDecimal("10000"));
        s.life.set("PDEF", new BigDecimal("500"));
        s.life.set("MDEF", new BigDecimal("500"));
        s.fixed.CRIT = BigDecimal.ZERO;
        return s;
    }

    private static void killCurrent(PlayerState s) {
        s.mob.curHP = new BigDecimal("0.1");   // 一拳必杀，避免多拳污染计数
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("50"), 1.0);
    }

    @Test
    void killGrantsCodexAndCrystalMats() {
        PlayerState s = strongInAbyss();
        GameEngine.spawnMob(s, "abyss");
        String mobId = s.mob.id;
        s.mob.trait = "crystal";   // 晶化：击杀必额外掉 2 份本区材料
        int matsBefore = s.materials.getOrDefault("abyss_crystal", 0);
        killCurrent(s);
        assertTrue(s.codex.contains(mobId), "深渊怪击杀应写入图鉴：" + mobId);
        int mats = s.materials.getOrDefault("abyss_crystal", 0);
        assertTrue(mats >= matsBefore + 2, "晶化深渊怪应至少掉 2 份深渊结晶，实际增量 " + (mats - matsBefore));
    }

    @Test
    void abyssBossKillRegisters() {
        PlayerState s = strongInAbyss();
        GameEngine.spawnBoss(s);
        assertEquals("boss_abyss", s.mob.id, "深渊 Boss 应为无光君王");
        long bossBefore = s.stats.bossKillCount;
        killCurrent(s);
        assertTrue(s.bossKills.contains("boss_abyss"), "Boss 首杀应登记 bossKills");
        assertEquals(bossBefore + 1, s.stats.bossKillCount, "Boss 击杀计数 +1");
        assertTrue(Num.gt(s.gold, Num.ZERO), "Boss 金币奖励应入账");
    }
}
