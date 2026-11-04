package com.gongpu.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第六批第 96 轮：前后端 API 契约锁——api.js 调用的每个 game/saves 端点
 * 必须在对应 Controller 里有真实 Mapping，防改名/漏写导致前端静默 404。
 */
class ApiContractTest {

    private static String read(Path p) throws Exception {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    /** 前端调用的端点后缀（/api/game/{slot} 与 /api/saves/{slot} 之后的部分）。 */
    private static Set<String> frontendSuffixes(String js, String base) {
        Set<String> out = new HashSet<>();
        Matcher m = Pattern.compile("'" + Pattern.quote(base) + "' \\+ slot \\+ '(/[a-z0-9-]+)'").matcher(js);
        while (m.find()) {
            out.add(m.group(1));
        }
        Matcher m2 = Pattern.compile("'" + Pattern.quote(base) + "' \\+ slot \\+ '/([a-z0-9-]+)/'").matcher(js);
        while (m2.find()) {
            out.add("/" + m2.group(1));
        }
        return out;
    }

    private static Set<String> controllerSuffixes(String java) {
        Set<String> out = new HashSet<>();
        Matcher m = Pattern.compile("@(?:Post|Get|Delete|Put)Mapping\\(\"/\\{slot\\}(/[a-z0-9-]+)\"\\)").matcher(java);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /** 第九批第 67 轮：本批新端点显式在册——自动派生只查「前端调了后端要有」，这里反查「后端新端点前端必须接」。 */
    @Test
    void batch9EndpointsWiredInFrontend() throws Exception {
        String js = read(Path.of("../web/js/api.js"));
        for (String ep : new String[]{"adopt-pet", "switch-pet", "feed-pet", "feed-pet-batch",
                "rename-pet", "monthly-claim", "sell-all-materials", "fuse-all-gems",
                "unsocket-all", "sell-extra-blueprints", "mat-exchange-all"}) {
            assertTrue(js.contains(ep), "api.js 须接入端点 " + ep);
        }
    }

    @Test
    void frontendEndpointsExistInControllers() throws Exception {
        Path serverDir = Path.of("").toAbsolutePath();
        String js = read(serverDir.resolve("../web/js/api.js"));
        String game = read(serverDir.resolve("src/main/java/com/gongpu/web/GameController.java"));
        String saves = read(serverDir.resolve("src/main/java/com/gongpu/web/SaveController.java"));

        Set<String> gameCtrl = controllerSuffixes(game);
        Set<String> saveCtrl = controllerSuffixes(saves);
        Set<String> gameJs = frontendSuffixes(js, "/api/game/");
        Set<String> saveJs = frontendSuffixes(js, "/api/saves/");
        assertTrue(gameJs.size() >= 20, "应解析到主要 game 端点，实际 " + gameJs.size());
        for (String ep : gameJs) {
            assertTrue(gameCtrl.contains(ep), "前端调用的 game 端点在 GameController 缺失：" + ep);
        }
        for (String ep : saveJs) {
            assertTrue(saveCtrl.contains(ep), "前端调用的 saves 端点在 SaveController 缺失：" + ep);
        }
    }
}
