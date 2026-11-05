package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 第八批第 87 轮：天赋·宝石亲和——镶嵌宝石 flat 加成 ×1.25（红宝石物攻 60 → 75）；
 * 无此天赋时原值。天赋池扩至 11。
 */
class GemAffinityTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState withGemWeapon() {
        PlayerState s = NewState.create();
        PlayerState.Item it = new PlayerState.Item();
        it.code = "iron_sword";
        it.name = "铁剑";
        it.slot = "weapon";
        it.gems = new java.util.ArrayList<>(java.util.List.of("gem_ruby"));
        s.equipped.put("weapon", it);
        return s;
    }

    @Test
    void affinityMultipliesGemStats() {
        PlayerState plain = withGemWeapon();
        // 基线 PATK 2（Life 默认）+ 红宝石 60
        assertEquals(0, PanelCalculator.of(plain).PATK().compareTo(Num.of(62)));
        PlayerState aff = withGemWeapon();
        aff.talent = "gem_affinity";
        // 60 × 1.25 = 75 → 2 + 75
        assertEquals(0, PanelCalculator.of(aff).PATK().compareTo(Num.of(77)));
        assertEquals(16, GameConst.TALENTS.size(), "天赋总数应为 11");
    }
}
