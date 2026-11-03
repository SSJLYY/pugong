package com.gongpu.web;

import com.gongpu.cfg.AffixTemplateRepository;
import com.gongpu.cfg.BuffTemplateRepository;
import com.gongpu.cfg.CraftTemplateRepository;
import com.gongpu.cfg.DropRateRuleRepository;
import com.gongpu.cfg.EquipmentTemplateRepository;
import com.gongpu.cfg.GameData;
import com.gongpu.cfg.ItemTemplateRepository;
import com.gongpu.cfg.SkillTemplateRepository;
import com.gongpu.config.StateCache;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** 第 2 轮：/config/meta 按 GameData 快照缓存——同快照重复请求复用同一 Map，不再全量重建。 */
class MetaControllerTest {

    @BeforeAll
    static void initGameData() {
        // 注册 GameData 单例（仓库全 mock，load 走空表快照）；meta() 的 zones/blessings 来自常量表
        new GameData(mock(EquipmentTemplateRepository.class), mock(AffixTemplateRepository.class),
                mock(ItemTemplateRepository.class), mock(SkillTemplateRepository.class),
                mock(BuffTemplateRepository.class), mock(DropRateRuleRepository.class),
                mock(CraftTemplateRepository.class));
    }

    @Test
    void metaIsCachedWithinSameSnapshot() {
        MetaController c = new MetaController(mock(StateCache.class), mock(DataSource.class));
        Map<String, Object> first = c.meta();
        Map<String, Object> second = c.meta();
        assertSame(first, second, "同一配置快照下应直接复用缓存实例");
        assertTrue(first.containsKey("zones"));
        assertTrue(first.containsKey("skills"));
        assertTrue(first.containsKey("potions"));
        assertTrue(first.containsKey("blessings"));
    }
}
