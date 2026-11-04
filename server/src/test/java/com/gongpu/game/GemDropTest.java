package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第八批第 3 轮：宝石掉落——Boss 击杀必得 1 颗随机宝石（击杀漏斗单点），
 * randomGem 只产出已定义宝石。
 */
class GemDropTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void randomGemAlwaysDefined() {
        for (int i = 0; i < 200; i++) {
            String g = GameEngine.randomGem();
            assertTrue(GameConst.GEMS.containsKey(g), "随机宝石必须是已定义宝石：" + g);
        }
    }

    @Test
    void bossKillGrantsGem() throws Exception {
        PlayerState s = NewState.create();
        s.zone = "plain";
        s.life.set("PATK", Num.of(100000));
        s.fixed.CRIT = BigDecimal.ZERO;
        GameEngine.spawnBoss(s);
        PlayerState.MobState boss = s.mob;
        boss.curHP = new BigDecimal("0.1");
        Method kill = GameEngine.class.getDeclaredMethod("onMobKilled",
                PlayerState.class, PlayerState.MobState.class);
        kill.setAccessible(true);
        int before = s.materials.entrySet().stream()
                .filter(e -> e.getKey().startsWith("gem_")).mapToInt(e -> e.getValue()).sum();
        kill.invoke(null, s, boss);
        int after = s.materials.entrySet().stream()
                .filter(e -> e.getKey().startsWith("gem_")).mapToInt(e -> e.getValue()).sum();
        assertEquals(before + 1, after, "Boss 击杀应必掉 1 颗宝石");
    }
}
