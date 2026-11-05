package com.gongpu.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 第十三批稳健：传承/扫荡/新词条计数往返、MobState.phantomCount 往返、旧档缺字段兜底。 */
class CodecBatch13Test {

    @Test
    void batch13StatsRoundTrip() {
        PlayerState s = NewState.create();
        s.stats.inherits = 12;
        s.stats.sweeps = 3456;
        s.stats.toughKills = 21;
        s.stats.phantomKills = 22;
        s.stats.eaterKills = 23;
        PlayerState back = StateCodec.decode(StateCodec.encode(s));
        assertEquals(12, back.stats.inherits);
        assertEquals(3456, back.stats.sweeps);
        assertEquals(21, back.stats.toughKills);
        assertEquals(22, back.stats.phantomKills);
        assertEquals(23, back.stats.eaterKills);
    }

    @Test
    void phantomCountRoundTrip() {
        PlayerState s = NewState.create();
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "taichu_beast";
        m.name = "幻影怪";
        m.trait = "phantom";
        m.phantomCount = 3;
        s.mob = m;
        PlayerState back = StateCodec.decode(StateCodec.encode(s));
        assertEquals(3, back.mob.phantomCount, "幻影计帧数应往返");
    }

    @Test
    void oldSaveDefaultsToZero() throws Exception {
        // 模拟旧档：stats 里没有第十三批字段
        String json = StateCodec.encode(NewState.create());
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(json);
        var stats = (com.fasterxml.jackson.databind.node.ObjectNode) node.get("stats");
        stats.remove("inherits");
        stats.remove("sweeps");
        stats.remove("toughKills");
        stats.remove("phantomKills");
        stats.remove("eaterKills");
        PlayerState back = StateCodec.decode(mapper.writeValueAsString(node));
        assertEquals(0, back.stats.inherits);
        assertEquals(0, back.stats.sweeps);
        assertEquals(0, back.stats.toughKills);
        assertEquals(0, back.stats.phantomKills);
        assertEquals(0, back.stats.eaterKills);
    }
}
