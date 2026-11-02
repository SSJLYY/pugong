package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 成长路径(GameEngine.grow)纯逻辑:
 * might 天赋只 ×1.10 面板物攻、不改赋能步长;growth_pot 药剂成长 ×2;
 * 村口 ×2 / 非村口无加成,且与药剂乘区相乘。
 */
class GrowthPathTest {

    @BeforeAll
    static void initGameData() {
        // grow/NewState 不查 DB,统一按项目惯例初始化空快照
        new GameData(null, null, null, null, null, null, null);
    }

    // ==================== might 天赋 ====================

    /**
     * 天生神力(物攻 +10%)作用域审查:只进面板乘区(pct PATK+0.10),
     * 不进 grow 的赋能步长(blessStep 只看基础步长×(1+强化等级))。
     * 即:同样的拳数,有/无 might 的 PATK 赋能增量完全相同。
     */
    @Test
    void mightTalentBoostsPanelPatkButNotGrowthStep() {
        PlayerState might = NewState.create();
        might.talent = "might";
        PlayerState plain = NewState.create();   // 未觉醒

        // 面板:基础 PATK 2 → might ×1.10 = 2.2
        assertEquals(0, PanelCalculator.of(might).PATK()
                        .compareTo(Num.mul(PanelCalculator.of(plain).PATK(), Num.of(1.10))),
                "might 面板物攻应为无天赋的 ×1.10");

        // 成长:同给 PATK 赋能,同村口打 10 拳,步长必须一致
        might.blessings.put("PATK", new PlayerState.Bless(0));
        plain.blessings.put("PATK", new PlayerState.Bless(0));
        GameEngine.grow(might, Num.of(10));
        GameEngine.grow(plain, Num.of(10));
        assertEquals(0, might.life.PATK.compareTo(plain.life.PATK),
                "might 不改赋能步长:两者 PATK 成长量必须相同");
        // 增量 = 10 拳 × 命中 0.95 × 村口 ×2 × 步长 1 = 19 → 2+19 = 21
        assertEquals(0, plain.life.PATK.compareTo(Num.of(21)),
                "无天赋增量应为 19(10×0.95×2),实际 " + Num.plain(plain.life.PATK));
        // HP 赋能同样不受 might 影响
        assertEquals(0, might.life.maxHP.compareTo(plain.life.maxHP), "HP 成长也不受 might 影响");
    }

    // ==================== growth_pot 药剂 ====================

    /** 修行药剂(growth_pot buff 存续期内)普攻成长 ×2,作用于全部赋能属性。 */
    @Test
    void growthPotDoublesGrowthGain() {
        PlayerState potted = NewState.create();
        potted.buffs.put("growth_pot", 120.0);
        PlayerState plain = NewState.create();

        GameEngine.grow(potted, Num.of(10));
        GameEngine.grow(plain, Num.of(10));

        BigDecimal baseGain = plain.life.maxHP.subtract(Num.of(100));
        assertTrue(baseGain.signum() > 0, "前置:无药剂时应有正成长");
        assertEquals(0, potted.life.maxHP.subtract(Num.of(100)).compareTo(baseGain.multiply(Num.of(2))),
                "growth_pot 应使 HP 成长翻倍");
        assertTrue(PanelCalculator.active(potted, "growth_pot"), "前置:药剂 buff 生效中");
    }

    /** 村口 ×2、非村口 ×1,且与 growth_pot 相乘:村口+药剂 = 4 倍基础步长。 */
    @Test
    void villageDoublesGrowthAndStacksWithGrowthPot() {
        PlayerState village = NewState.create();                     // 村口 ×2
        PlayerState wild = NewState.create();
        wild.zone = "plain";                                         // 非村口 ×1
        PlayerState villagePotted = NewState.create();               // 村口 ×2 + 药剂 ×2 = ×4
        villagePotted.buffs.put("growth_pot", 120.0);

        GameEngine.grow(village, Num.of(10));
        GameEngine.grow(wild, Num.of(10));
        GameEngine.grow(villagePotted, Num.of(10));

        BigDecimal wildGain = wild.life.maxHP.subtract(Num.of(100));
        assertTrue(wildGain.signum() > 0, "前置:平原也应有基础成长");
        assertEquals(0, village.life.maxHP.subtract(Num.of(100)).compareTo(wildGain.multiply(Num.of(2))),
                "村口成长应为平原的 ×2");
        assertEquals(0, villagePotted.life.maxHP.subtract(Num.of(100))
                        .compareTo(wildGain.multiply(Num.of(4))),
                "村口 + growth_pot 应为平原的 ×4(乘区叠乘)");
    }
}
