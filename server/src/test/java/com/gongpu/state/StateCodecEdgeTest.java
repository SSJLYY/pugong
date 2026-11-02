package com.gongpu.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StateCodec 边界(normalize 兜底语义):
 * lastTickAt 非正数(0/负)回落到 now;正数原样保留不做虚假归零;
 * bossKills/codex 集合序列化往返无损(LinkedHashSet 保序),缺字段回落空集合而非 null。
 */
class StateCodecEdgeTest {

    /** lastTickAt=0/负数(旧档/损坏档)decode 时必须回落到当前时间,让下一帧 advance 从现在起算,不得产生超大离线结算。 */
    @Test
    void lastTickAtNonPositiveFallsBackToNowPositivePreserved() {
        long before = System.currentTimeMillis();
        PlayerState zero = StateCodec.decode("{\"lastTickAt\":0}");
        assertTrue(zero.lastTickAt >= before, "lastTickAt=0 应回落到 now(不早于解码时刻)");
        assertTrue(zero.lastTickAt <= System.currentTimeMillis(), "回落值应是当前时间,不是未来");

        PlayerState negative = StateCodec.decode("{\"lastTickAt\":-5}");
        assertTrue(negative.lastTickAt > 0, "负数 lastTickAt 同样回落为正");

        // 正数原样保留:归档→载入不重置,推进语义连续
        PlayerState s = NewState.create();
        s.lastTickAt = 1234567890123L;
        PlayerState back = StateCodec.decode(StateCodec.encode(s));
        assertEquals(1234567890123L, back.lastTickAt, "正数 lastTickAt 不得被 normalize 改写");
    }

    /**
     * bossKills(Boss 首杀标记,防重复发奖)与 codex(图鉴)内容必须完整往返;旧档缺字段回落空集合。
     * <p>注:字段声明为 Set,decode 反序列化回落为无序 HashSet——插入顺序不跨存档保留,
     * 但两者语义均为成员判重(bossKills.contains 防重复发奖),顺序无关紧要,故只断言内容。</p>
     */
    @Test
    void bossKillsAndCodexRoundTrip() {
        PlayerState s = NewState.create();
        s.bossKills.add("boss_plain");
        s.bossKills.add("boss_wolf");
        s.codex.add("rabbit");
        s.codex.add("wolf");

        String json = StateCodec.encode(s);
        assertTrue(json.contains("boss_plain"), "bossKills 必须落库(丢了会重复发首杀奖金)");
        assertTrue(json.contains("rabbit"), "codex 必须落库");

        PlayerState back = StateCodec.decode(json);
        assertEquals(java.util.Set.of("boss_plain", "boss_wolf"), back.bossKills,
                "bossKills 内容往返无损");
        assertEquals(java.util.Set.of("rabbit", "wolf"), back.codex, "codex 内容往返无损");

        // 极简旧档:缺字段回落空集合而非 null(防运行期 NPE)
        PlayerState legacy = StateCodec.decode("{}");
        assertNotNull(legacy.bossKills);
        assertNotNull(legacy.codex);
        assertTrue(legacy.bossKills.isEmpty());
        assertTrue(legacy.codex.isEmpty());
    }
}
