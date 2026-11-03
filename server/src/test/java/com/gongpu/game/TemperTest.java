package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.ApiException;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import com.gongpu.state.StateCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 第七批第 11 轮：淬炼等级字段落地——编解码往返保留，旧档默认 0。 */
class TemperTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void codecRoundTripKeepsTemper() {
        PlayerState s = NewState.create();
        PlayerState.Item it = new PlayerState.Item();
        it.uid = 1;
        it.name = "铁剑";
        it.slot = "weapon";
        it.temper = 3;
        s.equipBag.add(it);
        PlayerState.Item legacy = new PlayerState.Item();
        legacy.uid = 2;
        legacy.name = "木棍";
        legacy.slot = "weapon";
        s.equipBag.add(legacy);

        PlayerState back = StateCodec.decode(StateCodec.encode(s));
        assertEquals(3, back.equipBag.get(0).temper, "淬炼等级应随存档往返保留");
        assertEquals(0, back.equipBag.get(1).temper, "未淬炼装备应为 0");
    }

    // ---- 第 12 轮：淬炼服务规则 ----

    private final GameService game = new GameService();

    private static PlayerState.Item bagItem(String zone, int quality) {
        PlayerState.Item it = new PlayerState.Item();
        it.name = "测试装";
        it.slot = "weapon";
        it.zone = zone;
        it.quality = quality;
        return it;
    }

    @Test
    void temperDeductsZoneMatAndGold() {
        PlayerState s = NewState.create();
        s.equipBag.add(bagItem("mine", 1));
        s.materials.put("ore", 10);
        s.gold = Num.of(10_000);
        game.temper(s, 0);
        assertEquals(1, s.equipBag.get(0).temper);
        assertEquals(8, s.materials.get("ore"), "一级淬炼耗 2 个来源区域材料（矿洞=矿石）");
        assertEquals(0, s.gold.compareTo(Num.of(10_000 - 300 * 1 * 2)), "金币 300×级×(品质+1)");
        assertEquals(1, s.stats.tempers);
    }

    @Test
    void temperRejectsWhenMatOrGoldShortOrMaxed() {
        PlayerState s = NewState.create();
        s.equipBag.add(bagItem("plain", 0));
        s.gold = Num.of(10_000);
        assertThrows(ApiException.class, () -> game.temper(s, 0), "无兽皮应拒绝");
        s.materials.put("pelt", 100);
        s.gold = Num.ZERO;
        assertThrows(ApiException.class, () -> game.temper(s, 0), "无金币应拒绝");
        s.gold = Num.of(100_000);
        s.equipBag.get(0).temper = GameConst.TEMPER_MAX_LV;
        assertThrows(ApiException.class, () -> game.temper(s, 0), "满级应拒绝");
        assertThrows(ApiException.class, () -> game.temper(s, 5), "索引越界应拒绝");
    }

    @Test
    void temperLockedItemAllowed() {
        PlayerState s = NewState.create();
        PlayerState.Item it = bagItem("snow", 2);
        it.locked = true;
        s.equipBag.add(it);
        s.materials.put("mana_dust", 10);
        s.gold = Num.of(10_000);
        game.temper(s, 0);
        assertEquals(1, it.temper, "锁定件可淬炼（纯增益，锁定只防处置类操作）");
    }
}
