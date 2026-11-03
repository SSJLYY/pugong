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
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import com.gongpu.state.StateCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 第七批第 1 轮：装备模板 code 打标——掉落/合成生成的装备带模板 code（套装判定的地基），
 * 编解码往返不丢；旧档无 code 的装备解码为 null（由 Sets.codeOf 按名回退）。
 */
class ItemCodeTest {

    @BeforeAll
    static void initGameData() {
        EquipmentTemplate tpl = new EquipmentTemplate();
        tpl.setCode("lava_blade");
        tpl.setName("熔岩之刃");
        tpl.setEmoji("🗡️");
        tpl.setSlot("weapon");
        tpl.setTier(3);
        tpl.setMinZone("volcano");
        tpl.setBaseStats("{\"PATK\": 120}");
        tpl.setSellBase(200);
        tpl.setEnhanceMax(10);
        tpl.setRemark("");
        tpl.setEnabled(true);
        tpl.setSortNo(1);
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
    void dropStampsTemplateCode() {
        PlayerState s = NewState.create();
        PlayerState.Item it = EquipFactory.generate(s, "volcano", false);
        assertNotNull(it);
        assertEquals("lava_blade", it.code, "掉落装备应打标模板 code");
    }

    @Test
    void craftStampsTemplateCode() {
        PlayerState s = NewState.create();
        PlayerState.Item it = EquipFactory.generateCraft(s, "lava_blade", 2);
        assertNotNull(it);
        assertEquals("lava_blade", it.code, "合成装备应打标模板 code");
    }

    @Test
    void codecRoundTripKeepsCode() {
        PlayerState s = NewState.create();
        PlayerState.Item worn = new PlayerState.Item();
        worn.uid = 1;
        worn.name = "熔岩之刃";
        worn.slot = "weapon";
        worn.code = "lava_blade";
        s.equipped.put("weapon", worn);
        PlayerState.Item bag = new PlayerState.Item();
        bag.uid = 2;
        bag.name = "皮甲";
        bag.slot = "chest";
        bag.code = "leather_armor";
        s.equipBag.add(bag);
        PlayerState.Item legacy = new PlayerState.Item();
        legacy.uid = 3;
        legacy.name = "旧木棍";
        legacy.slot = "weapon";
        s.equipBag.add(legacy);

        PlayerState back = StateCodec.decode(StateCodec.encode(s));
        assertEquals("lava_blade", back.equipped.get("weapon").code);
        assertEquals("leather_armor", back.equipBag.get(0).code);
        assertNull(back.equipBag.get(1).code, "旧档无 code 装备应保持 null（回退查名）");
    }
}
