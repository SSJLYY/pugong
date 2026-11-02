package com.gongpu.state;

import com.gongpu.common.Num;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 新档出生不变式(NewState.create(),纯内存无 DB):
 * 第 0 世面板 HP100/MP80、金 0、gen 0、村口、天赋未觉醒、仅 HP 赋能、木桩已刷、新手礼包。
 * <p>注:新手礼包实际为 hp_s=5 / mp=3(NewState.create 写死);
 * "hp_s=3" 是转生(GameService.rebirth)与每日签到(GameController.daily)的发放量,勿混淆。</p>
 */
class NewStateInvariantTest {

    @Test
    void newCharacterInitialInvariants() {
        PlayerState s = NewState.create();

        // 世代与钱包
        assertEquals(0, s.gen, "新档第 0 世");
        assertEquals(0, s.gold.compareTo(Num.ZERO), "初始金币为 0");
        assertFalse(s.dead, "新档非死亡态");

        // 生命/蓝量面板基准
        assertEquals(0, s.life.maxHP.compareTo(Num.of(100)), "初始生命上限 100");
        assertEquals(0, s.life.curHP.compareTo(Num.of(100)), "初始满血");
        assertEquals(0, s.life.MP.compareTo(Num.of(80)), "初始蓝量上限 80");
        assertEquals(0, s.life.curMP.compareTo(Num.of(80)), "初始满蓝");

        // 世界位置与觉醒态
        assertEquals("village", s.zone, "出生在村口");
        assertNull(s.talent, "天赋未觉醒(首次进游戏触发觉醒仪式)");
        assertEquals(1, s.blessings.size(), "第 0 世仅自带一条赋能");
        assertTrue(s.blessings.containsKey("HP"), "先天赋能是 HP");
        assertEquals(0, s.blessings.get("HP").lv, "HP 赋能初始 0 级");

        // 村口木桩已就位(进游戏即可开打)
        assertNotNull(s.mob, "出生即刷出木桩");
        assertEquals("dummy", s.mob.id);
        assertFalse(s.mob.boss, "木桩不是 Boss");

        // 时间戳:落库/结算所需
        assertTrue(s.lastTickAt > 0, "lastTickAt 必须就绪(advance 的时间基准)");
        assertEquals(s.lastTickAt, s.lastSaveAt, "首次落库时间与结算时间一致");

        // 新手礼包(实际语义:hp_s 5 瓶 + mp 3 瓶)
        assertEquals(5, s.potions.get("hp_s"), "新手礼包小血药 5 瓶");
        assertEquals(3, s.potions.get("mp"), "新手礼包蓝药 3 瓶");
        assertEquals(2, s.potions.size(), "礼包之外无其他药剂");
    }
}
