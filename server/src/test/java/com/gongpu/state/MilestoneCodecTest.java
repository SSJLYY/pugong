package com.gongpu.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第八批第 78 轮：里程碑已发掩码编解码往返——重启后不能重发已领档位的奖金。 */
class MilestoneCodecTest {

    @Test
    void achMilestonePaidSurvivesRoundTrip() {
        PlayerState s = NewState.create();
        s.stats.achMilestonePaid = 0b0101;
        PlayerState back = StateCodec.decode(StateCodec.encode(s));
        assertEquals(0b0101, back.stats.achMilestonePaid);
    }
}
