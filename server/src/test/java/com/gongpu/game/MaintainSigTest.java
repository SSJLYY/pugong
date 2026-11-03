package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** 第六批第 69 轮：自动管护签名——构成不变同签名，击杀/背包变化即变。 */
class MaintainSigTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void signatureTracksComposition() {
        PlayerState s = NewState.create();
        long a = GameService.maintainSig(s);
        assertEquals(a, GameService.maintainSig(s), "构成不变签名应稳定");
        s.stats.kills = BigDecimal.valueOf(3);
        long b = GameService.maintainSig(s);
        assertNotEquals(a, b, "击杀变化（新掉落）签名应变");
        PlayerState.Item it = new PlayerState.Item();
        it.name = "测试剑";
        it.slot = "weapon";
        s.equipBag.add(it);
        assertNotEquals(b, GameService.maintainSig(s), "背包件数变化签名应变");
    }

    @Test
    void autoMaintainSkipsWhenSignatureFresh() {
        PlayerState s = NewState.create();
        GameService.autoMaintain(s);            // 首帧执行并记录签名
        long sig = s.lastMaintainSig;
        assertEquals(GameService.maintainSig(s), sig, "执行后应记录当前签名");
        GameService.autoMaintain(s);            // 无变化帧：直接跳过（不抛错、签名不动）
        assertEquals(sig, s.lastMaintainSig);
    }
}
