package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import com.gongpu.state.SaveService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 无限属性点天赋(statlord)全链路:击杀得点/加点入面板/越界拒绝/转生清零。
 * 每杀一怪 +1 自由属性点;加点值 = STAT_POINT_STEP × count,并入面板 flat 区。
 */
class StatPointTest {

    private final GameService game = new GameService();

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState statlord() {
        PlayerState s = NewState.create();
        s.talent = "statlord";
        return s;
    }

    /** 亿级余额一次梭哈（玩家实测需求：3.86 亿点、旧前端最高档只给 +10000）。 */
    @Test
    void allocateHundredMillionAtOnce() {
        PlayerState s = statlord();
        s.freePoints = Num.of(386_000_000L);
        PanelCalculator.Panel base = PanelCalculator.of(s);
        game.allocateStatPoint(s, "PATK", BigDecimal.valueOf(100_000_000));
        assertEquals(0, s.freePoints.compareTo(Num.of(286_000_000L)), "扣点应精确");
        PanelCalculator.Panel p = PanelCalculator.of(s);
        assertEquals(0, p.PATK().compareTo(Num.add(base.PATK(), Num.of(100_000_000L))),
                "物攻应 +1 亿（每点 +1）");
        // 剩余全部一次加完
        game.allocateStatPoint(s, "PATK", BigDecimal.valueOf(286_000_000));
        assertEquals(0, s.freePoints.compareTo(Num.ZERO), "全部梭哈后余额清零");
    }

    @Test
    void killsGrantOnePointEach() {
        PlayerState s = statlord();
        s.zone = "plain";
        GameEngine.spawnMob(s, "plain");
        s.mob.curHP = new BigDecimal("0.1");   // 一拳必杀
        BigDecimal before = s.freePoints;

        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("50"), 1.0);

        assertTrue(Num.gte(s.freePoints, Num.add(before, Num.of(1))), "击杀必须 +1 自由属性点");
    }

    @Test
    void overflowKillsGrantPointsProportionally() {
        PlayerState s = statlord();
        s.zone = "plain";
        GameEngine.spawnMob(s, "plain");

        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("1e9"), 1.0);

        // 溢出期望击杀同样是真杀:点数与 kills 口径一致
        assertEquals(0, s.freePoints.compareTo(s.stats.kills),
                "自由属性点应等于总击杀数(溢出折算含在内)");
    }

    @Test
    void nonStatlordNeverEarnsPoints() {
        PlayerState s = NewState.create();
        s.talent = "might";
        s.zone = "plain";
        GameEngine.spawnMob(s, "plain");
        s.mob.curHP = new BigDecimal("0.1");

        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("50"), 1.0);

        assertEquals(0, s.freePoints.compareTo(Num.ZERO), "非 statlord 击杀不得得点");
    }

    @Test
    void allocateAddsToPanelAndSpendsPoints() {
        PlayerState s = statlord();
        s.freePoints = Num.of(10);
        BigDecimal patkBefore = PanelCalculator.of(s).PATK();

        game.allocateStatPoint(s, "PATK", BigDecimal.valueOf(5));

        assertEquals(0, s.freePoints.compareTo(Num.of(5)), "消耗 5 点");
        assertEquals(0, PanelCalculator.of(s).PATK().compareTo(patkBefore.add(Num.of(5))),
                "PATK 加点 = 每点 +1,直接进面板");
        // 面板小数步长属性:CRIT 每点 +0.5%
        s.freePoints = Num.of(4);
        game.allocateStatPoint(s, "CRIT", BigDecimal.valueOf(4));
        assertTrue(Num.gt(PanelCalculator.of(s).CRIT(), Num.of(0.019)), "CRIT 每点 +0.5%,4 点 ≥ +2%");
    }

    @Test
    void allocateRejectsInsufficientOrInvalid() {
        PlayerState s = statlord();
        s.freePoints = Num.of(2);
        assertThrows(com.gongpu.common.ApiException.class, () -> game.allocateStatPoint(s, "PATK", BigDecimal.valueOf(3)),
                "点数不足应拒绝");
        assertThrows(com.gongpu.common.ApiException.class, () -> game.allocateStatPoint(s, "GOLD", BigDecimal.ONE),
                "不可加点属性应拒绝(金币不是属性)");
        assertThrows(com.gongpu.common.ApiException.class, () -> game.allocateStatPoint(s, "PATK", BigDecimal.ZERO),
                "数量至少 1");
    }

    @Test
    void allocateRejectsWithoutTalent() {
        PlayerState s = NewState.create();
        assertThrows(com.gongpu.common.ApiException.class, () -> game.allocateStatPoint(s, "PATK", BigDecimal.ONE),
                "未觉醒 statlord 应拒绝加点");
    }

    /** 超 int 上限一次加完（2026-11-04 反馈：旧「全部」被前端/int 卡在 20 亿，不是真全部）。 */
    @Test
    void allocateBeyondIntRangeAtOnce() {
        PlayerState s = statlord();
        s.freePoints = new BigDecimal("5000000000");
        PanelCalculator.Panel base = PanelCalculator.of(s);
        game.allocateStatPoint(s, "PATK", new BigDecimal("3000000000"));
        assertEquals(0, s.freePoints.compareTo(new BigDecimal("2000000000")), "30 亿一次扣应精确");
        assertEquals(0, PanelCalculator.of(s).PATK()
                        .compareTo(Num.add(base.PATK(), new BigDecimal("3000000000"))),
                "物攻应 +30 亿整");
    }

    /** 天文数字余额一次梭哈（击杀垓级玩家的自由点远超 long/JS 安全整数，字符串口径全程精确）。 */
    @Test
    void allocateAstronomicalAllIn() {
        PlayerState s = statlord();
        BigDecimal all = new BigDecimal("689000000000000000000000");
        s.freePoints = all;
        game.allocateStatPoint(s, "PATK", all);
        assertEquals(0, s.freePoints.compareTo(BigDecimal.ZERO), "全部梭哈后应清零");
        assertEquals(0, s.statPoints.get("PATK").compareTo(all), "累计加点应与余额逐位相等");
    }

    @Test
    void mpAffixGrowsManaCap() {
        // 「法力洪流」词条(蓝上限):装备 base MP 进面板 MP 上限——魔弹枪流派的成长线
        PlayerState s = NewState.create();
        PlayerState.Item it = new PlayerState.Item();
        it.uid = 1;
        it.name = "蓝晶坠";
        it.slot = "necklace";
        it.base.put("MP", new BigDecimal("500"));
        s.equipped.put("necklace", it);
        PanelCalculator.Panel p = PanelCalculator.of(s);
        assertEquals(0, p.MP().compareTo(Num.add(Num.of(80), Num.of(500))),
                "装备 base MP 必须抬高蓝上限(80+500=580),魔弹枪每帧可打发数随之增长");
    }

    @Test
    void rebirthClearsPointsAndAllocations() {
        PlayerState s = statlord();
        s.freePoints = Num.of(50);
        s.statPoints.put("PATK", new BigDecimal("30"));
        s.fixed.ASPD = new BigDecimal("1e45");
        game.rebirth(s, GameService.autoRebirthChoice(s));   // 首个缺口赋能(NewState 已带 HP lv0)
        assertEquals(0, s.freePoints.compareTo(Num.ZERO), "自由点是本世机制,转生清零");
        assertTrue(s.statPoints.isEmpty(), "已加点随转生清零(与击杀数每世重置口径一致)");
    }
}
