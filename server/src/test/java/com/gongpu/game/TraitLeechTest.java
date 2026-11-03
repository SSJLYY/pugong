package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** 第 7 轮：新怪物词条「嗜血」——命中吸回实际掉血 30%，封顶怪自身上限，沼泽起解锁。 */
class TraitLeechTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState.MobState testMob(String trait, String maxHP, String curHP) {
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "testmob";
        m.name = "测试怪";
        m.emoji = "👾";
        m.maxHP = new BigDecimal(maxHP);
        m.curHP = new BigDecimal(curHP);
        m.PATK = new BigDecimal("200");
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        m.ASPD = 1;
        m.RNG = 0;
        m.SPD = 1;
        m.trait = trait;
        return m;
    }

    private PlayerState stateWith(PlayerState.MobState m) {
        PlayerState s = NewState.create();
        s.life.maxHP = new BigDecimal("100000");
        s.life.curHP = s.life.maxHP;
        s.life.set("PDEF", Num.ZERO);
        s.mob = m;
        return s;
    }

    @Test
    void leechHealsThirtyPercentOfDamageDealt() {
        PlayerState.MobState m = testMob("leech", "10000", "5000");
        PlayerState s = stateWith(m);
        GameEngine.mobAttack(s, PanelCalculator.of(s), 1.0);
        BigDecimal loss = s.life.maxHP.subtract(s.life.curHP);
        assertTrue(loss.signum() > 0, "怪应命中玩家");
        BigDecimal expected = new BigDecimal("5000").add(loss.multiply(new BigDecimal("0.30")));
        assertEquals(0, m.curHP.compareTo(expected), "吸血 = 实际掉血 × 30%");
    }

    @Test
    void leechHealCapsAtMobMaxHp() {
        PlayerState.MobState m = testMob("leech", "10000", "9990");
        PlayerState s = stateWith(m);
        GameEngine.mobAttack(s, PanelCalculator.of(s), 1.0);
        assertEquals(0, m.curHP.compareTo(new BigDecimal("10000")), "吸血不得超过怪生命上限");
    }

    @Test
    void plainMobDoesNotHeal() {
        PlayerState.MobState m = testMob(null, "10000", "5000");
        PlayerState s = stateWith(m);
        GameEngine.mobAttack(s, PanelCalculator.of(s), 1.0);
        assertEquals(0, m.curHP.compareTo(new BigDecimal("5000")), "无词条怪不回血");
    }

    @Test
    void leechUnlocksFromSwampOnly() {
        for (int i = 0; i < 500; i++) {
            String t = GameConst.rollMobTrait("mine");
            assertNotEquals("leech", t, "矿洞词条池不应出现嗜血");
        }
        boolean sawLeech = false;
        for (int i = 0; i < 3000 && !sawLeech; i++) {
            sawLeech = "leech".equals(GameConst.rollMobTrait("swamp"));
        }
        assertTrue(sawLeech, "沼泽 3000 次掷骰应至少出一次嗜血（单次约 19%/4）");
    }
}
