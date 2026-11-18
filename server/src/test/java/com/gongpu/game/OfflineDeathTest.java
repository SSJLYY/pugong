package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 设计文档 §10.2(已定决策 #10):离线死亡真实结算。
 * 离线与在线共用同一战斗流程(mobAttack/dotTick 照跑)——本测试验证:
 * 离线期间打不过的怪会真实打死玩家,回游戏进死亡画面。
 */
class OfflineDeathTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void offlineDeathHappensWhenMobTooStrong() {
        PlayerState s = NewState.create();          // 空手新号:HP100/PATK2
        s.zone = "volcano";
        GameEngine.spawnMob(s, "volcano");   // 火山怪(玩家毫无还手之力)
        double offlineSec = 8 * 3600;               // 8 小时上限
        GameEngine.AdvanceResult r = GameEngine.advance(
                s, System.currentTimeMillis() + (long) (offlineSec * 1000), 0.5, true);
        assertTrue(r.died(), "空手挂火山 8 小时必须离线死亡(设计 §10.2),实际活着");
        assertNotNull(s.deathCause, "死亡原因应有记录(尸检报告用)");
        assertEquals(Num.ZERO.compareTo(s.life.curHP), 0, "死亡时血量归零");
    }

    @Test
    void offlineSurvivalWhenMobIsKilled() {
        // 高战力:挂机 8 小时怪被反复秒杀 → 不死(正常放置收益)
        PlayerState s = NewState.create();
        s.zone = "plain";
        s.equipped.put("weapon", strongWeapon());
        GameEngine.spawnMob(s, "plain");
        GameEngine.AdvanceResult r = GameEngine.advance(
                s, System.currentTimeMillis() + (long) (8 * 3600 * 1000), 0.5, true);
        assertFalse(r.died(), "打得动怪的号不应离线死亡");
        assertTrue(r.kills() > 0, "8 小时应结算出击杀");
    }

    @Test
    void offlineDeathStillGrantsGrowth() {
        // §10.2 "血量成长永不丢":即使离线死了,死前的赋能成长(血量)必须保留
        PlayerState s = NewState.create();
        s.zone = "volcano";
        GameEngine.spawnMob(s, "volcano");
        GameEngine.AdvanceResult r = GameEngine.advance(
                s, System.currentTimeMillis() + (long) (8 * 3600 * 1000), 0.5, true);
        if (r.died()) {
            assertTrue(Num.gt(s.life.maxHP, Num.of(100)), "离线死了,死前的普攻成长(血上限)必须保留");
        }
    }

    @Test
    void dotDamageCappedAtBuffDuration() {
        // 审查 P1-2 回归测试:修复前离线帧新上的毒按全帧结算(0.02×maxHP×28800s ≈ 288×maxHP 必死)。
        // 修复后 DoT 伤害时长封顶 buff 剩余秒:毒 buff 10s → 最多 0.02×maxHP×10 = 20% 生命上限。
        PlayerState s = NewState.create();
        s.buffs.put("poison", 10.0);
        double before = s.life.curHP.doubleValue();   // 100
        GameEngine.dotTick(s, 14400);                  // 离线 8h 大帧
        double expectedDmg = Math.max(1, 0.02 * 100) * 10;   // 每秒 max(1, 2%×maxHP),封顶 10 秒 = 20
        assertEquals(before - expectedDmg, s.life.curHP.doubleValue(), 0.51,
                "毒伤害必须封顶为 buff 剩余时长(10s=20 血),不得按全帧结算");
        assertEquals(0.0, (double) (s.buffs.get("poison") == null ? 0 : s.buffs.get("poison")), 0.01,
                "毒 buff 应被耗尽");
    }

    @Test
    void advanceShortFramesCountDotDurationOnce() {
        PlayerState s = dotState();
        s.buffs.put("poison", 10.0);
        s.buffs.put("burn_dot", 10.0);
        long start = s.lastTickAt;

        for (int second = 1; second <= 10; second++) {
            GameEngine.AdvanceResult result = GameEngine.advance(s, start + second * 1000L, 1, false);
            assertFalse(result.died());
            assertEquals(10.0 - second, s.buffs.getOrDefault("poison", 0.0), 1e-9);
            assertEquals(10.0 - second, s.buffs.getOrDefault("burn_dot", 0.0), 1e-9);
            assertEquals(100.0 - 3.5 * second, s.life.curHP.doubleValue(), 1e-9,
                    "毒和灼烧每帧应各计时一次，并结算全部有效时长的伤害");
        }
        GameEngine.advance(s, start + 11000, 1, false);
        assertEquals(65.0, s.life.curHP.doubleValue(), 1e-9, "到期后不能继续造成伤害");
    }

    @Test
    void advanceLongFrameSettlesExistingDotsBeforeExpiration() {
        PlayerState s = dotState();
        s.buffs.put("poison", 10.0);
        s.buffs.put("burn_dot", 10.0);

        GameEngine.AdvanceResult result = GameEngine.advance(s, s.lastTickAt + 8 * 3600_000L, 0.5, true);

        assertFalse(result.died());
        assertEquals(65.0, s.life.curHP.doubleValue(), 1e-9,
                "长帧必须结算已有毒和灼烧各10秒伤害，不能由通用buff计时提前清除");
        assertEquals(0.0, s.buffs.getOrDefault("poison", 0.0), 1e-9);
        assertEquals(0.0, s.buffs.getOrDefault("burn_dot", 0.0), 1e-9);
    }

    @Test
    void advanceOfflineDotTimerUsesEffectiveSeconds() {
        PlayerState s = dotState();
        s.buffs.put("poison", 30.0);
        s.buffs.put("burn_dot", 30.0);

        GameEngine.advance(s, s.lastTickAt + 20000, 0.5, true);

        assertEquals(20.0, s.buffs.getOrDefault("poison", 0.0), 1e-9);
        assertEquals(20.0, s.buffs.getOrDefault("burn_dot", 0.0), 1e-9);
        assertEquals(65.0, s.life.curHP.doubleValue(), 1e-9,
                "离线DoT的计时与伤害应统一使用折算后的10秒");
    }

    @Test
    void advanceShortFrameCountsNewlyAppliedDotsOnce() {
        PlayerState s = dotState();
        GameEngine.spawnMob(s, "village");
        s.mob.id = "toad_lava";
        s.mob.ASPD = 1;
        s.mob.PATK = Num.ZERO;
        s.mob.MATK = Num.ZERO;
        s.mob.RNG = 0;
        s.fixed.EVA = Num.ZERO;

        GameEngine.AdvanceResult result = GameEngine.advance(s, s.lastTickAt + 4000, 1, false);

        assertFalse(result.died());
        assertEquals(82.0, s.life.curHP.doubleValue(), 1e-9,
                "怪物反击造成4点伤害，本帧新上的毒和灼烧各结算4秒，共14点伤害");
        assertEquals(6.0, s.buffs.getOrDefault("poison", 0.0), 1e-9);
        assertEquals(6.0, s.buffs.getOrDefault("burn_dot", 0.0), 1e-9);
    }

    @Test
    void advanceLongFrameCapsNewlyAppliedDots() {
        PlayerState s = dotState();
        GameEngine.spawnMob(s, "village");
        s.mob.id = "toad_lava";
        s.mob.ASPD = 1.0 / 14400;
        s.mob.PATK = Num.ZERO;
        s.mob.MATK = Num.ZERO;
        s.mob.RNG = 0;
        s.fixed.EVA = Num.ZERO;

        GameEngine.AdvanceResult result = GameEngine.advance(s, s.lastTickAt + 8 * 3600_000L, 0.5, true);

        assertFalse(result.died());
        assertEquals(64.0, s.life.curHP.doubleValue(), 1e-9,
                "怪物反击造成1点伤害，本帧新上的毒和灼烧应各封顶10秒，共35点伤害");
        assertEquals(0.0, s.buffs.getOrDefault("poison", 0.0), 1e-9);
        assertEquals(0.0, s.buffs.getOrDefault("burn_dot", 0.0), 1e-9);
    }

    @Test
    void advanceDotDeathSkipsRecoveryAndAutomaticConsumptionAndFreezesNextFrame() {
        for (boolean offline : new boolean[]{false, true}) {
            PlayerState s = dotState();
            s.zone = "plain";
            s.life.REGEN = Num.of(1);
            s.life.MREGEN = Num.of(1);
            s.life.curMP = Num.of(40);
            s.autoHpLine = 0.5;
            s.autoMpLine = 0.5;
            s.autoSkill = true;
            s.autoBurn = true;
            s.autoEat = true;
            s.potions.put("hp_s", 3);
            s.potions.put("hp_l", 3);
            s.potions.put("mp", 3);
            s.potions.put("food", 3);
            s.buffs.put("poison", 120.0);
            var potionsBefore = java.util.Map.copyOf(s.potions);
            var mpBefore = s.life.curMP;
            var goldBefore = s.gold;
            long deathsBefore = s.stats.deaths;
            long now = s.lastTickAt + (offline ? 120000 : 60000);

            GameEngine.AdvanceResult result = GameEngine.advance(s, now, 0.5, offline);

            assertTrue(result.died(), "持续伤害必须触发死亡，offline=" + offline);
            assertTrue(s.dead);
            assertNotNull(s.deathCause);
            assertEquals(0, s.life.curHP.compareTo(Num.ZERO), "死亡帧不能继续回血");
            assertEquals(0, s.life.curMP.compareTo(mpBefore), "死亡帧不能继续回蓝或自动用药");
            assertEquals(potionsBefore, s.potions, "死亡帧不能消耗药剂或食物");
            assertEquals(goldBefore, s.gold, "死亡帧不能自动买药");
            assertEquals(deathsBefore + 1, s.stats.deaths, "本帧只记录一次死亡");
            assertEquals(60.0, s.buffs.get("poison"), 1e-9);
            assertFalse(s.buffs.containsKey("regen_food"), "死亡帧不能自动进食");
            assertTrue(s.cds.isEmpty(), "死亡帧不能自动施放技能");
            var buffsAfterDeath = java.util.Map.copyOf(s.buffs);
            var punchesAfterDeath = s.stats.punches;
            long playSecondsAfterDeath = s.stats.playSeconds;

            GameEngine.AdvanceResult frozen = GameEngine.advance(s, now + 60000, 0.5, offline);

            assertTrue(frozen.died());
            assertEquals(result.deathCause(), frozen.deathCause());
            assertEquals(0.0, frozen.seconds(), 1e-9);
            assertEquals("0", frozen.punches());
            assertEquals(0, frozen.kills());
            assertEquals(0, frozen.drops());
            assertEquals(now, s.lastTickAt, "死亡后不再推进时间锚点");
            assertEquals(playSecondsAfterDeath, s.stats.playSeconds);
            assertEquals(punchesAfterDeath, s.stats.punches);
            assertEquals(deathsBefore + 1, s.stats.deaths, "后续帧不能重复记录死亡");
            assertEquals(0, s.life.curHP.compareTo(Num.ZERO));
            assertEquals(0, s.life.curMP.compareTo(mpBefore));
            assertEquals(potionsBefore, s.potions);
            assertEquals(goldBefore, s.gold);
            assertEquals(buffsAfterDeath, s.buffs, "死亡后增益计时也应冻结");
            assertTrue(s.cds.isEmpty());
        }
    }

    private PlayerState dotState() {
        PlayerState s = NewState.create();
        s.lastTickAt = 1000;
        s.mob = null;
        s.blessings.clear();
        s.life.REGEN = Num.ZERO;
        return s;
    }

    private PlayerState.Item strongWeapon() {
        PlayerState.Item it = new PlayerState.Item();
        it.uid = 1;
        it.name = "测试神兵";
        it.slot = "weapon";
        it.quality = 3;
        it.base.put("PATK", new java.math.BigDecimal("500"));
        it.base.put("ASPD", new java.math.BigDecimal("2"));
        return it;
    }
}
