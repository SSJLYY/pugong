package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 第五批第 5 轮：战绩统计——普攻结算帧累加终身总伤害、代表拳伤刷新最高单击。
 * 与 TraitThornsTest 同款夹具：PATK 恰好 100、暴击期望归零，每拳恰好 100 伤。
 */
class CombatStatsTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fighter() {
        PlayerState s = NewState.create();
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "dummy";
        m.name = "陪练";
        m.maxHP = new BigDecimal("1000000");
        m.curHP = new BigDecimal("1000000");
        m.PATK = Num.ZERO;
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        s.mob = m;
        s.life.set("PATK", new BigDecimal("100"));
        s.fixed.CRIT = BigDecimal.ZERO;
        s.fixed.CRITD = BigDecimal.ONE;   // 弱点打击中性化（第七批机制：暴伤=1 时弱点拳无加成，旧断言口径不变）
        s.life.maxHP = new BigDecimal("10000");
        s.life.curHP = new BigDecimal("10000");
        return s;
    }

    @Test
    void damageAccumulatesAndBestHitTracks() {
        PlayerState s = fighter();
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("10"), 1.0);
        assertEquals(0, s.stats.totalDamage.compareTo(Num.of(1000)),
                "10 拳 ×100 伤，总伤害应为 1000，实际=" + s.stats.totalDamage);
        assertEquals(0, s.stats.bestHit.compareTo(Num.of(100)), "最高单击应为单拳 100");
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("5"), 1.0);
        assertEquals(0, s.stats.totalDamage.compareTo(Num.of(1500)), "次帧应累加到 1500");
        assertEquals(0, s.stats.bestHit.compareTo(Num.of(100)), "最高单击不被更低值覆盖");
    }
}
