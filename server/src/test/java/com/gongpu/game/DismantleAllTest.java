package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第六批第 35 轮：一键分解——按档位批量分解（锁定件跳过，产出按来源区域汇总）。
 */
class DismantleAllTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private PlayerState.Item item(int quality, String zone, boolean locked) {
        PlayerState.Item it = new PlayerState.Item();
        it.name = "测试装";
        it.quality = quality;
        it.zone = zone;
        it.locked = locked;
        return it;
    }

    @Test
    void tieredDismantleSkipsLocked() {
        PlayerState s = NewState.create();
        s.equipBag.clear();
        s.equipBag.add(item(0, "forest", false));   // 白·森林 → 狼牙 ×1
        s.equipBag.add(item(1, "forest", false));   // 绿·森林 → 狼牙 ×2
        s.equipBag.add(item(3, "mine", false));     // 紫·矿洞 → 档位 3 不动
        s.equipBag.add(item(0, "plain", true));     // 白·锁定 → 跳过
        GameService gs = new GameService();
        Map<String, Object> r = gs.dismantleAll(s, 2);
        assertEquals(2, r.get("dismantledCount"), "应分解 2 件");
        assertEquals(2, s.equipBag.size(), "紫装与锁定件应留在背包");
        assertEquals(3, s.materials.getOrDefault("wolf_fang", 0), "狼牙应 1+2=3");
        assertEquals(2L, s.stats.dismantles, "终身分解计数应 +2");
        assertTrue(String.valueOf(r.get("yieldsText")).contains("狼牙×3"), "汇总文案应含狼牙×3：" + r.get("yieldsText"));
    }

    @Test
    void invalidKeepRejected() {
        PlayerState s = NewState.create();
        GameService gs = new GameService();
        assertThrows(RuntimeException.class, () -> gs.dismantleAll(s, 5), "keep=5 应被拒");
    }
}
