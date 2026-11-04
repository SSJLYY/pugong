package com.gongpu.web;

import com.gongpu.game.GameConst;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第六批第 49 轮：词条图鉴数据源——MOB_TRAITS 与图鉴条目数一致（防图鉴漏新词条）。 */
class MetaTraitsTest {

    @Test
    void traitCountMatchesLegendSource() {
        assertEquals(15, GameConst.MOB_TRAITS.size(), "词条总数应为 15（图鉴/图例同源 MOB_TRAITS）");
        for (Map.Entry<String, GameConst.TraitDef> e : GameConst.MOB_TRAITS.entrySet()) {
            assertEquals(e.getKey(), e.getValue().id(), "词条 id 与键一致：" + e.getKey());
            org.junit.jupiter.api.Assertions.assertFalse(e.getValue().desc().isBlank(),
                    "词条图鉴说明不能为空：" + e.getKey());
        }
    }
}
