package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第八批第 99 轮收官实测修复锁：前端两处天赋展示镜像（TAL_META 徽章表、
 * talentMeta 效果表）必须覆盖 GameConst.TALENTS 全部 id——漏一个，
 * 抽到该天赋的玩家徽章与效果行就静默不显示（幸运儿/铁胃曾漏两批）。
 */
class TalentMirrorTest {

    @Test
    void frontendMirrorsCoverAllTalents() throws Exception {
        String js = Files.readString(Path.of("../web/js/app.js"));
        for (String id : GameConst.TALENTS.keySet()) {
            assertTrue(js.contains(id + ": ['"), "前端天赋镜像缺 " + id);
        }
    }
}
