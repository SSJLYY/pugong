package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 第四批第 1 轮：回复系新药剂——特大血药（满血）、大蓝药（满蓝）、万灵药剂（双回 80%）。
 * usePotion 走 GameData.potion()，离线 GameData 无 DB 数据时回退 GameConst.POTIONS 镜像。
 */
class PotionsExpandTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void defsExistWithRightEffects() {
        GameConst.PotionDef xl = GameConst.POTIONS.get("hp_xl");
        assertNotNull(xl);
        assertEquals(1.0, xl.healHP());
        GameConst.PotionDef ml = GameConst.POTIONS.get("mp_l");
        assertNotNull(ml);
        assertEquals(1.0, ml.healMP());
        GameConst.PotionDef el = GameConst.POTIONS.get("elixir");
        assertNotNull(el);
        assertEquals(0.8, el.healHP());
        assertEquals(0.8, el.healMP());
    }

    @Test
    void hpXlFullyHeals() {
        PlayerState s = NewState.create();
        s.life.curHP = BigDecimal.ONE;
        s.potions.put("hp_xl", 1);
        new GameService().usePotion(s, "hp_xl");
        assertEquals(0, s.life.curHP.compareTo(PanelCalculator.of(s).maxHP()),
                "特大血药应回满");
        assertEquals(0, s.potions.getOrDefault("hp_xl", 0), "应扣 1 瓶");
    }

    @Test
    void elixirHealsBoth() {
        PlayerState s = NewState.create();
        s.life.curHP = BigDecimal.ONE;
        s.life.curMP = BigDecimal.ZERO;
        s.potions.put("elixir", 1);
        new GameService().usePotion(s, "elixir");
        var p = PanelCalculator.of(s);
        // 期望：HP = 1 + 80% maxHP；MP = 80% MP
        BigDecimal wantHp = BigDecimal.ONE.add(p.maxHP().multiply(new BigDecimal("0.8")));
        assertEquals(0, s.life.curHP.compareTo(wantHp), "生命应回 80% 上限");
        assertEquals(0, s.life.curMP.compareTo(
                p.MP().multiply(new BigDecimal("0.8"))), "蓝量应回 80%");
    }
}
