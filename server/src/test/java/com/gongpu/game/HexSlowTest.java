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
 * 第七批第 86 轮：主动「迟缓咒」——当前怪攻速 ×0.7 持续 8 秒（slowT 随帧递减、到期解除），
 * 怪反击总伤按 0.7 折算。反击口径：怪 PATK 100、无防无闪避、攻速 1 → 1 秒应打 100，迟缓后 70。
 */
class HexSlowTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fighter() {
        PlayerState s = NewState.create();
        s.zone = "plain";
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "boar";
        m.name = "野猪";
        m.emoji = "🐗";
        m.maxHP = new BigDecimal("100000000");
        m.curHP = new BigDecimal("100000000");
        m.PATK = Num.of(100);
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        m.ASPD = 1.0;
        m.SPD = 0; // 不风筝：玩家射程内
        s.mob = m;
        s.life.set("PATK", Num.ZERO);
        s.life.set("PDEF", Num.ZERO); // NewState 默认 PDEF=2 会污染每击口径
        s.life.set("MDEF", Num.ZERO);
        s.life.set("REGEN", Num.ZERO);
        s.fixed.ASPD = BigDecimal.ZERO; // 玩家不出拳，纯挨打口径
        s.fixed.EVA = BigDecimal.ZERO;
        s.fixed.SPD = Num.of(9999);
        s.life.maxHP = new BigDecimal("100000");
        s.life.curHP = new BigDecimal("100000");
        s.life.curMP = Num.of(100);
        return s;
    }

    @Test
    void castAppliesSlowAndExpires() {
        PlayerState s = fighter();
        GameService.castSkill(s, "hex_slow");
        assertEquals(8.0, s.mob.slowT, 1e-9, "施放后应挂 8 秒迟缓");
        // advance 3 秒：slowT 递减到 5（玩家 ASPD=0 不出拳、怪每秒打 70）
        long now = System.currentTimeMillis();
        s.lastTickAt = now - 3000; // advance 按真实流逝（now-lastTickAt）结算帧长
        GameEngine.advance(s, now, 3.0, false);
        assertEquals(5.0, s.mob.slowT, 1e-9);
        // 命中数是 double（攻速乘区），总伤带浮点尘，用 ±1 容差
        assertTrue(Math.abs(s.life.curHP.doubleValue() - 99790.0) < 1.0,
                "迟缓 3 秒应只挨 3×70=210 伤，实际剩余 " + s.life.curHP);
    }

    @Test
    void noSlowFullDamage() {
        PlayerState s = fighter();
        long now = System.currentTimeMillis();
        s.lastTickAt = now - 3000; // advance 按真实流逝（now-lastTickAt）结算帧长
        GameEngine.advance(s, now, 3.0, false);
        assertTrue(s.life.curHP.compareTo(new BigDecimal("100000").subtract(Num.of(300))) == 0,
                "无迟缓 3 秒应挨 3×100=300 伤，实际剩余 " + s.life.curHP);
    }
}
