package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 无限装备栏天赋(v3.12 起觉醒即无限,不再每日 +1)全链路测试:
 * 概率分布、无限栏位语义、加穿/卸下、面板属性叠加、一键穿最强适配。
 */
class BaglordTalentTest {

    private static int SEQ = 0;

    private final GameService game = new GameService();

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    // ==================== 概率分布 ====================

    @Test
    void rollTalentDistribution() {
        int n = 100_000;
        int eternal = 0, baglord = 0, statlord = 0, other = 0;
        for (int i = 0; i < n; i++) {
            String t = GameService.rollTalent();
            switch (t) {
                case "eternal" -> eternal++;
                case "baglord" -> baglord++;
                case "statlord" -> statlord++;
                default -> other++;
            }
        }
        // 30% ± 1.5%(10 万次抽样,标准差 ≈0.14%,3σ 余量大)
        assertEquals(0.30, (double) eternal / n, 0.015, "被动永续应约 30%");
        assertEquals(0.30, (double) baglord / n, 0.015, "无限装备栏应约 30%");
        assertEquals(0.30, (double) statlord / n, 0.015, "无限属性点应约 30%");
        assertEquals(0.10, (double) other / n, 0.015, "其余天赋合计约 10%");
    }

    @Test
    void topTierNotSelfChoosable() {
        PlayerState s = NewState.create();
        assertThrows(com.gongpu.common.ApiException.class, () -> game.drawTalent(s, "baglord"),
                "无限装备栏只能靠命,不可自选");
        assertThrows(com.gongpu.common.ApiException.class, () -> game.drawTalent(s, "eternal"),
                "被动永续只能靠命,不可自选");
        // 普通天赋仍可自选
        Map<String, Object> r = game.drawTalent(s, "might");
        assertEquals("might", r.get("talent"));
        assertEquals(Boolean.FALSE, r.get("topTier"));
    }

    @Test
    void rollResultCarriesTopTierFlag() {
        PlayerState s = NewState.create();
        for (int i = 0; i < 200 && s.talent == null; i++) {
            // 重置后反复抽,直到命中顶级机缘,校验标记
            PlayerState fresh = NewState.create();
            Map<String, Object> r = game.drawTalent(fresh, null);
            if (Boolean.TRUE.equals(r.get("topTier"))) {
                assertTrue("eternal".equals(r.get("talent")) || "baglord".equals(r.get("talent"))
                                || "statlord".equals(r.get("talent")),
                        "topTier 只应出现在三大机缘");
                return;
            }
            assertFalse("eternal".equals(r.get("talent")) || "baglord".equals(r.get("talent")),
                    "非顶级结果不得带 topTier");
        }
        // 200 次全未中顶级(概率 (0.1)^200 ≈ 0)——不应该走到这
        fail("200 次抽样未命中顶级机缘,概率实现可能有误");
    }

    // ==================== 觉醒即无限(v3.12) ====================

    @Test
    void baglordUnlimitedFromDayOne() {
        PlayerState s = NewState.create();
        s.talent = "baglord";
        s.extraSlots = 0;    // 觉醒当天:旧字段为 0 也不影响
        s.lastSlotDay = null;
        assertEquals(1, GameService.openExtraSlots(s), "觉醒即有 1 个空位(已用 0+1),不再等跨天");
    }

    @Test
    void nonBaglordNoExtraSlots() {
        PlayerState s = NewState.create();
        s.talent = "might";
        s.extraSlots = 5;    // 旧档残留的积累字段也不放行
        assertEquals(0, GameService.openExtraSlots(s), "非无限装备栏天赋恒为 0");
        s.talent = null;
        assertEquals(0, GameService.openExtraSlots(s));
    }

    @Test
    void extraSlotsSurviveRestart() {
        // 死亡重开保留(天赋是账号级积累)
        PlayerState s = NewState.create();
        s.talent = "baglord";
        s.extraSlots = 5;
        s.lastSlotDay = LocalDate.now().toString();
        com.gongpu.state.SaveService.resetForNewLife(s);
        assertEquals(5, s.extraSlots, "死亡重开不应清空已积累的额外栏");
        assertEquals("baglord", s.talent);
    }

    // ==================== 加穿/卸下/属性叠加 ====================

    private PlayerState.Item item(String slot, String statKey, String statVal) {
        PlayerState.Item it = new PlayerState.Item();
        it.uid = 1;
        it.name = "测试" + slot + "_" + (++SEQ);   // 唯一名:吞噬逻辑按名字合并,测试需显式控制同名
        it.emoji = "🗡️";
        it.slot = slot;
        it.quality = 1;
        it.base.put(statKey, new java.math.BigDecimal(statVal));
        return it;
    }

    @Test
    void equipExtraPutsItemAndStacksStats() {
        PlayerState s = NewState.create();
        s.talent = "baglord";
        double patkBefore = PanelCalculator.of(s).PATK().doubleValue();
        s.equipBag.add(item("weapon", "PATK", "50"));
        game.equipExtra(s, 0);
        assertNotNull(s.equipped.get("extra_0"), "应穿入 extra_0");
        double patkAfter = PanelCalculator.of(s).PATK().doubleValue();
        assertEquals(patkBefore + 50, patkAfter, 1e-9, "无相位栏装备属性必须叠加到面板");
        // 卸下回背包
        game.unequip(s, "extra_0");
        assertNull(s.equipped.get("extra_0"));
        assertEquals(1, s.equipBag.size(), "卸下后回背包");
    }

    @Test
    void equipExtraRejectsWhenNoTalent() {
        PlayerState s = NewState.create();
        s.equipBag.add(item("weapon", "PATK", "5"));
        assertThrows(com.gongpu.common.ApiException.class, () -> game.equipExtra(s, 0),
                "无天赋时应拒绝加穿");
        s.equipBag.clear();   // 清掉无天赋阶段留下的物品,避免混入下面的连穿序列
        // v3.12 起栏位无限:连续加穿多件全部成功(不抛异常,不再有每日/栏满限制);
        // 同名件自动吞噬合并——加穿 3 件同name → 一格 + 属性累加
        s.talent = "baglord";
        for (int i = 0; i < 3; i++) {
            PlayerState.Item dup = item("weapon", "PATK", String.valueOf(5 + i));
            dup.name = "同名牌";   // 显式同名:吞噬按名字匹配
            s.equipBag.add(dup);
            game.equipExtra(s, 0);
        }
        long worn = s.equipped.keySet().stream().filter(k -> k.startsWith("extra_")).count();
        assertEquals(1, worn, "同名 3 件吞噬合并成一件(连穿全程无异常)");
        assertEquals(0, s.equipped.get("extra_0").base.get("PATK").compareTo(new BigDecimal("18")),
                "三件 PATK 累加(5+6+7)");
        assertEquals(2, GameService.openExtraSlots(s), "已用 1 + 1 空位");
    }

    @Test
    void autoEquipFillsEmptyExtraSlotsByScore() {
        PlayerState s = NewState.create();
        s.talent = "baglord";   // v3.12 栏位无限:剩余背包全部穿入,断言不变
        s.equipBag.add(item("weapon", "PATK", "10"));   // 原生栏空 → 走原生装备
        s.equipBag.add(item("offhand", "PDEF", "8"));   // 原生栏空 → 走原生装备
        s.equipBag.add(item("weapon", "PATK", "30"));   // 评分高 → 替换原生 weapon(10 回背包)
        s.equipBag.add(item("chest", "PDEF", "6"));     // 原生栏 chest 空 → 走原生
        s.equipBag.add(item("boots", "SPD", "5"));      // 原生栏 boots 空 → 走原生
        s.equipBag.add(item("ring1", "PATK", "3"));     // 原生栏 ring1 空 → 走原生
        s.equipBag.add(item("weapon", "PATK", "20"));   // 剩余两把武器 → 填 extra
        game.autoEquip(s);
        assertNotNull(s.equipped.get("weapon"));
        assertNotNull(s.equipped.get("offhand"));
        assertNotNull(s.equipped.get("chest"));
        assertNotNull(s.equipped.get("boots"));
        assertNotNull(s.equipped.get("extra_0"), "一键穿最强应填入空额外栏");
        assertNotNull(s.equipped.get("extra_1"), "一键穿最强应填满额外栏");
        // 剩余两把武器按评分进额外栏:高的先进
        assertEquals(20.0, s.equipped.get("extra_0").base.get("PATK").doubleValue(),
                "评分高的先进额外栏");
        assertEquals(10.0, s.equipped.get("extra_1").base.get("PATK").doubleValue());
        assertTrue(s.equipBag.isEmpty(), "背包应被清空(7 件全部穿上)");
    }
}
