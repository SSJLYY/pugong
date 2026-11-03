package com.gongpu.web;

import com.gongpu.cfg.AffixTemplateRepository;
import com.gongpu.cfg.BuffTemplateRepository;
import com.gongpu.cfg.CraftTemplateRepository;
import com.gongpu.cfg.DropRateRuleRepository;
import com.gongpu.cfg.EquipmentTemplate;
import com.gongpu.cfg.EquipmentTemplateRepository;
import com.gongpu.cfg.GameData;
import com.gongpu.cfg.ItemTemplateRepository;
import com.gongpu.cfg.SkillTemplateRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 第七批第 7 轮：meta 套装图鉴——六套齐全、成员名按模板池解析、加成文案同源。 */
class MetaSetsTest {

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

    @Test
    @SuppressWarnings("unchecked")
    void metaCarriesSetCatalog() {
        Map<String, Object> meta = new MetaController(null, null).meta();
        List<Map<String, Object>> sets = (List<Map<String, Object>>) meta.get("sets");
        assertEquals(6, sets.size(), "meta 应下发六套图鉴");
        Map<String, Object> far = sets.stream()
                .filter(r -> "farwalker".equals(r.get("id"))).findFirst().orElseThrow();
        assertEquals(List.of("皮帽", "皮甲", "皮裤", "草鞋"), far.get("members"), "成员名应按模板池解析");
        assertEquals("生命上限 +150", far.get("bonus2"));
    }
}
