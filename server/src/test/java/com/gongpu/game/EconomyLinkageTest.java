package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.craft.CraftService;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第八批第 48 轮：经济联动回归——买入/卖出价差全链防套利（材料 ×3 买、宝石 ×4 买，
 * 买回即卖必亏）；一键穿套装后 4 件档加成真实进入 activeBonus。
 */
class EconomyLinkageTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void buyThenSellAlwaysLoses() {
        CraftService cs = new CraftService();
        PlayerState s = NewState.create();
        s.gold = Num.of(100000);
        cs.buy(s, "ore", 10);                       // -1800
        cs.sell(s, "ore", 0);                       // +600
        assertEquals(0, s.gold.compareTo(Num.of(98800)), "材料买贵卖贱：净亏 1200");
        cs.buyGem(s, "gem_opal");                   // -2800
        cs.sell(s, "gem_opal", 0);                  // +700
        assertEquals(0, s.gold.compareTo(Num.of(96700)), "宝石买贵卖贱：净亏 2100");
    }

    @Test
    void equipSetActivatesFourPieceBonus() {
        PlayerState s = NewState.create();
        GameService g = new GameService();
        String[][] members = {{"star_blade", "weapon"}, {"nebula_helm", "head"},
                {"galaxy_plate", "chest"}, {"meteor_greaves", "legs"}};
        for (String[] mm : members) {
            PlayerState.Item it = new PlayerState.Item();
            it.code = mm[0];
            it.name = mm[0];
            it.slot = mm[1];
            it.quality = 3;
            s.equipBag.add(it);
        }
        g.equipSet(s, "starset");
        // 套装两档叠加口径（第七批既定）：2 件 +120 与 4 件 +300 同时生效 = 420
        assertEquals(new BigDecimal("420"), Sets.activeBonus(s).get("PATK"), "星辰套装两档叠加应为 420");
        assertEquals(new BigDecimal("0.25"), Sets.activeBonus(s).get("CRITD"));
    }

    @Test
    void apiContractCoversNewEndpoints() throws Exception {
        // 与 ApiContractTest 同源校验：新端点必须同时存在于 Controller 与 api.js
        String ctrl = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/gongpu/web/GameController.java"));
        String api = java.nio.file.Files.readString(java.nio.file.Path.of("../web/js/api.js"));
        for (String ep : new String[]{"buy-material", "buy-gem", "equip-set", "weekly-claim"}) {
            assertTrue(ctrl.contains("/" + ep), "Controller 缺端点 " + ep);
            assertTrue(api.contains("/" + ep), "api.js 缺端点 " + ep);
        }
    }
}
