package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第六批第 95 轮：§4.1 幂等守卫锁——第六批追加段每条 INSERT 的 code 必须有同表 DELETE 守卫，
 * 保证 gongpu-full.sql 反复执行不撞主键（整合文件的核心承诺，静态锁死防后续轮次漏写）。
 */
class IdempotencyGuardTest {

    private static String section41() throws Exception {
        Path testRoot = Path.of(IdempotencyGuardTest.class.getResource("/").toURI());
        Path root = testRoot.resolveSibling("classes");
        if (!Files.isDirectory(root)) {
            root = testRoot;
        }
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.filter(p -> p.toString().endsWith(".sql")).toList()) {
                sb.append(Files.readString(p, StandardCharsets.UTF_8)).append('\n');
            }
        }
        String sql = sb.toString();
        int i = sql.indexOf("§4.1");
        return i >= 0 ? sql.substring(i) : "";
    }

    @Test
    void everyAppendedInsertIsDeleteGuarded() throws Exception {
        String s41 = section41();
        assertTrue(!s41.isEmpty(), "应找到 §4.1 段");
        // DELETE 守卫集合（表 → codes）
        Map<String, Set<String>> guarded = new HashMap<>();
        Matcher dm = Pattern.compile("DELETE FROM `(\\w+)` WHERE `code` IN \\(([^)]*)\\)").matcher(s41);
        while (dm.find()) {
            Set<String> codes = guarded.computeIfAbsent(dm.group(1), k -> new HashSet<>());
            Matcher cm = Pattern.compile("'([a-z0-9_]+)'").matcher(dm.group(2));
            while (cm.find()) {
                codes.add(cm.group(1));
            }
        }
        // INSERT 行首 code（表 → codes）
        Matcher im = Pattern.compile("INSERT INTO `(\\w+)`.*?VALUES(.*?);\\s*\\n", Pattern.DOTALL).matcher(s41);
        int inserts = 0;
        while (im.find()) {
            String table = im.group(1);
            Matcher rm = Pattern.compile("^\\s*\\('([a-z0-9_]+)',", Pattern.MULTILINE).matcher(im.group(2));
            while (rm.find()) {
                inserts++;
                String code = rm.group(1);
                assertTrue(guarded.getOrDefault(table, Set.of()).contains(code),
                        "§4.1 中 " + table + "." + code + " 缺 DELETE 守卫，重复执行会撞主键");
            }
        }
        assertTrue(inserts >= 40, "§4.1 应有大量追加行，实际解析到 " + inserts);
    }
}
