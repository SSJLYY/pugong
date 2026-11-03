package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第六批第 22 轮：怪物词条「剧毒」——命中概率给玩家上毒（6 秒），矿洞起入池。
 * 概率 0.3/秒 × 10 秒帧 = 必触发，保证用例确定性。
 */
class VenomTraitTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void hitAppliesPoison() {
        PlayerState s = NewState.create();
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "spider";
        m.name = "毒蜘蛛";
        m.trait = "venom";
        m.maxHP = Num.of(100000);
        m.curHP = Num.of(100000);
        m.PATK = Num.of(1);
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        m.ASPD = 1;
        m.RNG = 1;
        m.SPD = 10;
        s.mob = m;
        s.zone = "mine";
        s.fixed.EVA = BigDecimal.ZERO;
        s.fixed.RNG = Num.of(1);
        s.fixed.SPD = Num.of(10);
        s.life.set("PDEF", Num.ZERO);
        s.life.set("MDEF", Num.ZERO);
        s.life.maxHP = Num.of(100000);
        s.life.curHP = Num.of(100000);
        GameEngine.mobAttack(s, PanelCalculator.of(s), 10.0);
        assertTrue(PanelCalculator.active(s, "poison"), "剧毒怪命中应给玩家上毒");
    }

    @Test
    void poolFromMine() {
        Set<String> forest = new HashSet<>();
        Set<String> mine = new HashSet<>();
        for (int i = 0; i < 4000; i++) {
            String a = GameConst.rollMobTrait("forest");
            if (a != null) forest.add(a);
            String b = GameConst.rollMobTrait("mine");
            if (b != null) mine.add(b);
        }
        assertFalse(forest.contains("venom"), "森林池不应有剧毒");
        assertTrue(mine.contains("venom"), "矿洞池应能掷出剧毒");
    }
}
