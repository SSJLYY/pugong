package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 社交/附魔公式承载表完整性(SocialService.perSecondDamage 为 private 且 duel 依赖 DB,
 * 不可直测——改为守护其数值来源 GameConst 常量表):
 * SCROLLS 火/冰/雷三系键值齐全,SCROLL_DROP/ZONE_MAT/MAT_INFO 引用一致(防空指针与幽灵掉落)。
 */
class GameConstTablesTest {

    /** 附魔卷轴表:火/冰/雷三系键齐全,名称/emoji 非空,魔伤加成为正且等于设计值(火15/冰12/雷20)。 */
    @Test
    void enchantScrollTableCompleteForThreeElements() {
        assertEquals(Set.of("scroll_fire", "scroll_ice", "scroll_thunder"),
                GameConst.SCROLLS.keySet(), "附魔卷轴必须火/冰/雷三系齐全");

        Map<String, Object[]> scrolls = GameConst.SCROLLS;
        assertEquals("火之卷轴", scrolls.get("scroll_fire")[0]);
        assertEquals(15, ((Number) scrolls.get("scroll_fire")[2]).intValue(), "火系附魔魔伤 +15");
        assertEquals("冰之卷轴", scrolls.get("scroll_ice")[0]);
        assertEquals(12, ((Number) scrolls.get("scroll_ice")[2]).intValue(), "冰系附魔魔伤 +12");
        assertEquals("雷之卷轴", scrolls.get("scroll_thunder")[0]);
        assertEquals(20, ((Number) scrolls.get("scroll_thunder")[2]).intValue(), "雷系附魔魔伤 +20");

        // 结构护栏:name/emoji 均非空,加成为正数(GameService.enchant 直接读 [0]/[2])
        for (Map.Entry<String, Object[]> e : scrolls.entrySet()) {
            Object[] v = e.getValue();
            assertTrue(v[0] instanceof String s && !s.isBlank(), e.getKey() + " 卷轴名缺失");
            assertTrue(v[1] instanceof String t && !t.isBlank(), e.getKey() + " emoji 缺失");
            assertTrue(v[2] instanceof Number n && n.doubleValue() > 0,
                    e.getKey() + " 魔伤加成必须为正数");
        }
    }

    /** 掉落表引用一致性:SCROLL_DROP 的卷轴 code 必须存在于 SCROLLS,来源怪必须是真实刷怪;六区材料各自存在且村口无材料。 */
    @Test
    void scrollDropAndZoneMatReferentialIntegrity() {
        // 收集全部真实怪物 id(普通怪 + Boss)
        Set<String> mobIds = new HashSet<>();
        GameConst.ZONES.values().forEach(z -> {
            if (z.mobs() != null) {
                z.mobs().forEach(m -> mobIds.add(m.id()));
            }
            if (z.boss() != null) {
                mobIds.add(z.boss().id());
            }
        });
        for (Map.Entry<String, String> e : GameConst.SCROLL_DROP.entrySet()) {
            assertTrue(mobIds.contains(e.getKey()),
                    "掉卷轴的怪 " + e.getKey() + " 必须真实存在于某区域刷怪表");
            assertTrue(GameConst.SCROLLS.containsKey(e.getValue()),
                    "掉落目标 " + e.getValue() + " 必须是 SCROLLS 里真实存在的卷轴(幽灵掉落会让 enchant 400)");
        }

        // 区域材料:恰好覆盖八个可狩猎区（第八批加星界）,村口(安全区)不产材料
        Set<String> huntingZones = Set.of("plain", "forest", "mine", "swamp", "snow", "volcano", "abyss", "star");
        assertEquals(huntingZones, GameConst.ZONE_MAT.keySet(), "材料表应恰好覆盖八区");
        Set<String> mats = new HashSet<>();
        for (Map.Entry<String, String> e : GameConst.ZONE_MAT.entrySet()) {
            assertTrue(GameConst.ZONES.containsKey(e.getKey()), e.getKey() + " 必须是真实区域");
            assertFalse(GameConst.ZONES.get(e.getKey()).safe(), "安全区不应产材料");
            assertTrue(GameConst.MAT_INFO.containsKey(e.getValue()),
                    "材料 " + e.getValue() + " 缺少 MAT_INFO 展示配置");
            mats.add(e.getValue());
        }
        assertEquals(8, mats.size(), "八区材料应各不相同");
    }
}
