package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 第五批第 2 轮：自动分解——掉落拦截决策（分解优先于售卖）与配置钳制。
 */
class AutoSmeltTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState.Item item(int quality) {
        PlayerState.Item it = new PlayerState.Item();
        it.name = "测试装";
        it.slot = "weapon";
        it.zone = "snow";
        it.quality = quality;
        return it;
    }

    @Test
    void interceptMatrix() {
        PlayerState s = NewState.create();
        assertEquals("bag", GameService.dropIntercept(s, item(0)), "双关时一律入包");
        s.autoSellKeep = 4;
        assertEquals("sell", GameService.dropIntercept(s, item(0)), "仅售卖开 → 折现");
        s.autoSmeltKeep = 2;
        assertEquals("smelt", GameService.dropIntercept(s, item(1)), "绿及以下 → 分解优先");
        assertEquals("sell", GameService.dropIntercept(s, item(3)), "紫装不在分解档内 → 落到售卖档（全折现）");
        s.autoSellKeep = 0;
        assertEquals("bag", GameService.dropIntercept(s, item(3)), "紫装双不命中 → 入包");
    }

    @Test
    void grantDismantleAccumulates() {
        PlayerState s = NewState.create();
        GameService.grantDismantle(s, item(2));
        GameService.grantDismantle(s, item(2));
        assertEquals(6, s.materials.getOrDefault("mana_dust", 0), "两件蓝雪原装各出 3 魔尘");
        assertEquals(2, s.stats.dismantles);
    }

    @Test
    void configClamp() {
        PlayerState s = NewState.create();
        GameService g = new GameService();
        g.setAutoLines(s, null, null, null, null, null, null, null, null, null, null, 99);
        assertEquals(4, s.autoSmeltKeep, "自动分解档位应钳制到 4");
        g.setAutoLines(s, null, null, null, null, null, null, null, null, null, null, -2);
        assertEquals(0, s.autoSmeltKeep, "负档位应钳制到 0");
    }
}
