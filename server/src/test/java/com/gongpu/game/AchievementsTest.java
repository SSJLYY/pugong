package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 成就系统测试:评估正确性 + 前后端 id 表一致性(防两端各自增删导致漂移)。
 */
class AchievementsTest {

    @Test
    void allIdsUnique() {
        Set<String> ids = new HashSet<>();
        for (Achievements.Def d : Achievements.ALL) {
            assertTrue(ids.add(d.id()), "成就 id 重复: " + d.id());
        }
        assertEquals(61, Achievements.ALL.size(), "成就总数应为 61(与前端表同步增删)");
    }

    @Test
    void frontendIdsMatchServer() throws Exception {
        Path js = Path.of("../web/js/achievements.js");
        assertTrue(Files.exists(js), "找不到前端 achievements.js(工作目录应为 server/)");
        Matcher m = Pattern.compile("\\{\\s*id:\\s*'([^']+)'").matcher(Files.readString(js));
        Set<String> fe = new HashSet<>();
        while (m.find()) {
            fe.add(m.group(1));
        }
        Set<String> be = Achievements.ALL.stream().map(Achievements.Def::id).collect(Collectors.toSet());
        assertEquals(be, fe, "前后端成就 id 必须完全一致");
    }

    @Test
    void newCharacterUnlocksNothing() {
        PlayerState s = NewState.create();
        List<String> got = Achievements.evaluate(s);
        assertTrue(got.isEmpty(), "新号(0 拳)不应解锁任何成就,实际 " + got);
    }

    @Test
    void firstPunchUnlocksWithLifetimeCounter() {
        PlayerState s = NewState.create();
        s.stats.lifetimePunches = com.gongpu.common.Num.of(1);
        s.achievements.clear();
        // 模拟 persist 流程:评估 → addAll 落集合
        s.achievements.addAll(Achievements.evaluate(s));
        assertTrue(s.achievements.contains("first_punch"));
        // 万拳成就同时达成(lifetime 1e4)
        s.stats.lifetimePunches = com.gongpu.common.Num.of(10000);
        List<String> got = Achievements.evaluate(s);
        assertTrue(got.contains("punch_10k"));
        assertFalse(got.contains("first_punch"), "已解锁的不应重复出现在增量里");
    }

    @Test
    void goldAndTalentAndBossConditions() {
        PlayerState s = NewState.create();
        s.gold = com.gongpu.common.Num.of(20000);
        s.talent = "eternal";
        s.bossKills.add("boss_plain");
        s.stats.kills = com.gongpu.common.Num.of(12);
        List<String> got = Achievements.evaluate(s);
        assertTrue(got.contains("gold_10k"));
        assertTrue(got.contains("talent"));
        assertTrue(got.contains("boss_win"));
        assertTrue(got.contains("duelist"), "觉醒+12杀 应解锁切磋者");
        assertFalse(got.contains("zone_volcano"), "没到火山不该解锁");
    }

    @Test
    void evaluateAddsIntoPersistedSet() {
        // 模拟 persist 流程:evaluate 的增量 addAll 进 achievements 后,再次评估应为空增量
        PlayerState s = NewState.create();
        s.stats.lifetimePunches = com.gongpu.common.Num.of(5);
        s.achievements.addAll(Achievements.evaluate(s));
        assertTrue(Achievements.evaluate(s).isEmpty(), "解锁落集合后再次评估不应有增量");
    }
}
