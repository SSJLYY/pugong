package com.gongpu.web;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第七批第 71 轮：响应压缩配置锁——application.yml 必须开启 gzip 且覆盖 JSON、
 * 阈值 1KB（全量视图受益、小回执不浪费 CPU）。防止配置被误改回默认后无人察觉。
 */
class CompressionConfigTest {

    @Test
    void compressionEnabledForJsonWithThreshold() throws Exception {
        String yml;
        try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
            assertNotNull(in, "application.yml 应在 classpath 上");
            yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int idx = yml.indexOf("compression:");
        assertTrue(idx >= 0, "应有 compression 配置段");
        String seg = yml.substring(idx, Math.min(yml.length(), idx + 500));
        assertTrue(seg.contains("enabled: true"), "压缩应开启");
        assertTrue(seg.contains("application/json"), "压缩类型应含 application/json");
        assertTrue(seg.contains("min-response-size: 1024"), "压缩阈值应为 1KB");
    }
}
