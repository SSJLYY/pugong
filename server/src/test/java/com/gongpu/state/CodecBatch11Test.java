package com.gongpu.state;

import com.gongpu.game.GameConst;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十一批稳健：进化集合/觉醒等级 codec 往返与钳制、新统计字段往返。 */
class CodecBatch11Test {

    @Test
    void roundTripKeepsBatch11Fields() throws Exception {
        PlayerState s = NewState.create();
        s.petsOwned.add("pet_cat");
        s.pet = "pet_cat";
        s.petLevels.put("pet_cat", 50);
        s.petEvolved.add("pet_cat");
        s.stats.petEvolves = 3;
        s.stats.immortalKills = 11;
        s.stats.devourKills = 12;
        s.stats.taixuKills = 13;
        s.stats.awakens = 7;
        PlayerState.Item it = new PlayerState.Item();
        it.name = "觉醒剑";
        it.slot = "weapon";
        it.star = 5;
        it.awaken = 2;
        it.base.put("PATK", new BigDecimal("100"));
        s.equipBag.add(it);

        String json = StateCodec.encode(s);
        PlayerState back = StateCodec.decode(json);
        assertTrue(back.petEvolved.contains("pet_cat"), "进化集合应往返");
        assertEquals(3, back.stats.petEvolves);
        assertEquals(11, back.stats.immortalKills);
        assertEquals(12, back.stats.devourKills);
        assertEquals(13, back.stats.taixuKills);
        assertEquals(7, back.stats.awakens);
        assertEquals(2, back.equipBag.get(0).awaken, "觉醒等级应往返");
    }

    @Test
    void normalizeClampsAwakenAndPrunesEvolved() throws Exception {
        PlayerState s = NewState.create();
        s.petsOwned.add("pet_cat");
        s.petEvolved.add("pet_cat");
        s.petEvolved.add("pet_qilin");      // 未领养 → 应剔除
        s.petEvolved.add("not_a_pet");      // 图鉴外 → 应剔除
        PlayerState.Item worn = new PlayerState.Item();
        worn.name = "甲";
        worn.slot = "chest";
        worn.awaken = 99;
        s.equipped.put("chest", worn);
        PlayerState.Item bag = new PlayerState.Item();
        bag.name = "剑";
        bag.slot = "weapon";
        bag.awaken = -3;
        s.equipBag.add(bag);

        PlayerState back = StateCodec.decode(StateCodec.encode(s));
        assertEquals(GameConst.AWAKEN_MAX_LV, back.equipped.get("chest").awaken, "觉醒 99 应钳到上限 3");
        assertEquals(0, back.equipBag.get(0).awaken, "觉醒 -3 应钳到 0");
        assertEquals(1, back.petEvolved.size(), "进化集合只保留合法已领养宠物");
        assertTrue(back.petEvolved.contains("pet_cat"));
    }

    @Test
    void legacySaveWithoutBatch11FieldsDecodes() throws Exception {
        PlayerState s = NewState.create();
        String json = StateCodec.encode(s);
        // 模拟旧档：删掉第十一批新字段后仍应正常解码（Jackson 忽略缺失即默认值）
        String legacy = json.replaceAll("\"petEvolved\":\\[[^]]*\\],?", "")
                .replaceAll("\"awaken\":\\d+,?", "");
        PlayerState back = StateCodec.decode(legacy);
        assertTrue(back.petEvolved != null && back.petEvolved.isEmpty(), "旧档进化集合应兜底为空集");
    }
}
