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
