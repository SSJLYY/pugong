package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.ApiException;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第八批第 43 轮：一键穿套装——按成员 code 从背包挑评分最高件逐件换装，
 * 已穿成员跳过、缺件如实上报；套装加成随之激活。
 */
class EquipSetTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState.Item item(String code, String slot, int quality) {
        PlayerState.Item it = new PlayerState.Item();
        it.code = code;
        it.name = code;
        it.slot = slot;
        it.quality = quality;
        return it;
    }

    @Test
    void equipsAllAvailableMembers() {
        PlayerState s = NewState.create();
        GameService g = new GameService();
        s.equipBag.add(item("star_blade", "weapon", 3));
        s.equipBag.add(item("nebula_helm", "head", 3));
        s.equipBag.add(item("galaxy_plate", "chest", 2));
        // meteor_greaves 缺件
        Map<String, Object> r = g.equipSet(s, "starset");
        assertEquals(3, r.get("equipped"));
        assertEquals(java.util.List.of("meteor_greaves"), r.get("missing"));
        assertEquals("star_blade", Sets.codeOf(s.equipped.get("weapon")));
        // 3 件未达 4 件档，但 2 件档已激活：面板含套装加成（PATK 基础 2 + 120）
        assertTrue(Sets.activeBonus(s).containsKey("PATK"), "2 件套加成应激活");
    }

    @Test
    void skipsAlreadyWornAndRejectsUnknownSet() {
        PlayerState s = NewState.create();
        GameService g = new GameService();
        PlayerState.Item worn = item("star_blade", "weapon", 3);
        s.equipped.put("weapon", worn);
        Map<String, Object> r = g.equipSet(s, "starset");
        assertEquals(1, r.get("equipped"), "已穿的星陨之刃应计件且不重复换装");
        assertTrue(s.equipped.get("weapon") == worn, "已穿件不应被换下");
        assertThrows(ApiException.class, () -> g.equipSet(s, "nope"), "未知套装应拒绝");
    }


    @Test
    void voidSetEquipsAndMissingReported() {
        PlayerState s = NewState.create();
        GameService g = new GameService();
        s.equipBag.add(item("void_blade", "weapon", 3));
        s.equipBag.add(item("void_hood", "head", 3));
        Map<String, Object> r = g.equipSet(s, "voidset");
        assertEquals(2, r.get("equipped"));
        assertEquals(java.util.List.of("void_robe", "void_treads"), r.get("missing"));
        // 无相 2 件档：双攻 +220
        assertEquals(0, new java.math.BigDecimal("220").compareTo(Sets.activeBonus(s).get("PATK")));
    }
}
