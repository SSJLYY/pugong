package com.gongpu.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 加点大数口径的线上兼容锁：StatPointReq.count 为 String，
 * 旧前端发的 JSON 数字与新前端发的字符串都必须能解（Jackson 对 String 字段兼容数字 token）。
 */
class StatPointReqJsonTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void acceptsNumericCountFromOldClients() throws Exception {
        GameController.StatPointReq r = om.readValue(
                "{\"stat\":\"PATK\",\"count\":2000000000}", GameController.StatPointReq.class);
        assertEquals("2000000000", r.count());
    }

    @Test
    void acceptsStringCountBeyondDoublePrecision() throws Exception {
        GameController.StatPointReq r = om.readValue(
                "{\"stat\":\"PATK\",\"count\":\"689000000000000000000000\"}",
                GameController.StatPointReq.class);
        assertEquals("689000000000000000000000", r.count());
    }
}
