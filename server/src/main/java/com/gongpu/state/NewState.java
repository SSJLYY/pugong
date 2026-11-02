package com.gongpu.state;

import com.gongpu.game.PanelCalculator;

import java.math.BigDecimal;
import java.util.LinkedHashMap;

/** 新档出生：第 0 世面板 + 初始赋能 + 新手礼包。 */
public final class NewState {

    private NewState() {
    }

    public static PlayerState create() {
        PlayerState s = new PlayerState();
        s.gen = 0;
        s.life = Life.fresh();
        s.fixed = Fixed.fresh();
        // 第 0 世仅自带 HP 赋能
        s.blessings = new LinkedHashMap<>();
        s.blessings.put("HP", new PlayerState.Bless(0));
        s.zone = "village";
        s.stats = new PlayerState.Stats();
        s.stats.born = System.currentTimeMillis();
        // 新手礼包：各 5 瓶基础药剂，避免开局无药可嗑
        s.potions.put("hp_s", 5);
        s.potions.put("mp", 3);
        s.lastTickAt = System.currentTimeMillis();
        s.lastSaveAt = s.lastTickAt;
        s.talent = null;               // 首次进游戏触发觉醒仪式
        // 村口木桩
        GameEngineAccess.spawn(s);
        return s;
    }

    /** 避免 NewState 与 GameEngine 循环依赖的小门面。 */
    private static final class GameEngineAccess {
        static void spawn(PlayerState s) {
            com.gongpu.game.GameEngine.spawnMob(s, s.zone);
        }
    }

    /** 出生面板（展示用）。 */
    public static BigDecimal initialMaxHP() {
        return PanelCalculator.of(new PlayerState()).maxHP();
    }
}
