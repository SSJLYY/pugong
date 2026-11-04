package com.gongpu.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 前端静态版本一致性锁：index.html 内所有 ?v= 必须同版（漏升一处 = immutable 缓存下新旧文件混跑）。
 */
class FrontendVersionTest {

    @Test
    void allAssetsShareOneVersion() throws Exception {
        Path html = Path.of("../web/index.html");
        if (!Files.exists(html)) return; // 仅后端模块打包场景跳过
        String s = Files.readString(html, StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\\?v=(\\d+)").matcher(s);
        Set<String> versions = new HashSet<>();
        int n = 0;
        while (m.find()) {
            versions.add(m.group(1));
            n++;
        }
        assertFalse(versions.isEmpty(), "index.html 应带 ?v= 版本参数");
        assertEquals(1, versions.size(), "全部资源应同版，实际版本集：" + versions + "（共 " + n + " 处）");
    }
}
