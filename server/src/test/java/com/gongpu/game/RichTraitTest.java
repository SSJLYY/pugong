package com.gongpu.game;

import com.gongpu.common.Num;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第五批第 3 轮：怪物词条「💰富饶」——击杀金币 ×3，森林（idx≥2）起入池。
 */
class RichTraitTest {

    @Test
    void goldMulOnlyForRich() {
        PlayerState.MobState m = new PlayerState.MobState();
        m.trait = "rich";
        assertEquals(0, GameEngine.traitGoldMul(m).compareTo(Num.of(3)), "富饶应 ×3");
        m.trait = "frenzy";
        assertEquals(0, GameEngine.traitGoldMul(m).compareTo(Num.of(1)), "其他词条不影响金币");
        m.trait = null;
        assertEquals(0, GameEngine.traitGoldMul(m).compareTo(Num.of(1)), "无词条 ×1");
    }

    @Test
    void poolMembershipByZone() {
        Set<String> plain = new HashSet<>();
        for (int i = 0; i < 4000; i++) {
            String t = GameConst.rollMobTrait("plain");
            if (t != null) {
                plain.add(t);
            }
        }
        assertFalse(plain.contains("rich"), "平原池不应有富饶");
        Set<String> forest = new HashSet<>();
        for (int i = 0; i < 6000; i++) {
            String t = GameConst.rollMobTrait("forest");
            if (t != null) {
                forest.add(t);
            }
        }
        assertTrue(forest.contains("rich"), "森林池应能掷出富饶");
        assertTrue(GameConst.MOB_TRAITS.containsKey("rich"), "词条定义应注册");
    }
}
