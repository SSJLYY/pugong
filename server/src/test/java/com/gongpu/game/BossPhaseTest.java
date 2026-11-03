package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第七批第 46 轮：Boss 二阶段——跌破半血置 phase2（只一次、带播报），
 * 反击攻速 ×1.3、攻击 ×1.2；击破二阶段 Boss 计入 bossPhaseKills；普通怪无二阶段。
 */
class BossPhaseTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fighter(BigDecimal bossCurHP) {
        PlayerState s = NewState.create();
        s.zone = "plain";
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "boss_boar";
        m.name = "野猪王";
        m.emoji = "🐗";
        m.boss = true;
        m.maxHP = new BigDecimal("10000");
        m.curHP = bossCurHP;
        m.PATK = new BigDecimal("100");
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        m.ASPD = 1.0;
        m.SPD = 1;
        s.mob = m;
        s.life.maxHP = new BigDecimal("1000000");
        s.life.curHP = new BigDecimal("1000000");
        s.life.set("PDEF", Num.ZERO);
        s.life.set("MDEF", Num.ZERO);
        s.fixed.EVA = BigDecimal.ZERO;
        s.fixed.RNG = BigDecimal.ZERO;
        s.fixed.SPD = new BigDecimal("9999");   // 移速压制避免被风筝判定干扰（kite 还看射程，同为 0 时不风筝）
        return s;
    }

    @Test
    void phaseTriggersBelowHalfOnce() {
        PlayerState s = fighter(new BigDecimal("6000"));
        GameEngine.checkBossPhase(s);
        assertFalse(s.mob.phase2, "60% 血不应进二阶段");
        s.mob.curHP = new BigDecimal("4999");
        GameEngine.checkBossPhase(s);
        assertTrue(s.mob.phase2, "跌破半血应进二阶段");
        assertTrue(s.events.stream().anyMatch(e -> e.contains("二阶段")), "进二阶段应有战斗日志播报");
        int events = s.events.size();
        GameEngine.checkBossPhase(s);
        assertEquals(events, s.events.size(), "二阶段只应触发一次");
    }

    @Test
    void phase2BoostsBossAttack() {
        PlayerState s = fighter(new BigDecimal("10000"));
        BigDecimal hpBefore = s.life.curHP;
        GameEngine.mobAttack(s, PanelCalculator.of(s), 1.0);
        BigDecimal dmgNormal = hpBefore.subtract(s.life.curHP);
        s.life.curHP = hpBefore;
        s.mob.phase2 = true;
        GameEngine.mobAttack(s, PanelCalculator.of(s), 1.0);
        BigDecimal dmgPhase2 = hpBefore.subtract(s.life.curHP);
        // 攻速 ×1.3 与攻击 ×1.2 叠加 → 总伤 ×1.56（100 攻、1 秒、闪避 0）
        assertEquals(0, dmgNormal.compareTo(new BigDecimal("100")), "一阶段总伤应为 100");
        assertEquals(0, dmgPhase2.compareTo(new BigDecimal("156")), "二阶段总伤应为 100×1.3×1.2=156");
    }

    @Test
    void normalMobHasNoPhase() {
        PlayerState s = fighter(new BigDecimal("100"));
        s.mob.boss = false;
        GameEngine.checkBossPhase(s);
        assertFalse(s.mob.phase2, "普通怪不应有二阶段");
    }
}
