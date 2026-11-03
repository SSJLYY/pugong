package com.gongpu.quest;

import com.gongpu.cfg.GameData;
import com.gongpu.cfg.QuestTemplate;
import com.gongpu.cfg.QuestTemplateRepository;
import com.gongpu.common.ApiException;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 第三批第 9 轮：QuestService 首个单测（此前零覆盖）——接取基线/进度/交付发奖全链路，
 * 顺带锁定 reqJson 解析缓存不改变语义（门槛判定一致）。
 */
class QuestServiceTest {

    private QuestService service;

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    private QuestService serviceWith(QuestTemplate t) {
        QuestTemplateRepository repo = mock(QuestTemplateRepository.class);
        when(repo.findByCode(t.getCode())).thenReturn(Optional.of(t));
        when(repo.findByEnabledTrueOrderBySortNoAscIdAsc()).thenReturn(List.of(t));
        return new QuestService(repo);
    }

    private QuestTemplate template(String reqJson) {
        QuestTemplate t = new QuestTemplate();
        t.setCode("q_test");
        t.setName("测试委托");
        t.setEmoji("📜");
        t.setTargetZone("plain");
        t.setReqJson(reqJson);
        t.setTargetKills(3);
        t.setRewardGold(100);
        t.setRepeatable(true);
        t.setEnabled(true);
        return t;
    }

    @Test
    void acceptProgressDeliverFlow() {
        service = serviceWith(template("{}"));
        PlayerState s = NewState.create();
        s.gold = BigDecimal.ZERO;
        service.accept(s, "q_test");
        assertTrue(s.activeQuests.containsKey("q_test"), "接取后应记录击杀基线");
        // 未达标交付被拒
        assertThrows(ApiException.class, () -> service.deliver(s, "q_test"));
        s.stats.kills = s.stats.kills.add(new BigDecimal("3"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) service.questBoard(s).get("rows");
        assertEquals(Boolean.TRUE, rows.get(0).get("ready"), "达标后委托板应标记可交付");
        service.deliver(s, "q_test");
        assertEquals(0, s.gold.compareTo(new BigDecimal("100")), "交付应发 100 金");
        assertTrue(!s.activeQuests.containsKey("q_test"), "交付后应移出进行中");
        assertTrue(s.doneQuests.contains("q_test"), "应记入已完成");
    }

    @Test
    void reqGateBlocksUnderpowered() {
        service = serviceWith(template("{\"maxHP\": 999999999}"));
        PlayerState s = NewState.create();
        assertThrows(ApiException.class, () -> service.accept(s, "q_test"),
                "生命上限门槛不达应拒接");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) service.questBoard(s).get("rows");
        assertTrue(String.valueOf(rows.get(0).get("rejectReason")).contains("属性不足"));
    }

    @Test
    void parseJsonCacheConsistent() {
        Map<String, Object> a = QuestService.parseJson("{\"maxHP\": 500}");
        Map<String, Object> b = QuestService.parseJson("{\"maxHP\": 500}");
        assertEquals(a, b);
        assertEquals(500, ((Number) a.get("maxHP")).intValue());
        assertTrue(QuestService.parseJson("not json").isEmpty(), "坏 JSON 仍回退空表");
        assertTrue(QuestService.parseJson(null).isEmpty());
    }
}
