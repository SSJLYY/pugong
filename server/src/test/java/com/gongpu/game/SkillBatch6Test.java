package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 第六批技能扩充（第 25~30 轮）逐轮断言。
 */
class SkillBatch6Test {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void round25DiamondBody() {
        PlayerState s = NewState.create();
        s.life.set("PDEF", Num.of(100));
        s.life.set("MDEF", Num.of(100));
        PanelCalculator.Panel p0 = PanelCalculator.of(s);
        s.passives.add("diamond_body");
        PanelCalculator.Panel p1 = PanelCalculator.of(s);
        assertEquals(0, p1.PDEF().compareTo(Num.mul(p0.PDEF(), Num.of(1.15))), "金刚体物防应 ×1.15");
        assertEquals(0, p1.MDEF().compareTo(Num.mul(p0.MDEF(), Num.of(1.15))), "金刚体魔防应 ×1.15");
    }

    @Test
    void round26LuckyStar() {
        PlayerState s = NewState.create();
        assertEquals(1.0, GameEngine.dropLuckMul(s), 1e-9, "无被动无药剂时乘区应为 1");
        s.passives.add("lucky_star");
        assertEquals(1.1, GameEngine.dropLuckMul(s), 1e-9, "幸运星应 +10%");
        s.buffs.put("luck_pot", 300.0);
        assertEquals(1.65, GameEngine.dropLuckMul(s), 1e-9, "与鸿运药剂应乘算 1.5×1.1");
        s.buffs.put("silence", 5.0);
        assertEquals(1.5, GameEngine.dropLuckMul(s), 1e-9, "沉默时被动失效、药剂照旧");
    }

    @Test
    void round27EagleBone() {
        PlayerState s = NewState.create();
        java.math.BigDecimal base = PanelCalculator.of(s).RNG();
        s.passives.add("eagle_bone");
        assertEquals(0, PanelCalculator.of(s).RNG().subtract(base).compareTo(Num.of(1)),
                "鹰骨应让射程 +1");
    }

    @Test
    void round28HealSkill() {
        PlayerState s = NewState.create();
        s.life.maxHP = Num.of(10000);
        s.life.curHP = Num.of(1000);
        s.life.curMP = Num.of(100);
        GameService.castSkill(s, "heal");
        assertEquals(0, s.life.curHP.compareTo(Num.of(4500)),
                "治愈术应回 35% 上限（3500），实际=" + s.life.curHP);
        assertEquals(0, s.life.curMP.compareTo(Num.of(60)), "应扣 40 蓝");
    }

    @Test
    void round29Bloodlust() {
        PlayerState s = NewState.create();
        s.life.curMP = Num.of(100);
        java.math.BigDecimal base = PanelCalculator.of(s).LIFESTEAL();
        GameService.castSkill(s, "bloodlust");
        assertEquals(0, PanelCalculator.of(s).LIFESTEAL().subtract(base).compareTo(Num.of(0.20)),
                "嗜血术应让吸血 +20%");
    }

    @Test
    void round30IronWall() {
        PlayerState s = NewState.create();
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "boar";
        m.name = "野猪";
        m.maxHP = Num.of(100000);
        m.curHP = Num.of(100000);
        m.PATK = Num.of(100);
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        m.ASPD = 1;
        m.RNG = 1;
        m.SPD = 10;
        s.mob = m;
        s.zone = "plain";
        s.fixed.EVA = java.math.BigDecimal.ZERO;
        s.fixed.RNG = Num.of(1);
        s.fixed.SPD = Num.of(10);
        s.life.set("PDEF", Num.ZERO);
        s.life.set("MDEF", Num.ZERO);
        s.life.maxHP = Num.of(10000);
        s.life.curHP = Num.of(10000);
        s.life.curMP = Num.of(100);
        GameService.castSkill(s, "iron_wall");
        GameEngine.mobAttack(s, PanelCalculator.of(s), 1.0);
        assertEquals(0, s.life.curHP.compareTo(Num.of(9950)), "铁壁应把 100 伤减半为 50，实际余血=" + s.life.curHP);
        assertEquals(26, GameConst.ACTIVE_SKILLS.size() + GameConst.PASSIVE_SKILLS.size(),
                "技能总数应达 26（25 + 处决者）");
    }
}
