package com.gongpu.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 第二十批·轮10：用户自由文本控制字符清洗（墓志铭/宠物名/存档名入口）。
 */
class TextsTest {

    @Test
    void stripsNewlinesAndControlChars() {
        assertEquals("大侠走好", Texts.stripControl("大侠\n走好"));
        assertEquals("一路好走", Texts.stripControl("一\r\n路\u0000好走\t"));
        assertEquals("plain", Texts.stripControl("plain"));
    }

    @Test
    void keepsChineseAndEmoji() {
        assertEquals("剑来🗡️", Texts.stripControl("剑来🗡️"));
    }

    @Test
    void nullPassthrough() {
        assertNull(Texts.stripControl(null));
    }
}
