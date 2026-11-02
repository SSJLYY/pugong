package com.gongpu.state;

import com.gongpu.common.Num;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 旧档 pct 词条迁移(v3.9 一次性转换)的数值语义锁。
 * <p>转换入口是 decode→normalize 内的私有 convertLegacyPctAffixes,与生产同链路走
 * encode→decode 全程验证(同 DataConsistencyTest 手法);此处聚焦<b>乘算键的线性映射数值</b>,
 * 与 DataConsistencyTest 的"表覆盖全 key"/"率型键保值"互补,不重复。</p>
 * <p>映射公式:t = clamp((v-旧min)/(旧max-旧min), 0, 1);新值 = round((新min + t×(新max-新min))×100)/100。</p>
 */
class StateCodecMigrationTest {

    /** 旧档形态的 pct 词条。 */
    private PlayerState.AffixInst pctAffix(String key, double val) {
        PlayerState.AffixInst a = new PlayerState.AffixInst();
        a.name = key + "旧词条";
        a.key = key;
        a.type = "pct";
        a.val = Num.of(val);
        return a;
    }

    private PlayerState.Item itemWith(PlayerState.AffixInst... affixes) {
        PlayerState.Item it = new PlayerState.Item();
        it.uid = 1;
        it.name = "旧档遗物";
        it.emoji = "🗡️";
        it.slot = "weapon";
        it.affixes = new ArrayList<>();
        for (PlayerState.AffixInst a : affixes) {
            it.affixes.add(a);
        }
        return it;
    }

    private PlayerState roundtrip(PlayerState s) {
        return StateCodec.decode(StateCodec.encode(s));
    }

    /** 乘算键中点映射:PATK 旧区间 0.05~0.20 的中点 0.125 → 新区间 4~16 的中点 10。 */
    @Test
    void multiplicativeKeyMidpointMapsLinearly() {
        PlayerState s = NewState.create();
        s.equipBag.add(itemWith(pctAffix("PATK", 0.125)));

        PlayerState back = roundtrip(s);
        PlayerState.AffixInst a = back.equipBag.get(0).affixes.get(0);
        assertEquals("flat", a.type, "乘算键迁移后必须是 flat 直接数值词条");
        assertEquals(0, a.val.compareTo(new BigDecimal("10")),
                "PATK 0.125(旧区间中点)应线性映射到新区间 4~16 的中点 10,实际 " + a.val.toPlainString());

        // 幂等:flat 不再进转换分支,再走一轮往返数值纹丝不动
        PlayerState back2 = roundtrip(back);
        assertEquals("flat", back2.equipBag.get(0).affixes.get(0).type);
        assertEquals(0, back2.equipBag.get(0).affixes.get(0).val.compareTo(new BigDecimal("10")),
                "迁移结果必须幂等(二次解码不得再映射)");
    }

    /** 越界钳制:超旧区间上限钳到新 max,低于旧下限钳到新 min,不得外溢。 */
    @Test
    void outOfRangeValuesClampedToNewBounds() {
        PlayerState s = NewState.create();
        s.equipBag.add(itemWith(pctAffix("PATK", 0.9)));    // 远超旧上限 0.20
        s.equipBag.add(itemWith(pctAffix("PATK", -1)));     // 低于旧下限 0.05

        PlayerState back = roundtrip(s);
        assertEquals(0, back.equipBag.get(0).affixes.get(0).val.compareTo(new BigDecimal("16")),
                "超旧上限应钳到新区间 max(PATK=16),实际 " + back.equipBag.get(0).affixes.get(0).val);
        assertEquals(0, back.equipBag.get(1).affixes.get(0).val.compareTo(new BigDecimal("4")),
                "低于旧下限应钳到新区间 min(PATK=4),实际 " + back.equipBag.get(1).affixes.get(0).val);
    }

    /** 已穿戴(equipped)与背包(equipBag)两处旧词条都要被转换,漏一处=半档玩家。 */
    @Test
    void equippedAndEquipBagBothConverted() {
        PlayerState s = NewState.create();
        s.equipped.put("weapon", itemWith(pctAffix("MATK", 0.125)));   // 中点 → 6~24 中点 15
        s.equipBag.add(itemWith(pctAffix("REGEN", 0.125)));            // 中点 → 1~4 中点 2.5

        PlayerState back = roundtrip(s);
        PlayerState.AffixInst worn = back.equipped.get("weapon").affixes.get(0);
        PlayerState.AffixInst bagged = back.equipBag.get(0).affixes.get(0);
        assertEquals("flat", worn.type, "已穿戴装备的旧词条同样要转换");
        assertEquals(0, worn.val.compareTo(new BigDecimal("15")),
                "MATK 0.125 应映射到 15,实际 " + worn.val.toPlainString());
        assertEquals("flat", bagged.type, "背包装备的旧词条同样要转换");
        assertEquals(0, bagged.val.compareTo(new BigDecimal("2.5")),
                "REGEN 0.125 应映射到 2.5,实际 " + bagged.val.toPlainString());
    }

    /** 重甲副作用字段(sideKey=SPD/sideVal=-0.03)迁移后必须原样保留(丢掉=移速惩罚蒸发)。 */
    @Test
    void heavySideEffectFieldsPreserved() {
        PlayerState s = NewState.create();
        PlayerState.Item it = itemWith(pctAffix("PDEF", 0.175));   // PDEF 旧区间中点 → 6~20 中点 13
        it.affixes.get(0).name = "重甲";
        it.affixes.get(0).sideKey = "SPD";
        it.affixes.get(0).sideVal = Num.of(-0.03);
        s.equipBag.add(it);

        PlayerState back = roundtrip(s);
        PlayerState.AffixInst a = back.equipBag.get(0).affixes.get(0);
        assertEquals("flat", a.type);
        assertEquals(0, a.val.compareTo(new BigDecimal("13")),
                "PDEF 0.175(旧区间中点)应映射到 13,实际 " + a.val.toPlainString());
        assertEquals("SPD", a.sideKey, "副作用键迁移后必须保留");
        assertEquals(0, a.sideVal.compareTo(Num.of(-0.03)),
                "副作用值(-3% 移速)迁移后必须保留");
    }

    /** 无词条/纯 flat 词条的装备不参与转换,原样往返(空档不炸、flat 不被二次改写)。 */
    @Test
    void itemsWithoutPctAffixesPassThroughUntouched() {
        PlayerState s = NewState.create();
        PlayerState.Item plain = new PlayerState.Item();
        plain.name = "白板装";
        plain.slot = "weapon";
        plain.base.put("PATK", Num.of(5));
        s.equipBag.add(plain);
        PlayerState.AffixInst flatAffix = new PlayerState.AffixInst();
        flatAffix.name = "锋利";
        flatAffix.key = "PATK";
        flatAffix.type = "flat";
        flatAffix.val = Num.of(10);
        s.equipBag.add(itemWith(flatAffix));

        PlayerState back = roundtrip(s);
        assertTrue(back.equipBag.get(0).affixes.isEmpty(), "无词条装备应原样往返");
        assertEquals(0, back.equipBag.get(0).base.get("PATK").compareTo(Num.of(5)), "底子数值不受迁移影响");
        PlayerState.AffixInst flat = back.equipBag.get(1).affixes.get(0);
        assertEquals("flat", flat.type, "已是 flat 的词条不进转换分支");
        assertEquals(0, flat.val.compareTo(Num.of(10)), "flat 词条数值原样保留");
    }
}
