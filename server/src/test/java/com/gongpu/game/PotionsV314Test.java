package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v3.14 商店药剂扩容(8→18 种)回归测试:
 * 乘区接线(攻/防/攻速/暴击/暴伤/吸血/命中/闪避/回蓝)与护盾吸收机制。
 */
class PotionsV314Test {

    private final GameService game = new GameService();

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState fresh() {
        return NewState.create();
    }

    // ==================== 商店与兜底表 ====================

    @Test
    void potionTableHas18Entries() {
        // 18 = v3.14 基线（8 旧 + 10 新）；第四批 25 种；第六批 = 34；第七批 = 36；第八批 +破甲 = 37
        assertEquals(39, GameConst.POTIONS.size(), "GameConst 兜底表应有 39 种药剂（第八批终版口径）");
        for (String id : new String[]{"crit", "critd", "lifesteal", "atk", "def", "aspd",
                "acc", "eva", "mregen", "shield"}) {
            assertNotNull(GameConst.POTIONS.get(id), "缺少新药剂: " + id);
        }
    }

    @Test
    void newPotionsBuyAndUse() {
        PlayerState s = fresh();
        s.gold = Num.of(100000);
        // 每种新药剂:买得起 → 买入 → 使用成功 → buff 挂上
        String[] ids = {"crit", "critd", "lifesteal", "atk", "def", "aspd", "acc", "eva", "mregen"};
        for (String id : ids) {
            GameService.buyPotion(s, id, 1);
            Map<String, Object> r = game.usePotion(s, id);
            assertEquals(Boolean.TRUE, r.get("ok"), id + " 使用应成功");
        }
        for (String buff : new String[]{"crit_pot", "critd_pot", "lifesteal_pot", "atk_pot",
                "def_pot", "aspd_pot", "acc_pot", "eva_pot", "mregen_pot"}) {
            assertTrue(s.buffs.get(buff) != null && s.buffs.get(buff) > 0, "buff 未挂上: " + buff);
        }
    }

    // ==================== 乘区接线 ====================

    @Test
    void atkPotionMultipliesAttack() {
        PlayerState s = fresh();
        s.buffs.put("atk_pot", 60.0);
        double before = PanelCalculator.of(s).PATK().doubleValue();
        s.buffs.remove("atk_pot");
        double after = PanelCalculator.of(s).PATK().doubleValue();
        assertEquals(1.5, before / after, 1e-9, "狂暴药剂:攻击 ×1.5");
    }

    @Test
    void defPotionMultipliesBothDefenses() {
        PlayerState s = fresh();
        s.life.set("PDEF", Num.of(100));   // 给基础值,否则 0/0=NaN
        s.life.set("MDEF", Num.of(80));
        s.buffs.put("def_pot", 60.0);
        PanelCalculator.Panel with = PanelCalculator.of(s);
        s.buffs.remove("def_pot");
        PanelCalculator.Panel without = PanelCalculator.of(s);
        assertEquals(1.5, with.PDEF().doubleValue() / without.PDEF().doubleValue(), 1e-9, "磐石:物防 ×1.5");
        assertEquals(1.5, with.MDEF().doubleValue() / without.MDEF().doubleValue(), 1e-9, "磐石:魔防 ×1.5");
    }

    @Test
    void aspdPotionMultipliesAspd() {
        PlayerState s = fresh();
        s.buffs.put("aspd_pot", 60.0);
        double with = PanelCalculator.of(s).ASPD().doubleValue();
        s.buffs.remove("aspd_pot");
        double without = PanelCalculator.of(s).ASPD().doubleValue();
        assertEquals(1.5, with / without, 1e-9, "疾风:攻速 ×1.5");
    }

    @Test
    void critAndCritdAddFlat() {
        PlayerState s = fresh();
        s.buffs.put("crit_pot", 90.0);
        s.buffs.put("critd_pot", 90.0);
        PanelCalculator.Panel with = PanelCalculator.of(s);
        s.buffs.remove("crit_pot");
        s.buffs.remove("critd_pot");
        PanelCalculator.Panel without = PanelCalculator.of(s);
        assertEquals(0.10, with.CRIT().doubleValue() - without.CRIT().doubleValue(), 1e-9, "锐眼:暴击率 +10%");
        assertEquals(0.50, with.CRITD().doubleValue() - without.CRITD().doubleValue(), 1e-9, "残暴:暴击伤害 +50%");
    }

    @Test
    void accEvaLifestealMregenAdd() {
        PlayerState s = fresh();
        s.fixed.ACC = Num.of(0.10);   // 拉开与封顶的距离,加成不被 BLESS_CAP 吃掉
        s.fixed.EVA = Num.of(0.10);
        s.buffs.put("acc_pot", 120.0);
        s.buffs.put("eva_pot", 120.0);
        s.buffs.put("lifesteal_pot", 90.0);
        s.buffs.put("mregen_pot", 120.0);
        PanelCalculator.Panel with = PanelCalculator.of(s);
        s.buffs.clear();
        PanelCalculator.Panel without = PanelCalculator.of(s);
        assertEquals(0.10, with.ACC().doubleValue() - without.ACC().doubleValue(), 1e-9, "明察:命中 +10%");
        assertEquals(0.10, with.EVA().doubleValue() - without.EVA().doubleValue(), 1e-9, "幻影:闪避 +10%");
        assertEquals(0.10, with.LIFESTEAL().doubleValue() - without.LIFESTEAL().doubleValue(), 1e-9, "嗜血:吸血 +10%");
        assertEquals(10.0, with.MREGEN().doubleValue() - without.MREGEN().doubleValue(), 1e-9, "凝神:回蓝 +10/秒");
    }

    @Test
    void silenceSuppressesNewPotionBuffs() {
        PlayerState s = fresh();
        s.buffs.put("atk_pot", 60.0);
        double boosted = PanelCalculator.of(s).PATK().doubleValue();
        s.buffs.put("silence", 5.0);
        double silenced = PanelCalculator.of(s).PATK().doubleValue();
        s.buffs.remove("atk_pot");
        s.buffs.remove("silence");
        double base = PanelCalculator.of(s).PATK().doubleValue();   // 此处才是真裸值(不带任何增益)
        assertEquals(base, silenced, 1e-9, "沉默期间新药剂增益失效(与既有药剂同语义)");
        assertTrue(boosted > base, "无沉默时增益正常");
    }

    // ==================== 护盾机制 ====================

    @Test
    void shieldPotionGrantsAndAbsorbs() {
        PlayerState s = fresh();
        s.gold = Num.of(100000);
        BigDecimal maxHp = PanelCalculator.of(s).maxHP();
        GameService.buyPotion(s, "shield", 1);   // 先买再嗑(直接 usePotion 会报"没有药剂")
        game.usePotion(s, "shield");
        assertEquals(0, s.shield.compareTo(Num.mul(maxHp, Num.of(0.30))), "护盾=30% 生命上限");
        // 吸收:伤害小于盾 → 血不掉、盾减
        BigDecimal dmg = Num.mul(maxHp, Num.of(0.10));
        BigDecimal left = GameEngine.absorbShield(s, dmg);
        assertEquals(0, left.compareTo(BigDecimal.ZERO), "全额被盾吸收");
        assertEquals(0, s.shield.compareTo(Num.mul(maxHp, Num.of(0.20))), "盾余量 30%-10%=20%");
        // 伤害大于剩余盾 → 余量进血
        BigDecimal big = Num.mul(maxHp, Num.of(0.50));
        left = GameEngine.absorbShield(s, big);
        assertEquals(0, left.compareTo(Num.mul(maxHp, Num.of(0.30))), "超出部分 50%-20%=30% 返回扣血");
        assertEquals(0, s.shield.compareTo(BigDecimal.ZERO), "盾耗尽归零");
    }

    @Test
    void shieldExpiresWithBuff() {
        PlayerState s = fresh();
        s.shield = Num.of(999);
        s.buffs.put("shield_pot", 2.0);
        // tickBuff: buff 到期 → 盾清零(反射调用包内方法)
        GameEngine.advance(s, System.currentTimeMillis() + 3000, 1.0, false);
        assertEquals(0, s.shield.compareTo(BigDecimal.ZERO), "shield_pot 到期后护盾清零");
    }

    @Test
    void shieldSurvivesStateRoundTrip() {
        PlayerState s = fresh();
        s.shield = Num.of(12345);
        PlayerState back = com.gongpu.state.StateCodec.decode(com.gongpu.state.StateCodec.encode(s));
        assertEquals(0, back.shield.compareTo(Num.of(12345)), "护盾字段随存档持久化");
    }

    // ==================== 每日签到/经济不受影响 ====================

    @Test
    void oldPotionPricesUnchanged() {
        assertEquals(50, GameConst.POTIONS.get("hp_s").price());
        assertEquals(600, GameConst.POTIONS.get("growth").price());
        assertEquals(400, GameConst.POTIONS.get("ironpot").price(), "旧药剂价格不被扩容改动");
    }
}
