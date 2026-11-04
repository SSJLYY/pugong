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

/**
 * 第八批第 18 轮：词条预警——带词条怪首次交手播报一次（名称+克制说明），
 * 后续帧不再刷屏；无词条怪不播报。
 */
class TraitWarnTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fighter(String trait) {
        PlayerState s = NewState.create();
        s.life.set("PATK", new BigDecimal("100"));
        s.fixed.CRIT = BigDecimal.ZERO;
        s.fixed.CRITD = BigDecimal.ONE;
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "warn_dummy";
        m.name = "预警桩";
        m.maxHP = new BigDecimal("1000000000");
        m.curHP = new BigDecimal("1000000000");
        m.trait = trait;
        s.mob = m;
        return s;
    }

    @Test
    void warnsOncePerMob() {
        PlayerState s = fighter("regen");
        PanelCalculator.Panel p = PanelCalculator.of(s);
        GameEngine.dealDamage(s, p, Num.of(1), 1.0);
        long warns = s.events.stream().filter(e -> e.contains("再生")).count();
        assertEquals(1, warns, "首次交手应播报一次再生词条");
        assertTrue(s.mob.traitWarned);
        GameEngine.dealDamage(s, p, Num.of(1), 1.0);
        warns = s.events.stream().filter(e -> e.contains("再生")).count();
        assertEquals(1, warns, "后续帧不应重复播报");
    }

    @Test
    void plainMobNoWarning() {
        PlayerState s = fighter(null);
        GameEngine.dealDamage(s, PanelCalculator.of(s), Num.of(1), 1.0);
        assertTrue(s.events.stream().noneMatch(e -> e.contains("带词条")), "无词条怪不应播报");
    }
}
