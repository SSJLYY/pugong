package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import com.gongpu.state.StateCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v3.13 挂机自动管护 + 背包容量膨胀修复回归测试。
 * <p>玩家档实锤(2026-10-31 三槽):吞噬把 BAG_CAP 累加进合并腰带 → bagCap 33720,
 * 背包堆 3.3 万件 = 轮询 13.9MB JSON 卡死。锁死三条修复链:吞噬容量取大不求和、
 * bagCap 钳制、autoMaintain(卖白装+吞噬+穿最强)全链路。</p>
 */
class BaglordMaintainTest {

    private final GameService game = new GameService();

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState.Item item(String slot, String key, String val, int quality) {
        PlayerState.Item it = new PlayerState.Item();
        it.uid = 1;
        it.name = "测试" + slot + "_" + System.nanoTime();
        it.emoji = "🗡️";
        it.slot = slot;
        it.quality = quality;
        it.base.put(key, new BigDecimal(val));
        return it;
    }

    // ==================== 吞噬不再累加容量 ====================

    @Test
    void devourDoesNotStackBagCap() {
        PlayerState s = NewState.create();
        s.talent = "baglord";
        PlayerState.Item belt1 = item("legs", "BAG_CAP", "20", 1);
        belt1.name = "负重腰带";
        s.equipBag.add(belt1);
        game.equipExtra(s, 0);
        PlayerState.Item belt2 = item("legs", "BAG_CAP", "20", 1);
        belt2.name = "负重腰带";
        s.equipBag.add(belt2);
        game.equipExtra(s, 0);
        assertEquals(0, s.equipped.get("extra_0").base.get("BAG_CAP")
                        .compareTo(new BigDecimal("20")),
                "同名腰带吞噬:容量取较大值,不累加(旧档 1680 条腰带叠出 33620 格的根因)");
        assertEquals(GameConst.BAG_MAX + 20, GameService.bagCap(s), "吞噬后容量只算一份腰带");
    }

    // ==================== bagCap 钳制旧档膨胀 ====================

    @Test
    void bagCapClampsLegacyBloat() {
        PlayerState s = NewState.create();
        PlayerState.Item megaBelt = item("legs", "BAG_CAP", "33620", 1);
        megaBelt.name = "负重腰带";
        s.equipped.put("offhand", megaBelt);
        assertEquals(GameConst.BAG_MAX * 2, GameService.bagCap(s),
                "历史档单件 BAG_CAP 累加到 3.3 万:容量词条封顶 BAG_MAX(每件腰带本义 +20)");
    }

    // ==================== autoMaintain 全链路 ====================

    @Test
    void autoMaintainSellsWhitesAndWearsBest() {
        PlayerState s = NewState.create();
        s.talent = "baglord";
        s.equipBag.add(item("weapon", "PATK", "10", 0));    // 白装 → 卖
        s.equipBag.add(item("boots", "SPD", "1", 0));       // 白装 → 卖
        s.equipBag.add(item("weapon", "PATK", "50", 2));    // 更强 → 原生 weapon
        s.equipBag.add(item("chest", "PDEF", "5", 1));      // 空位 → 原生 chest
        BigDecimal goldBefore = s.gold;
        GameService.autoMaintain(s);
        assertEquals(0, s.equipBag.size(), "管护后背包清空(白装卖掉,剩余全穿)");
        assertEquals(0, s.gold.compareTo(Num.add(goldBefore, Num.of(40L))),
                "两件白装卖价 20×(0+1)×2=40 金入账");
        assertNotNull(s.equipped.get("weapon"), "最强武器上原生栏");
        assertNotNull(s.equipped.get("chest"), "空原生栏补上");
        assertEquals(0, s.equipped.get("weapon").base.get("PATK")
                .compareTo(new BigDecimal("50")), "穿的是最强那把");
    }

    @Test
    void autoMaintainDevoursDuplicatesIntoOneSlot() {
        PlayerState s = NewState.create();
        s.talent = "baglord";
        for (int i = 0; i < 100; i++) {
            PlayerState.Item dup = item("weapon", "PATK", "3", 1);
            dup.name = "铁剑";
            s.equipBag.add(dup);
        }
        GameService.autoMaintain(s);
        long extras = s.equipped.keySet().stream().filter(k -> k.startsWith("extra_")).count();
        assertEquals(0, extras, "第一件铁剑进原生 weapon 空位,其余 99 件全部吞噬进它,不占 extra 格");
        assertEquals(0, s.equipBag.size());
        assertEquals(0, s.equipped.get("weapon").base.get("PATK")
                .compareTo(new BigDecimal("300")), "base 求和(3×100)合并进原生栏");
    }

    @Test
    void autoMaintainDeadSkips() {
        PlayerState s = NewState.create();
        s.dead = true;
        s.equipBag.add(item("weapon", "PATK", "10", 1));
        GameService.autoMaintain(s);
        assertEquals(1, s.equipBag.size(), "死亡态世界冻结,管护不动作");
    }

    @Test
    void autoMaintainNonBaglordKeepsRestInBag() {
        PlayerState s = NewState.create();
        s.talent = "might";
        s.equipBag.add(item("weapon", "PATK", "10", 0));   // 白装 → 卖
        s.equipBag.add(item("weapon", "PATK", "5", 1));    // 穿原生
        s.equipBag.add(item("boots", "SPD", "3", 1));      // 穿原生
        s.equipBag.add(item("head", "PDEF", "2", 2));      // 无空位冲突? head 原生空 → 穿
        s.equipBag.add(item("ring1", "PATK", "1", 1));     // ring1 原生空 → 穿
        GameService.autoMaintain(s);
        assertEquals(0, s.equipBag.size(), "非 baglord:白装卖掉,其余空位穿上,无多余堆积场景剩余为 0");
    }

    @Test
    void autoEquipBestFlagRoundTrip() {
        PlayerState s = NewState.create();
        assertFalse(s.autoEquipBest, "默认关闭");
        s.autoEquipBest = true;
        PlayerState back = StateCodec.decode(StateCodec.encode(s));
        assertTrue(back.autoEquipBest, "开关随存档持久化,服务重启不丢");
    }

    // ==================== 视图截断 ====================

    @Test
    void fullViewTruncatesHugeBag() {
        PlayerState s = NewState.create();
        for (int i = 0; i < GameConst.BAG_VIEW_LIMIT + 50; i++) {
            s.equipBag.add(item("weapon", "PATK", "1", 1));
        }
        java.util.Map<String, Object> v = game.fullView(s);
        assertEquals(GameConst.BAG_VIEW_LIMIT, ((java.util.List<?>) v.get("bag")).size(),
                "视图截断:超大背包只下发前 BAG_VIEW_LIMIT 件");
        assertEquals(GameConst.BAG_VIEW_LIMIT + 50, ((Number) v.get("bagTotal")).intValue(),
                "bagTotal 如实下发真实总数");
    }

    // ==================== O(n) 规模回归:3 万件背包管护不超时 ====================

    @Test
    void maintainHandles30kBagFast() {
        PlayerState s = NewState.create();
        s.talent = "baglord";
        String[] names = {"兔骨匕首", "魔弹加特林", "奥术步枪", "塔盾", "附魔弓", "魔弹手枪"};
        for (int i = 0; i < 30000; i++) {
            PlayerState.Item it = item("weapon", "PATK", "2", (i % 10 == 0) ? 0 : 3);
            it.name = names[i % names.length];
            s.equipBag.add(it);
        }
        long t0 = System.currentTimeMillis();
        GameService.autoMaintain(s);
        long cost = System.currentTimeMillis() - t0;
        assertEquals(0, s.equipBag.size(), "3 万件全部清掉(白卖+吞噬+穿入)");
        assertTrue(cost < 3000, "3 万件管护应秒级完成,实测 " + cost + "ms");
        long extras = s.equipped.keySet().stream().filter(k -> k.startsWith("extra_")).count();
        assertTrue(extras <= names.length + 8, "同名吞噬后已穿件数收敛到名字种数量级");
    }
}
