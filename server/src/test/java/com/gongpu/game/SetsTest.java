package com.gongpu.game;

import com.gongpu.cfg.AffixTemplateRepository;
import com.gongpu.cfg.BuffTemplateRepository;
import com.gongpu.cfg.CraftTemplateRepository;
import com.gongpu.cfg.DropRateRuleRepository;
import com.gongpu.cfg.EquipmentTemplate;
import com.gongpu.cfg.EquipmentTemplateRepository;
import com.gongpu.cfg.GameData;
import com.gongpu.cfg.ItemTemplateRepository;
import com.gongpu.cfg.SkillTemplateRepository;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 第七批第 2 轮：套装定义与件数判定——带 code 直判、旧档按名回退、背包不计、2/4 档加成叠加。 */
class SetsTest {

    private static EquipmentTemplate tpl(String code, String name, String slot) {
        EquipmentTemplate t = new EquipmentTemplate();
        t.setCode(code);
        t.setName(name);
        t.setEmoji("🎽");
        t.setSlot(slot);
        t.setTier(0);
        t.setMinZone("plain");
        t.setBaseStats("{}");
        t.setSellBase(20);
        t.setEnhanceMax(10);
        t.setRemark("");
        t.setEnabled(true);
        t.setSortNo(1);
        return t;
    }

    @BeforeAll
    static void initGameData() {
        EquipmentTemplateRepository eq = mock(EquipmentTemplateRepository.class);
        when(eq.findByEnabledTrueOrderBySortNoAscIdAsc()).thenReturn(List.of(
                tpl("leather_cap", "皮帽", "head"),
                tpl("leather_armor", "皮甲", "chest"),
                tpl("leather_pants", "皮裤", "legs"),
                tpl("straw_sandals", "草鞋", "boots")));
        AffixTemplateRepository af = mock(AffixTemplateRepository.class);
        when(af.findByEnabledTrueOrderBySortNoAscIdAsc()).thenReturn(List.of());
        ItemTemplateRepository ir = mock(ItemTemplateRepository.class);
        when(ir.findByEnabledTrueOrderBySortNoAscIdAsc()).thenReturn(List.of());
        SkillTemplateRepository sk = mock(SkillTemplateRepository.class);
        when(sk.findByEnabledTrueOrderBySortNoAscIdAsc()).thenReturn(List.of());
        BuffTemplateRepository bf = mock(BuffTemplateRepository.class);
        when(bf.findByEnabledTrueOrderBySortNoAscIdAsc()).thenReturn(List.of());
        DropRateRuleRepository dr = mock(DropRateRuleRepository.class);
        when(dr.findByEnabledTrueOrderBySortNoAsc()).thenReturn(List.of());
        CraftTemplateRepository cr = mock(CraftTemplateRepository.class);
        when(cr.findByEnabledTrueOrderBySortNoAscIdAsc()).thenReturn(List.of());
        new GameData(eq, af, ir, sk, bf, dr, cr);
    }

    private static PlayerState.Item worn(String slot, String code, String name) {
        PlayerState.Item it = new PlayerState.Item();
        it.slot = slot;
        it.code = code;
        it.name = name;
        return it;
    }

    @Test
    void sixSetsOfFour() {
        assertEquals(10, Sets.ALL.size());
        for (Sets.SetDef d : Sets.ALL) {
            assertEquals(4, d.members().size(), d.id() + " 应为四件套");
        }
    }

    @Test
    void countsCodedPiecesAndStacksTiers() {
        PlayerState s = NewState.create();
        s.equipped.put("head", worn("head", "leather_cap", "皮帽"));
        s.equipped.put("chest", worn("chest", "leather_armor", "皮甲"));
        assertEquals(2, Sets.wornCounts(s).get("farwalker"));
        Map<String, BigDecimal> bonus = Sets.activeBonus(s);
        assertEquals(0, bonus.get("maxHP").compareTo(Num.of(150)), "2 件只激活一档");

        s.equipped.put("legs", worn("legs", "leather_pants", "皮裤"));
        s.equipped.put("boots", worn("boots", "straw_sandals", "草鞋"));
        assertEquals(4, Sets.wornCounts(s).get("farwalker"));
        bonus = Sets.activeBonus(s);
        assertEquals(0, bonus.get("maxHP").compareTo(Num.of(500)), "4 件两档叠加 150+350");
        assertEquals(0, bonus.get("REGEN").compareTo(Num.of(5)));
    }

    @Test
    void legacyItemsResolveByName() {
        PlayerState s = NewState.create();
        s.equipped.put("head", worn("head", null, "皮帽"));
        s.equipped.put("chest", worn("chest", null, "皮甲"));
        assertEquals(2, Sets.wornCounts(s).get("farwalker"), "旧档无 code 应按名回退命中套装");
    }

    @Test
    void bagAndNonSetItemsIgnored() {
        PlayerState s = NewState.create();
        s.equipBag.add(worn("head", "leather_cap", "皮帽"));
        s.equipped.put("weapon", worn("weapon", "lava_blade", "熔岩之刃"));
        assertEquals(0, Sets.wornCounts(s).get("farwalker"), "背包件不计件数");
        assertTrue(Sets.activeBonus(s).isEmpty(), "非套装件无加成");
    }
}
