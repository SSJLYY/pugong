package com.gongpu.game;

import com.gongpu.common.Num;
import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 自动管家 buff 常驻链(legacy autobot autoBurn/autoSkill 迁移)行为测试。
 * 经 GameEngine.advance 驱动(与生产同路径),验证开/关/条件门槛。
 */
class AutoBuffTickTest {

    @BeforeAll
    static void initGameData() {
        // null 仓库 → load() 内部失败回落空快照 → GameConst 兜底(与 EquipFactoryTest 同法)
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState combatReady() {
        PlayerState s = NewState.create();
        s.zone = "plain";
        GameEngine.spawnMob(s, "plain");
        s.dead = false;
        // advance 按 (now - lastTickAt) 结算,拉开 5 秒避免 seconds<=0 早退
        s.lastTickAt = System.currentTimeMillis() - 5000;
        return s;
    }

    @Test
    void autoBurnRecastsWhenBuffAbsentAndHpHigh() {
        PlayerState s = combatReady();
        s.autoBurn = true;
        // 满血(100%)>60% 门槛
        GameEngine.advance(s, System.currentTimeMillis(), 1.0, false);
        assertTrue(PanelCalculator.active(s, "burn"), "autoBurn 开+满血应自动点燃燃烧 buff");
        assertTrue(s.mastery.containsKey("burn"), "自动施放也应累计熟练度(与手动一致)");
    }

    @Test
    void autoBurnSkipsWhenHpLow() {
        PlayerState s = combatReady();
        s.autoBurn = true;
        s.life.curHP = Num.of(30);   // 30% < 60% 门槛
        GameEngine.advance(s, System.currentTimeMillis(), 1.0, false);
        assertFalse(PanelCalculator.active(s, "burn"), "血量低于 60% 不应自动燃烧(防烧死)");
    }

    @Test
    void autoBurnOffByDefault() {
        PlayerState s = combatReady();
        assertFalse(s.autoBurn, "autoBurn 默认关(legacy 语义:有风险)");
        assertFalse(s.autoSkill, "autoSkill 默认关(尊重玩家手操)");
        GameEngine.advance(s, System.currentTimeMillis(), 1.0, false);
        assertFalse(PanelCalculator.active(s, "burn"), "未开启时不应自动燃烧");
    }

    @Test
    void autoSkillCastsBuffsWhenEnabled() {
        PlayerState s = combatReady();
        s.autoSkill = true;
        s.life.curMP = Num.of(80);   // 满蓝
        GameEngine.advance(s, System.currentTimeMillis(), 1.0, false);
        assertTrue(PanelCalculator.active(s, "rapid"), "autoSkill 应自动补急速射击");
        assertTrue(PanelCalculator.active(s, "heavy"), "autoSkill 应自动补蓄力重击");
        // surge 需要蓝>50%:满蓝应已施放
        assertTrue(PanelCalculator.active(s, "surge"), "满蓝应自动补魔力涌动");
    }

    @Test
    void autoSkillSkipsInVillage() {
        PlayerState s = NewState.create();   // 村口
        s.autoSkill = true;
        s.lastTickAt = System.currentTimeMillis() - 5000;
        GameEngine.advance(s, System.currentTimeMillis(), 1.0, false);
        assertFalse(PanelCalculator.active(s, "rapid"), "村口(安全区)不应自动施放(legacy 语义)");
    }

    @Test
    void autoBuffSkipsWhenSilenced() {
        PlayerState s = combatReady();
        s.autoSkill = true;
        s.autoBurn = true;
        s.buffs.put("silence", 10.0);
        s.lastTickAt = System.currentTimeMillis() - 5000;
        GameEngine.advance(s, System.currentTimeMillis(), 1.0, false);
        assertFalse(PanelCalculator.active(s, "rapid"), "沉默期间增益全失效,不应浪费蓝自动补");
    }
}
