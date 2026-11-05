package com.gongpu.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十二批稳健：zoneKills 往返/兜底过滤、三阶融合计数往返、MobState 新字段往返。 */
class CodecBatch12Test {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void zoneKillsRoundTrip() {
        PlayerState s = NewState.create();
        s.stats.zoneKills.put("guixu", 4321);
        s.stats.zoneKills.put("plain", 500);
        s.stats.gemTier3Fused = 3;
        s.stats.bellKills = 7;
        s.stats.corrodeKills = 8;
        s.stats.thunderKills = 9;
        PlayerState back = StateCodec.decode(StateCodec.encode(s));
        assertEquals(4321, back.stats.zoneKills.get("guixu"));
        assertEquals(500, back.stats.zoneKills.get("plain"));
        assertEquals(3, back.stats.gemTier3Fused);
        assertEquals(7, back.stats.bellKills);
        assertEquals(8, back.stats.corrodeKills);
        assertEquals(9, back.stats.thunderKills);
    }

    @Test
    void zoneKillsBadKeysPruned() throws Exception {
        PlayerState s = NewState.create();
        String json = StateCodec.encode(s);
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.readTree(json);
        var stats = (com.fasterxml.jackson.databind.node.ObjectNode) node.get("stats");
        var zk = MAPPER.createObjectNode();
        zk.put("guixu", 100);
        zk.put("not_a_zone", 50);
        zk.put("chaos", -3);
        stats.set("zoneKills", zk);
        PlayerState back = StateCodec.decode(MAPPER.writeValueAsString(node));
        assertEquals(100, back.stats.zoneKills.get("guixu"));
        assertFalse(back.stats.zoneKills.containsKey("not_a_zone"), "未知区域键应被剔除");
        assertEquals(0, back.stats.zoneKills.get("chaos"), "负数应钳 0");
    }

    @Test
    void legacySaveWithoutZoneKillsDecodes() throws Exception {
        PlayerState s = NewState.create();
        String json = StateCodec.encode(s);
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.readTree(json);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node.get("stats")).remove("zoneKills");
        PlayerState back = StateCodec.decode(MAPPER.writeValueAsString(node));
        assertTrue(back.stats.zoneKills != null && back.stats.zoneKills.isEmpty(), "旧档缺 zoneKills 应兜底为空表");
    }

    @Test
    void mobStateNewFieldsRoundTrip() {
        PlayerState s = NewState.create();
        PlayerState.MobState m = new PlayerState.MobState();
        m.bellUsed = true;
        m.corrodeStacks = 6;
        s.mob = m;
        PlayerState back = StateCodec.decode(StateCodec.encode(s));
        assertTrue(back.mob.bellUsed);
        assertEquals(6, back.mob.corrodeStacks);
    }
}
