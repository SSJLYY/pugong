package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第九批·无相战装第八套（第 16 轮）。 */
class VoidSetTest {

    private static PlayerState.Item item(String code) {
        PlayerState.Item it = new PlayerState.Item();
        it.code = code;
        it.name = code;
        it.slot = "weapon";
        return it;
    }

    @Test
    void eightSetsAndVoidMembers() {
        assertEquals(8, Sets.ALL.size());
        Sets.SetDef d = Sets.ALL.stream().filter(x -> x.id().equals("voidset")).findFirst().orElseThrow();
        assertEquals(4, d.members().size());
        assertTrue(d.members().contains("void_blade"));
    }

    @Test
    void twoPieceBonusApplies() {
        PlayerState s = NewState.create();
        s.equipped.put("weapon", item("void_blade"));
        s.equipped.put("head", item("void_hood"));
        Map<String, BigDecimal> bonus = Sets.activeBonus(s);
        assertEquals(0, new BigDecimal("220").compareTo(bonus.get("PATK")));
    }
}
