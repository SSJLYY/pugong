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
 * 第六批第 23 轮：怪物词条「晶化」——击杀必额外掉 2 份本区材料，森林起入池。
 */
class CrystalTraitTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void killGrantsBonusMaterials() {
        PlayerState s = NewState.create();
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "wolf";
        m.name = "晶化狼";
        m.trait = "crystal";
        m.maxHP = Num.of(10);
        m.curHP = Num.of(10);
        m.PATK = Num.ZERO;
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        m.gold = 5;
        s.mob = m;
        s.zone = "forest";
        s.life.set("PATK", new BigDecimal("100"));
        s.fixed.CRIT = BigDecimal.ZERO;
        s.life.maxHP = new BigDecimal("10000");
        s.life.curHP = new BigDecimal("10000");
        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("10"), 1.0);
        int fangs = s.materials.getOrDefault("wolf_fang", 0);
        assertTrue(fangs >= 2, "晶化击杀应至少 +2 狼牙（基础掷骰另算），实际=" + fangs);
    }

    @Test
    void poolFromForest() {
        Set<String> plain = new HashSet<>();
        Set<String> forest = new HashSet<>();
        for (int i = 0; i < 4000; i++) {
            String a = GameConst.rollMobTrait("plain");
            if (a != null) plain.add(a);
            String b = GameConst.rollMobTrait("forest");
            if (b != null) forest.add(b);
        }
        assertFalse(plain.contains("crystal"), "平原池不应有晶化");
        assertTrue(forest.contains("crystal"), "森林池应能掷出晶化");
    }
}
