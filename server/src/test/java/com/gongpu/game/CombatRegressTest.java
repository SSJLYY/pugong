package com.gongpu.game;

import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 战斗回归锁:三条历史 P1(格挡反弹误杀玩家/护栏外溢出击杀零金币/自动飞升选择顺序)。
 * 每条对应一次真实事故级缺陷,勿删。
 */
class CombatRegressTest {

    @Test
    void reflectKillGrantsKillInsteadOfKillingPlayer() {
        // P1(2026-10-31):格挡反弹把怪物打到 0 血时误调 kill(玩家死亡函数),
        // 玩家被自己的反弹"弹死",且击杀不计 kills/金币、怪物残留 0 血。
        PlayerState s = NewState.create();
        s.zone = "plain";
        GameEngine.spawnMob(s, "plain");
        PlayerState.MobState m = s.mob;
        m.curHP = new BigDecimal("0.1");  // 反弹一点就死
        s.buffs.put("block", 5.0);        // 格挡中
        java.math.BigDecimal goldBefore = s.gold;

        GameEngine.mobAttack(s, PanelCalculator.of(s), 1.0);

        assertFalse(s.dead, "反弹击杀绝不能把玩家判死");
        assertTrue(Num.gte(s.stats.kills, Num.of(1)), "反弹击杀必须计入 kills");
        assertTrue(Num.gt(s.gold, goldBefore), "反弹击杀必须发金币");
        assertNotNull(s.mob, "普通怪被反弹打死应刷出下一只");
        assertNotSame(m, s.mob, "0 血怪物不能残留在场景里");
    }

    @Test
    void overflowDamageStillGrantsGold() {
        // P1(2026-10-31):单帧伤害超 100 只怪后,护栏外折算的 extraKills 只补
        // kills 和掉落不补金币——中后期挂机收益被系统性吞掉大半。
        PlayerState s = NewState.create();
        s.zone = "plain";
        GameEngine.spawnMob(s, "plain");
        java.math.BigDecimal goldBefore = s.gold;

        int drops = GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("1e9"), 1.0);

        assertTrue(Num.gt(s.stats.kills, Num.of(100)), "超出护栏的伤害应折算成期望击杀");
        assertTrue(Num.gt(s.gold, goldBefore), "护栏外折算的击杀必须补发期望金币");
        assertTrue(drops >= 0);
    }

    @Test
    void overflowDropsRespectAutoSellKeep() {
        // 玩家反馈(2026-10-31):高攻速下常态走溢出期望分支,该分支此前不处理
        // autoSellKeep——挂机售卖开着,白装照样进背包,设置形同虚设。
        PlayerState s = NewState.create();
        s.zone = "plain";
        GameEngine.spawnMob(s, "plain");
        s.autoSellKeep = 1;   // 卖白色及以下
        int bagBefore = s.equipBag.size();

        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("1e9"), 1.0);

        // 溢出阶段产生的所有入包物品必须全部 >= 保留档(白色一律折现,不占背包)
        for (int i = bagBefore; i < s.equipBag.size(); i++) {
            assertTrue(s.equipBag.get(i).quality >= s.autoSellKeep,
                    "自动售卖开启时,低于保留档的掉落必须折现而非入包");
        }
    }

    @Test
    void autoRebirthChoiceFillsFirstMissingBless() {
        // P1(2026-10-31):控制器先 validate(s,null) 后取 chosen——validate 两个分支
        // 恒抛,自动飞升从未生效。选择逻辑下沉到 GameService 并在此锁死语义。
        PlayerState s = NewState.create();
        s.blessings.put("HP", new PlayerState.Bless(1));
        s.blessings.put("PATK", new PlayerState.Bless(1));
        String chosen = GameService.autoRebirthChoice(s);
        assertEquals("MATK", chosen, "应按 BLESS_ORDER 补第一条缺口");
    }

    @Test
    void autoRebirthChoiceAllBlessedChoosesHp() {
        PlayerState s = NewState.create();
        for (String k : GameConst.BLESS_ORDER) {
            s.blessings.put(k, new PlayerState.Bless(1));
        }
        assertEquals("HP", GameService.autoRebirthChoice(s), "强化纪元(全赋能)应永远强化生命");
    }
}
