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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 第七批第 24 轮：深渊装备掉落——generate 按 min_zone 深度过滤，深渊装只在深渊掉、浅区不串池。 */
class AbyssDropTest {

    @BeforeAll
    static void initGameData() {
        EquipmentTemplate tpl = new EquipmentTemplate();
        tpl.setCode("abyss_blade");
        tpl.setName("渊噬之刃");
        tpl.setEmoji("🗡️");
        tpl.setSlot("weapon");
        tpl.setTier(4);
        tpl.setMinZone("abyss");
        tpl.setBaseStats("{\"PATK\": 260, \"CRITD\": 0.3}");
        tpl.setSellBase(900);
        tpl.setEnhanceMax(10);
        tpl.setRemark("");
        tpl.setEnabled(true);
        tpl.setSortNo(53);
        EquipmentTemplateRepository eq = mock(EquipmentTemplateRepository.class);
        when(eq.findByEnabledTrueOrderBySortNoAscIdAsc()).thenReturn(List.of(tpl));
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
    void abyssGearDropsInAbyss() {
        // tier 4 模板在 pickTpl 有 30% 放行率（高 tier 低概率出口径），单次 generate 可能回落硬编码底子；
        // 连抽 40 次必中模板（落空概率 0.7^40 ≈ 6e-7），中模板时校验打标与底子
        PlayerState s = NewState.create();
        PlayerState.Item hit = null;
        for (int i = 0; i < 40 && hit == null; i++) {
            PlayerState.Item it = EquipFactory.generate(s, "abyss", false);
            if (it != null && "abyss_blade".equals(it.code)) {
                hit = it;
            }
        }
        assertNotNull(hit, "深渊连抽 40 次应至少掉一件深渊装");
        assertEquals("abyss", hit.zone, "掉落装备应打来源区域标（分解/淬炼按区域取材）");
        assertEquals(0, hit.base.get("PATK").compareTo(Num.of(260)), "底子应来自模板 base_stats");
    }

    @Test
    void abyssGearDoesNotLeakIntoShallowerZones() {
        PlayerState s = NewState.create();
        // pickTpl 按 min_zone 深度过滤是确定性的：火山（idx 6）够不到 abyss（idx 7）模板；
        // 池空时 generate 回落硬编码底子——回落产物无模板 code，绝不会是深渊装
        for (int i = 0; i < 40; i++) {
            PlayerState.Item it = EquipFactory.generate(s, "volcano", false);
            assertTrue(it == null || !"abyss_blade".equals(it.code),
                    "min_zone=abyss 的装备不应在火山掉落（池子按深度过滤）");
        }
    }
}
