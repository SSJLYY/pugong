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
 * 第七批第 41 轮：词条「诅咒」——与诅咒怪交战时玩家一切回血减半
 * （喝药/回复 tick/吸血三口径一致），沼泽起入池。
 */
class TraitCurseTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fighter(String trait) {
        PlayerState s = NewState.create();
        s.zone = "swamp";
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "wraith";
        m.name = "诅咒怨灵";
        m.emoji = "👻";
        m.maxHP = new BigDecimal("1000000000");
        m.curHP = new BigDecimal("1000000000");
        m.PATK = Num.ZERO;
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        m.trait = trait;
        s.mob = m;
        s.life.maxHP = new BigDecimal("10000");
        return s;
    }

    @Test
    void potionHealHalvedUnderCurse() {
        GameService game = new GameService();
        PlayerState s = fighter("curse");
        s.life.curHP = BigDecimal.ZERO;
        s.potions.put("hp_s", 1);
        game.usePotion(s, "hp_s");
        // 小血药回 20% 上限 = 2000，诅咒减半 → 1000
        assertEquals(0, s.life.curHP.compareTo(new BigDecimal("1000")),
                "诅咒在场喝药回血应减半");
    }

    @Test
    void potionHealFullWithoutCurse() {
        GameService game = new GameService();
        PlayerState s = fighter(null);
        s.life.curHP = BigDecimal.ZERO;
        s.potions.put("hp_s", 1);
        game.usePotion(s, "hp_s");
        assertEquals(0, s.life.curHP.compareTo(new BigDecimal("2000")),
                "无诅咒时喝药应全额回复");
    }

    @Test
    void regenTickHalvedUnderCurse() {
        PlayerState s = fighter("curse");
        s.life.set("REGEN", new BigDecimal("1000"));
        s.life.curHP = new BigDecimal("5000");
        s.life.maxHP = new BigDecimal("100000");
        s.fixed.ASPD = BigDecimal.ZERO;   // 不出拳：排除出拳成长对 curHP 的干扰
        long now = System.currentTimeMillis();
        s.lastTickAt = now - 1000;
        GameEngine.advance(s, now, 1.0, false);
        assertEquals(0, s.life.curHP.compareTo(new BigDecimal("5500")),
                "诅咒在场每秒回复应减半：5000 + 1000×0.5");
    }

    @Test
    void lifestealHalvedUnderCurse() {
        // 双生对照：同一套出拳分别打普通怪与诅咒怪，出拳成长等副作用相同，差额只来自吸血减半
        PlayerState plain = fighter(null);
        PlayerState cursed = fighter("curse");
        for (PlayerState s : new PlayerState[]{plain, cursed}) {
            s.life.set("PATK", new BigDecimal("100"));
            s.fixed.CRIT = BigDecimal.ZERO;
            s.fixed.CRITD = BigDecimal.ONE;   // 弱点打击中性化（同上）
            s.life.curHP = new BigDecimal("5000");
            s.life.maxHP = new BigDecimal("100000");
            s.life.curMP = Num.of(100);
            GameService.castSkill(s, "bloodlust");   // 吸血 +20%
            GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("10"), 1.0);
        }
        // 伤害 1000 → 吸血 200；诅咒减半为 100 → 诅咒侧 curHP 应恰少 100
        assertEquals(0, plain.life.curHP.subtract(cursed.life.curHP)
                        .compareTo(new BigDecimal("100")),
                "诅咒应使吸血减半：普通=" + plain.life.curHP + " 诅咒=" + cursed.life.curHP);
    }
}
