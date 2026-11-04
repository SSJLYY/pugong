package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 第八批第 83 轮：被动·宝石嗅觉——词条怪宝石掉率 10%→15%（×1.5），沉默压制回落。
 */
class GemSnifferTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void rateBoostedUnlessSilenced() {
        PlayerState s = NewState.create();
        assertEquals(0.10, GameEngine.gemDropRate(s), 1e-9);
        s.passives.add("gem_sniffer");
        assertEquals(0.15, GameEngine.gemDropRate(s), 1e-9, "学宝石嗅觉后掉率应 ×1.5");
        s.buffs.put("silence", 5.0);
        assertEquals(0.10, GameEngine.gemDropRate(s), 1e-9, "沉默应压制被动回落基础率");
        assertEquals(32, GameConst.PASSIVE_SKILLS.size() + GameConst.ACTIVE_SKILLS.size(),
                "技能总数应为 32（主动 14 + 被动 18）");
    }
}
