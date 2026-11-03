package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import com.gongpu.state.StateCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
