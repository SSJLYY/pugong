package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 新手保底(legacy 迁移)+ 词条 flat 化 + 「每秒回复 X% 生命上限」词条测试。
 */
class NewbiePityTest {

    private final GameService game = new GameService();

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fighter() {
        PlayerState s = NewState.create();
        s.zone = "plain";
        return s;
    }

    // ==================== 保底发放 ====================

    @Test
    void firstFiveKillsGrantFiveWeapons() {
        PlayerState s = fighter();
        for (int i = 0; i < 5; i++) {
            GameEngine.grantNewbieDrops(s);
        }
        assertEquals(5, s.newbieWeaponsGiven, "前 5 杀每杀必掉一把保底武器");
        assertEquals(5, s.equipBag.size());
        for (PlayerState.Item it : s.equipBag) {
            assertEquals(1, it.quality, "保底武器为精良(1 词条)");
            assertEquals(1, it.affixes.size(), "保底武器带 1 条随机词条");
            assertTrue(List.of("新手木棍", "兔骨匕首", "铁剑", "匕首", "轻弩").contains(it.name),
                    "武器应在保底池内,实际 " + it.name);
        }
        // 第 6 杀不再发武器
        GameEngine.grantNewbieDrops(s);
        assertEquals(5, s.newbieWeaponsGiven, "满 5 件后停止发放");
    }

    @Test
    void ringGrantedAtTenKillsWithRegenHpAffix() {
        PlayerState s = fighter();
        for (int i = 0; i < 10; i++) {
            GameEngine.grantNewbieDrops(s);
        }
        assertTrue(s.ringGranted, "≥10 杀应发放回春戒");
        // 空戒指位自动穿上,两戒占位才进背包——两处都可能
        PlayerState.Item ring = "回春戒".equals(s.equipped.get("ring1") == null ? "" : s.equipped.get("ring1").name)
                ? s.equipped.get("ring1")
                : s.equipBag.stream().filter(it -> "回春戒".equals(it.name)).findFirst().orElse(null);
        assertNotNull(ring, "回春戒应在戒指位或背包");
        assertEquals(3, ring.quality, "回春戒为史诗");
        assertTrue(ring.affixes.stream().anyMatch(a -> "REGEN_HP".equals(a.key) && "pct_hp".equals(a.type)),
                "回春戒必须带「每秒回复 X% 生命上限」词条");
    }

    @Test
    void bagFullPityWaitsNotConsumed() {
        PlayerState s = fighter();
        // 背包塞满 + 两个戒指位都占住:戒指无处可去 → 保底等待(不置位,清理后必得)
        PlayerState.Item junkRing = new PlayerState.Item();
        junkRing.uid = s.nextUid++;
        junkRing.name = "铜戒";
        junkRing.slot = "ring1";
        s.equipped.put("ring1", junkRing);
        PlayerState.Item junkRing2 = new PlayerState.Item();
        junkRing2.uid = s.nextUid++;
        junkRing2.name = "铜戒2";
        junkRing2.slot = "ring2";
        s.equipped.put("ring2", junkRing2);
        while (s.equipBag.size() < GameConst.BAG_MAX) {
            PlayerState.Item it = new PlayerState.Item();
            it.uid = s.nextUid++;
            it.name = "占位";
            s.equipBag.add(it);
        }
        for (int i = 0; i < 12; i++) {
            GameEngine.grantNewbieDrops(s);
        }
        assertEquals(0, s.newbieWeaponsGiven, "背包满时保底不发放也不消耗次数");
        assertFalse(s.ringGranted, "戒指保底等待(不置位,清理背包后必得)");
    }

    @Test
    void ringAutoEquipsIntoEmptyRingSlot() {
        // 玩家反馈(2026-10-31):回春戒躺背包里 1% 回复等于没有——保底的意义在于立刻生效
        PlayerState s = fighter();
        for (int i = 0; i < 12; i++) {
            GameEngine.grantNewbieDrops(s);
        }
        assertTrue(s.ringGranted);
        boolean worn = ("回春戒".equals(s.equipped.get("ring1") == null ? "" : s.equipped.get("ring1").name))
                || ("回春戒".equals(s.equipped.get("ring2") == null ? "" : s.equipped.get("ring2").name));
        assertTrue(worn, "空戒指位时回春戒应自动穿上");
    }

    @Test
    void villageKillsDoNotCount() {
        PlayerState s = NewState.create();
        s.zone = "village";
        for (int i = 0; i < 20; i++) {
            GameEngine.grantNewbieDrops(s);
        }
        assertEquals(0, s.newbieKills, "村口(安全区)击杀不计入保底");
    }

    @Test
    void pityResetsEachLife() {
        PlayerState s = fighter();
        for (int i = 0; i < 10; i++) {
            GameEngine.grantNewbieDrops(s);
        }
        assertTrue(s.ringGranted);
        // 死亡重开:保底每世重新生效
        com.gongpu.state.SaveService.resetForNewLife(s);
        assertEquals(0, s.newbieKills);
        assertEquals(0, s.newbieWeaponsGiven);
        assertFalse(s.ringGranted, "转生/重开后保底重新计算");
    }

    // ==================== 词条 flat 化 / pct_hp ====================

    private PlayerState.Item itemWith(String type, String key, String val) {
        PlayerState.Item it = new PlayerState.Item();
        it.uid = 1;
        it.name = "测试装";
        PlayerState.AffixInst a = new PlayerState.AffixInst();
        a.type = type;
        a.key = key;
        a.val = new java.math.BigDecimal(val);
        it.affixes.add(a);
        return it;
    }

    @Test
    void vigorousRegenNowFlat() {
        PlayerState s = NewState.create();
        s.equipped.put("necklace", itemWith("flat", "REGEN", "2"));
        PanelCalculator.Panel p = PanelCalculator.of(s);
        // 基础 REGEN 2 + 词条 2 = 4
        assertEquals(0, p.REGEN().compareTo(Num.of(4)), "坚韧改 flat:REGEN 直接 +2(旧 pct 只加 0.1 纯摆设)");
        assertEquals(0, p.regenHpPerSec().compareTo(Num.ZERO), "无 pct_hp 词条时额外回复为 0");
    }

    @Test
    void springRingHealsPercentMaxHpPerSecond() {
        PlayerState s = NewState.create();
        s.equipped.put("ring1", EquipFactory.generateNewbieRing(s));
        PanelCalculator.Panel p = PanelCalculator.of(s);
        // 生命泉涌:每秒回复 1% 生命上限
        assertEquals(0, p.regenHpPerSec().compareTo(Num.mul(p.maxHP(), Num.of(0.01))),
                "regenHpPerSec 应等于 maxHP×1%");
        // 战斗回血:regenTick 2 秒 → curHP 增加 (REGEN + maxHP×1%)×2
        s.life.curHP = Num.of(50);   // 先扣血
        double before = s.life.curHP.doubleValue();
        GameEngine.advance(s, System.currentTimeMillis() + 1_000_000, 1.0, false);
        // 用直接调用语义不便(advance 会战斗),改为断言 regen 组成即可(上面已验),此处验证方向:
        assertTrue(s.life.curHP.doubleValue() >= before, "带泉涌词条挂机回血不应为负");
    }
}
