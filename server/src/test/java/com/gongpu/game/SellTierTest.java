package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.ApiException;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分级售卖锁（玩家需求）：手动一键卖分白/绿/蓝/紫四档，
 * 挂机自动售卖（掉落拦截）同步扩到紫档——选哪档，哪档及以下不进背包。
 * 品质序：0 普通（白）/1 精良（绿）/2 稀有（蓝）/3 史诗（紫），售价 20×(品质+1)。
 */
class SellTierTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private final GameService game = new GameService();

    private PlayerState.Item item(int quality) {
        PlayerState.Item it = new PlayerState.Item();
        it.uid = quality + 1;
        it.name = "测试装备" + quality;
        it.slot = "sword";
        it.quality = quality;
        return it;
    }

    private PlayerState stateWithAllTiers() {
        PlayerState s = NewState.create();
        for (int q = 0; q <= 3; q++) {
            s.equipBag.add(item(q));
        }
        return s;
    }

    /** 手动卖绿档：白+绿卖掉（20+40 金），蓝与紫保留。 */
    @Test
    void manualSellKeepsAboveChosenTier() {
        PlayerState s = stateWithAllTiers();
        BigDecimal goldBefore = s.gold;
        Map<String, Object> r = game.autoSell(s, 2);
        assertEquals(2, r.get("soldCount"));
        assertEquals(2, s.equipBag.size());
        assertTrue(s.equipBag.stream().allMatch(it -> it.quality >= 2), "蓝紫必须保留");
        assertEquals(0, s.gold.subtract(goldBefore).compareTo(Num.of(60)));
    }

    /** 手动卖紫档 = 全卖，含史诗；keep 越界仍拒绝。 */
    @Test
    void manualSellPurpleTierSellsEverything() {
        PlayerState s = stateWithAllTiers();
        BigDecimal goldBefore = s.gold;
        Map<String, Object> r = game.autoSell(s, 4);
        assertEquals(4, r.get("soldCount"));
        assertTrue(s.equipBag.isEmpty(), "紫及以下即全部装备，必须卖空");
        assertEquals(0, s.gold.subtract(goldBefore).compareTo(Num.of(200)));
        assertThrows(ApiException.class, () -> game.autoSell(s, 5));
    }

    /** 挂机自动售卖档位放宽到 4（紫及以下全折现），钳制同步放宽。 */
    @Test
    void autoSellKeepClampsToFour() {
        PlayerState s = NewState.create();
        game.setAutoLines(s, null, null, null, null, null, null, null, 4, null, null, null, null);
        assertEquals(4, s.autoSellKeep);
        game.setAutoLines(s, null, null, null, null, null, null, null, 99, null, null, null, null);
        assertEquals(4, s.autoSellKeep, "超限值应钳到最高档 4");
    }

    /** 掉落拦截紫档：大额溢出击杀后背包零新增（掉落全折现）。 */
    @Test
    void dropInterceptAtPurpleTierLetsNothingIntoBag() {
        PlayerState s = NewState.create();
        s.zone = "plain";
        GameEngine.spawnMob(s, "plain");
        s.autoSellKeep = 4;         // 紫及以下全折现
        s.newbieWeaponsGiven = 5;   // 新手保底是福利直入包，与拦截无关，先置已发满
        s.ringGranted = true;
        int bagBefore = s.equipBag.size();

        GameEngine.dealDamage(s, PanelCalculator.of(s), new BigDecimal("1e9"), 1.0);

        assertTrue(Num.gt(s.stats.kills, Num.of(100)), "溢出分支应真实结算击杀");
        assertEquals(bagBefore, s.equipBag.size(),
                "紫档拦截下任何品质掉落都不应进背包（最高品质 3 < 保留档 4）");
    }
}
