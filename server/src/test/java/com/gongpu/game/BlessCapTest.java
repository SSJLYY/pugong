package com.gongpu.game;

import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 赋能到顶置灰(blessOptions,转生/强化界面数据源):
 * 百分比属性(ACC/CRIT/EVA)面板值达到 {@link GameConst#BLESS_CAP} 后必须标 disabled=true,
 * 未满属性可选。两个分支都要锁:
 * <ul>
 *   <li>补缺分支(未集齐):已拥有的/到顶的置灰,未拥有且未满的可选;</li>
 *   <li>强化纪元(集齐 13 条):owned 且未满的可选(用来升强化等级),到顶的仍置灰。</li>
 * </ul>
 */
class BlessCapTest {

    private final GameService game = new GameService();

    private Map<String, Object> opt(List<Map<String, Object>> options, String key) {
        return options.stream()
                .filter(o -> key.equals(o.get("key")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("blessOptions 缺少属性 " + key));
    }

    /** ACC 已到上限(BLESS_CAP=1)→ disabled=true;未满未拥有的 EVA → disabled=false。 */
    @Test
    void cappedAccDisabledWhileUncappedEvaSelectable() {
        PlayerState s = NewState.create();
        s.fixed.ACC = Num.of(1);   // BLESS_CAP.ACC = 1,命中已到顶

        List<Map<String, Object>> options = game.blessOptions(s);

        Map<String, Object> acc = opt(options, "ACC");
        assertEquals(Boolean.TRUE, acc.get("capped"), "ACC 面板值达到 BLESS_CAP 应判 capped");
        assertEquals(Boolean.TRUE, acc.get("disabled"), "到顶属性必须置灰(选了也涨不动)");

        Map<String, Object> eva = opt(options, "EVA");
        assertEquals(Boolean.FALSE, eva.get("capped"), "EVA 默认 0 未到上限(0.95)不 capped");
        assertEquals(Boolean.FALSE, eva.get("disabled"), "未拥有且未满的属性应可选");
    }

    /** 补缺分支:已拥有(HP)但未集齐 → disabled=true(不能再选),与 capped 无关也要置灰。 */
    @Test
    void ownedButNotAllBlessedIsDisabled() {
        PlayerState s = NewState.create();   // 只自带 HP 赋能,未集齐

        List<Map<String, Object>> options = game.blessOptions(s);

        assertEquals(Boolean.TRUE, opt(options, "HP").get("owned"));
        assertEquals(Boolean.TRUE, opt(options, "HP").get("disabled"),
                "补缺分支里已拥有的属性应置灰(转生只能补缺口)");
    }

    /** 强化纪元(集齐 13 条):owned 且未满的可选,到顶的仍置灰。 */
    @Test
    void allBlessedEraRespectsCap() {
        PlayerState s = NewState.create();
        s.fixed.ACC = Num.of(1);   // ACC 到顶
        for (String k : GameConst.BLESS_ORDER) {
            s.blessings.put(k, new PlayerState.Bless(0));
        }

        List<Map<String, Object>> options = game.blessOptions(s);

        assertEquals(Boolean.TRUE, opt(options, "ACC").get("disabled"), "强化纪元到顶属性仍置灰");
        assertEquals(Boolean.FALSE, opt(options, "CRIT").get("disabled"),
                "强化纪元 owned 且未满(CRIT 0.05 < 1)应可选(升强化等级)");
    }
}
