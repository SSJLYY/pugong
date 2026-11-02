package com.gongpu.state;

import com.gongpu.common.Num;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据一致性回归锁(2026-10-31 R2):
 * 死亡重开/转生共用的重置语义 + 旧档 pct 词条迁移表覆盖。
 */
class DataConsistencyTest {

    @Test
    void resetForNewLifeClearsPotionPriceLadder() {
        // 此前全工程无清理点:买涨计数跨世累积,药价很快永久钉死 2 倍封顶
        PlayerState s = NewState.create();
        s.potionBought.put("hp_m", 100);
        s.potionBought.put("mp", 50);
        SaveService.resetForNewLife(s);
        assertTrue(s.potionBought.isEmpty(), "药价阶梯必须每世重置");
    }

    @Test
    void resetForNewLifeClearsRecentKills() {
        // 尸检击杀序列只属于当世,跨世残留会让尸检报出上一世的死因线索
        PlayerState s = NewState.create();
        s.recentKills = new ArrayList<>();
        s.recentKills.add("00:01 野猪");
        SaveService.resetForNewLife(s);
        assertTrue(s.recentKills.isEmpty(), "击杀序列必须每世清空");
    }

    @Test
    void legacyPctMapCoversAllAffixKeys() {
        // PanelCalculator 对率型/射程键只读 flat 分支:迁移表漏键 = 词条静默失效。
        // 表必须覆盖词条池全部 key(含防御性兜底),否则老玩家属性无声蒸发。
        java.util.Set<String> poolKeys = new java.util.HashSet<>();
        for (com.gongpu.game.GameConst.Affix a : com.gongpu.game.GameConst.AFFIXES) {
            poolKeys.add(a.key());
        }
        for (String k : poolKeys) {
            assertTrue(StateCodec.legacyPctMapHasKey(k),
                    "LEGACY_PCT_MAP 缺词条键 " + k + ":旧档该键 pct 词条会静默失效");
        }
    }

    @Test
    void legacyPctRateKeysKeepOriginalValue() {
        // 率型键 flat/pct 同一小数单位,迁移只翻 type,数值必须原样保留
        PlayerState s = NewState.create();
        PlayerState.Item it = new PlayerState.Item();
        it.affixes = new ArrayList<>();
        PlayerState.AffixInst a = new PlayerState.AffixInst();
        a.name = "致命";
        a.key = "CRIT";
        a.type = "pct";
        a.val = Num.of(0.15);
        it.affixes.add(a);
        s.equipBag.add(it);
        // 走 decode 的转换入口:直接构造 JSON 往返过于繁琐,这里调包内可见的转换器等价物
        // (convertLegacyPctAffixes 为 private,经 encode/decode 全链路验证幂等与保值)
        String json = StateCodec.encode(s);
        PlayerState back = StateCodec.decode(json);
        PlayerState.AffixInst converted = back.equipBag.get(0).affixes.get(0);
        assertEquals("flat", converted.type, "率型键 pct 应翻成 flat");
        assertEquals(0, converted.val.compareTo(Num.of(0.15)), "同单位键迁移必须保值");
        // 幂等:再走一遍不变
        PlayerState back2 = StateCodec.decode(StateCodec.encode(back));
        assertEquals("flat", back2.equipBag.get(0).affixes.get(0).type);
    }
}
