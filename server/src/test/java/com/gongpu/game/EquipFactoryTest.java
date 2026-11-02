package com.gongpu.game;

import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 装备工厂测试:生成合法性、品质规则、词条洗炼锁定。
 * GameData 用 null 仓库构造 → load() 失败回落空快照 → 走 GameConst 内置兜底表,
 * 恰好覆盖"空库也能玩"的兜底路径。
 */
class EquipFactoryTest {

    @org.junit.jupiter.api.BeforeAll
    static void initGameData() {
        // 构造器 load() 因 null 仓库抛 RuntimeException 被内部捕获 → 空快照兜底
        new com.gongpu.cfg.GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fresh() {
        return NewState.create();
    }

    @Test
    void generateInUnsafeZone() {
        PlayerState s = fresh();
        PlayerState.Item it = EquipFactory.generate(s, "plain", false);
        assertNotNull(it, "平原(不安全区)必须能出货");
        assertTrue(it.uid >= 1, "uid 应从 1 递增");
        assertNotNull(it.slot);
        assertNotNull(it.base);
        assertFalse(it.base.isEmpty(), "底子属性不能为空");
        // 词条数 ≤ 品质上限
        assertTrue(it.affixes.size() <= GameConst.QUALITY_AFFIX_COUNT[it.quality]);
    }

    @Test
    void generateInSafeZoneReturnsNull() {
        assertNull(EquipFactory.generate(fresh(), "village", false), "村口安全区不掉装备");
        assertNull(EquipFactory.generate(fresh(), "no_such_zone", false), "未知区域不掉装备");
    }

    @Test
    void bossDropQualityAtLeastRare() {
        PlayerState s = fresh();
        for (int i = 0; i < 30; i++) {
            PlayerState.Item it = EquipFactory.generate(s, "plain", true);
            assertNotNull(it);
            assertTrue(it.quality >= 2, "Boss 掉落必出稀有+，实际 " + it.quality);
        }
    }

    @Test
    void uidStrictlyIncreasing() {
        PlayerState s = fresh();
        Set<Integer> uids = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            PlayerState.Item it = EquipFactory.generate(s, "forest", false);
            if (it != null) {
                assertTrue(uids.add(it.uid), "uid 重复: " + it.uid);
            }
        }
        assertFalse(uids.isEmpty());
    }

    @Test
    void rerollKeepsLockedAffix() {
        PlayerState s = fresh();
        PlayerState.Item it = EquipFactory.generate(s, "plain", false);
        assertNotNull(it);
        assertTrue(it.affixes.size() >= 1, "测试前置:生成的装备至少 1 条词条");
        String lockedName = it.affixes.get(0).name;

        EquipFactory.rerollAffixes(it, 0);
        assertEquals(lockedName, it.affixes.get(0).name, "锁定词条必须原样保留在首位");
        // 锁定词条不在其余词条中重复出现(同名不重复)
        for (int i = 1; i < it.affixes.size(); i++) {
            assertNotEquals(lockedName, it.affixes.get(i).name);
        }
    }

    @Test
    void rerollWithoutLockChangesAll() {
        PlayerState s = fresh();
        PlayerState.Item it = EquipFactory.generate(s, "plain", true);  // Boss 必出 3~4 词条
        assertNotNull(it);
        int before = it.affixes.size();
        EquipFactory.rerollAffixes(it, -1);
        // 不锁定:词条数量应恢复品质档位
        assertEquals(GameConst.QUALITY_AFFIX_COUNT[it.quality], it.affixes.size());
        assertEquals(before, it.affixes.size());
    }

    @Test
    void costsScaleWithQualityAndEnhance() {
        PlayerState.Item white = new PlayerState.Item();
        white.quality = 0;
        white.enhance = 0;
        PlayerState.Item epic = new PlayerState.Item();
        epic.quality = 3;
        epic.enhance = 5;

        assertTrue(Num.gt(EquipFactory.rerollCost(epic), EquipFactory.rerollCost(white)),
                "高品高强洗炼必须更贵");
        assertTrue(Num.gt(EquipFactory.sellPrice(epic), EquipFactory.sellPrice(white)),
                "史诗卖价必须高于白装");
        assertTrue(Num.gt(EquipFactory.enhanceCost(epic), EquipFactory.enhanceCost(white)),
                "强化越贵越高级");
    }
}
