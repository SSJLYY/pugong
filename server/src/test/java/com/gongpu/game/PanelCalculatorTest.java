package com.gongpu.game;

import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 面板计算测试:成长叠加、装备词条乘区、被动技能、沉默、赋能步长。
 */
class PanelCalculatorTest {

    @Test
    void emptyStateHasBasePanel() {
        PlayerState s = NewState.create();
        PanelCalculator.Panel p = PanelCalculator.of(s);
        // 新号初始面板(README 口径):HP100/MP80/PATK2/PDEF2/REGEN2/ASPD2
        assertEquals(0, p.maxHP().compareTo(Num.of(100)));
        assertEquals(0, p.MP().compareTo(Num.of(80)));
        assertEquals(0, p.PATK().compareTo(Num.of(2)));
        assertEquals(0, p.ASPD().compareTo(Num.of(2)));
        assertFalse(p.silenced());
    }

    @Test
    void passiveMultipilers() {
        PlayerState s = NewState.create();
        s.passives.add("iron");     // 物防×1.2
        PanelCalculator.Panel p = PanelCalculator.of(s);
        BigDecimal base = Num.of(2);
        assertEquals(0, p.PDEF().compareTo(Num.mul(base, Num.of(1.2))), "iron 被动物防×1.2");
    }

    @Test
    void silencedDisablesPassives() {
        PlayerState s = NewState.create();
        s.passives.add("iron");
        s.buffs.put("silence", 10.0);
        PanelCalculator.Panel p = PanelCalculator.of(s);
        assertTrue(p.silenced(), "沉默减益生效时应标记 silenced");
        assertEquals(0, p.PDEF().compareTo(Num.of(2)), "沉默期间被动失效,物防回到基础值");
    }

    @Test
    void equipAffixFlatAndPct() {
        PlayerState s = NewState.create();
        PlayerState.Item it = new PlayerState.Item();
        it.base.put("PDEF", Num.of(10));
        PlayerState.AffixInst a = new PlayerState.AffixInst();
        a.type = "flat";
        a.key = "PDEF";
        a.val = Num.of(5);
        it.affixes.add(a);
        s.equipped.put("offhand", it);

        PanelCalculator.Panel p = PanelCalculator.of(s);
        assertEquals(0, p.PDEF().compareTo(Num.of(2 + 10 + 5)), "底子10+词条5+基础2=17");
    }

    @Test
    void enhanceBoostsBase() {
        PlayerState s = NewState.create();
        PlayerState.Item it = new PlayerState.Item();
        it.base.put("PDEF", Num.of(10));
        it.enhance = 10;   // +50% 底子
        s.equipped.put("offhand", it);
        PanelCalculator.Panel p = PanelCalculator.of(s);
        // 10 × (1 + 10×0.05) = 15,+基础 2 → 17
        assertEquals(0, p.PDEF().compareTo(Num.of(17)));
    }

    @Test
    void pctAffixMultiplies() {
        PlayerState s = NewState.create();
        PlayerState.Item it = new PlayerState.Item();
        PlayerState.AffixInst a = new PlayerState.AffixInst();
        a.type = "pct";
        a.key = "maxHP";
        a.val = new BigDecimal("0.5");
        it.affixes.add(a);
        s.equipped.put("necklace", it);
        PanelCalculator.Panel p = PanelCalculator.of(s);
        assertEquals(0, p.maxHP().compareTo(Num.of(150)), "maxHP+50% 词条 → 100×1.5");
    }

    @Test
    void blessStepScalesWithLevel() {
        PlayerState s = NewState.create();
        BigDecimal baseStep = PanelCalculator.blessStep(s, "HP");
        assertNotNull(baseStep);
        s.blessings.get("HP").lv = 3;
        BigDecimal boosted = PanelCalculator.blessStep(s, "HP");
        assertEquals(0, boosted.compareTo(Num.mul(baseStep, Num.of(4))), "强化 3 级 → 步长×(1+3)");
    }

    @Test
    void panelClampsCritAtBlessCap() {
        PlayerState s = NewState.create();
        // CRIT 有上限(BLESS_CAP):面板值必须被 min(cap) 钳住(赋能量满也不越界);
        // 封顶"置灰"判定移到 GameService.panelCapped(看面板最终值,行为测试见 BlessCapTest)
        BigDecimal cap = GameConst.BLESS_CAP.get("CRIT");
        assertNotNull(cap, "CRIT 应有上限定义");
        s.fixed.CRIT = Num.add(cap, Num.of(1));
        assertEquals(0, PanelCalculator.of(s).CRIT().compareTo(cap), "面板 CRIT 必须被钳在 BLESS_CAP");
    }

    @Test
    void nullBlessReturnsNull() {
        PlayerState s = NewState.create();
        assertNull(PanelCalculator.blessStep(s, "NO_SUCH"), "未赋能属性步长应为 null");
    }
}
