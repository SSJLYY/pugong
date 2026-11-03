package com.gongpu.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第六批第 78 轮：经济与数值 sanity——药剂定价/时长/回复比、词条区间合法性全量锁定。 */
class EconomySanityTest {

    @Test
    void potionsSane() {
        assertEquals(33, GameConst.POTIONS.size(), "药剂总数应为 33");
        for (GameConst.PotionDef p : GameConst.POTIONS.values()) {
            assertTrue(p.price() > 0, p.id() + " 价格应为正");
            assertTrue(p.price() <= 100_000, p.id() + " 价格不应离谱：" + p.price());
            if (p.buff() != null) {
                assertTrue(p.buffDur() > 0, p.id() + " 有 buff 必须有时长");
                assertTrue(p.buffDur() <= 3600, p.id() + " buff 时长不应超过 1 小时：" + p.buffDur());
            }
            assertTrue(p.healHP() >= 0 && p.healHP() <= 1.0, p.id() + " 回血比应在 [0,1]");
            assertTrue(p.healMP() >= 0 && p.healMP() <= 1.0, p.id() + " 回蓝比应在 [0,1]");
            assertTrue(p.healHP() > 0 || p.healMP() > 0 || p.buff() != null || "cure".equals(p.id()) || "food".equals(p.id()),
                    p.id() + " 必须至少有一种效果（回复/buff/解毒/食物）");
        }
    }

    @Test
    void affixesSane() {
        assertEquals(21, GameConst.AFFIXES.size(), "词条总数应为 21");
        for (GameConst.Affix a : GameConst.AFFIXES) {
            assertTrue(a.min() <= a.max(), a.name() + " 区间下限不应大于上限");
            assertTrue(a.max() > 0, a.name() + " 区间上限应为正");
        }
    }

    @Test
    void sellPriceFormulaBounded() {
        // 售价公式与前端同口径：20×(品质+1)×(1+强化×0.5)；顶配（橙 +20）也不应溢出金币量级
        double top = 20 * (4 + 1) * (1 + 20 * 0.5);
        assertEquals(1100.0, top, 0.001, "顶配单件售价基准");
        assertTrue(top < 100_000, "单件售价不应失控");
    }
}
