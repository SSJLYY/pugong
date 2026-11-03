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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第六批第 73 轮：手动施法回归——六个元老主动的 buff/扣蓝/CD/熟练度与前置校验。 */
class ManualCastTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private static PlayerState caster() {
        PlayerState s = NewState.create();
        s.zone = "plain";
        s.life.maxHP = new BigDecimal("10000");
        s.life.curHP = new BigDecimal("10000");
        s.life.curMP = new BigDecimal("10000");
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = "cast_dummy";
        m.name = "施法靶";
        m.maxHP = Num.of(1000);
        m.curHP = Num.of(1000);
        m.PATK = Num.ZERO;
        m.MATK = Num.ZERO;
        m.PDEF = Num.ZERO;
        m.MDEF = Num.ZERO;
        m.SPD = 1;   // 慢于玩家，怒吼可生效
        s.mob = m;
        return s;
    }

    @Test
    void buffSkillsApplyBuffDeductMpAndSetCd() {
        for (String id : new String[]{"rapid", "block", "surge", "heavy"}) {
            PlayerState s = caster();
            BigDecimal mpBefore = s.life.curMP;
            Map<String, Object> r = GameService.castSkill(s, id);
            assertEquals(Boolean.TRUE, r.get("ok"), id);
            assertTrue(PanelCalculator.active(s, id), id + " 应挂上同名 buff");
            assertTrue(Num.lt(s.life.curMP, mpBefore), id + " 应扣蓝");
            assertTrue(s.cds.getOrDefault(id, 0.0) > 0, id + " 应进入冷却");
            assertEquals(0, BigDecimal.ONE.compareTo(s.mastery.get(id)), id + " 熟练度应 +1");
            // 冷却中再放：拒绝且不再扣蓝
            BigDecimal mpMid = s.life.curMP;
            assertThrows(ApiException.class, () -> GameService.castSkill(s, id), id + " 冷却中应拒绝");
            assertEquals(0, mpMid.compareTo(s.life.curMP), id + " 拒绝时不应扣蓝");
        }
    }

    @Test
    void burnCostsQuarterHp() {
        PlayerState s = caster();
        GameService.castSkill(s, "burn");
        assertEquals(0, new BigDecimal("7500").compareTo(s.life.curHP), "燃烧应扣当前生命 25%");
        assertTrue(PanelCalculator.active(s, "burn"));
    }

    @Test
    void roarSwapsMobWhenFaster() {
        PlayerState s = caster();
        PlayerState.MobState old = s.mob;
        GameService.castSkill(s, "roar");
        assertTrue(s.mob != null && s.mob != old, "怒吼应换掉当前怪");
    }

    @Test
    void roarRejectedWhenSlowerWithoutCost() {
        PlayerState s = caster();
        s.mob.SPD = 99999;   // 怪比玩家快得多
        BigDecimal mpBefore = s.life.curMP;
        assertThrows(ApiException.class, () -> GameService.castSkill(s, "roar"));
        assertEquals(0, mpBefore.compareTo(s.life.curMP), "前置校验失败不应扣蓝");
        assertTrue(s.cds.getOrDefault("roar", 0.0) == 0, "前置校验失败不应进 CD");
    }
}
