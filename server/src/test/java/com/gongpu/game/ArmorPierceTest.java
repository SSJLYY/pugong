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
 * 第八批第 13 轮：被动「破甲专精」——无视目标 20% 双防，
 * dealDamage/expectedPerHit 同口径；沉默时失效。
 */
class ArmorPierceTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fighter(boolean pierce) {
        PlayerState s = NewState.create();
        s.life.set("PATK", new BigDecimal("1000"));
        s.fixed.CRIT = BigDecimal.ZERO;
        if (pierce) {
            s.passives.add("armor_pierce");
        }
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "armor_dummy";
        m.name = "铁甲桩";
        m.maxHP = new BigDecimal("100000000");
        m.curHP = new BigDecimal("100000000");
        m.PDEF = new BigDecimal("400");
        s.mob = m;
        return s;
    }

    @Test
    void pierceIgnoresTwentyPercentDef() {
        PlayerState plain = fighter(false);
        PlayerState pierce = fighter(true);
        // DMG_DEF_FACTOR=0.8：无专精 1000 − 400×0.8 = 680
        BigDecimal base = GameEngine.expectedPerHit(plain, PanelCalculator.of(plain), plain.mob);
        // 破甲：1000 − 320×0.8 = 744
        BigDecimal with = GameEngine.expectedPerHit(pierce, PanelCalculator.of(pierce), pierce.mob);
        assertEquals(0, with.compareTo(Num.add(base, Num.of(64))),
                "破甲应让每拳多打 64（防 400 的 20%×0.8），base=" + base + " with=" + with);
        // effDef 直接口径与沉默失效
        assertEquals(0, GameEngine.effDef(pierce, new BigDecimal("400")).compareTo(new BigDecimal("320")));
        pierce.buffs.put("silence", 5.0);
        assertEquals(0, GameEngine.effDef(pierce, new BigDecimal("400")).compareTo(new BigDecimal("400")),
                "沉默应压制破甲专精");
    }
}
