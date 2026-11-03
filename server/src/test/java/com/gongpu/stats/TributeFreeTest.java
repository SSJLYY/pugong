package com.gongpu.stats;

import com.gongpu.account.User;
import com.gongpu.account.UserRepository;
import com.gongpu.common.ApiException;
import com.gongpu.common.Num;
import com.gongpu.state.GameSaveRepository;
import com.gongpu.state.Grave;
import com.gongpu.state.GraveRepository;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import com.gongpu.state.SaveService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 第七批第 57 轮：每日首祭免费——每档每天第一次祭奠免单（不扣金币、墓主照常得香火），
 * 第二次起恢复 200 金；首祭名额在墓主解析成功后才占用。
 */
class TributeFreeTest {

    private SocialService service;
    private PlayerState actor;

    private SocialService fixture(long actorGold) {
        UserRepository users = mock(UserRepository.class);
        GraveRepository graves = mock(GraveRepository.class);
        GameSaveRepository saves = mock(GameSaveRepository.class);
        SaveService saveService = mock(SaveService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);

        Grave g = new Grave();
        g.setId(7L);
        g.setUserId(99L);
        when(graves.findById(7L)).thenReturn(Optional.of(g));
        User owner = new User();
        owner.setId(99L);
        owner.setUsername("墓主");
        when(users.findById(99L)).thenReturn(Optional.of(owner));

        actor = NewState.create();
        actor.gold = Num.of(actorGold);
        when(saveService.load(1L, 1)).thenReturn(actor);

        service = new SocialService(users, graves, saves, saveService, redis);
        return service;
    }

    @Test
    void firstTributeOfDayIsFree() {
        SocialService svc = fixture(0);   // 身无分文也能完成今日首祭
        Map<String, Object> r = svc.tribute(1L, 7L, 1);
        assertEquals(Boolean.TRUE, r.get("firstFree"));
        assertEquals(0, actor.gold.compareTo(Num.ZERO), "首祭不应扣金币");
        assertTrue(actor.tributeDay != null && !actor.tributeDay.isBlank(), "首祭名额应被占用");
    }

    @Test
    void secondTributeCharges() {
        SocialService svc = fixture(1000);
        svc.tribute(1L, 7L, 1);
        Map<String, Object> r2 = svc.tribute(1L, 7L, 1);
        assertEquals(Boolean.FALSE, r2.get("firstFree"));
        assertEquals(0, actor.gold.compareTo(Num.of(1000 - SocialService.TRIBUTE_COST)),
                "第二次祭奠应扣 200 金");
        // 金币不足时第三次应拒绝（名额已用完，不再免费）
        actor.gold = Num.ZERO;
        assertThrows(ApiException.class, () -> svc.tribute(1L, 7L, 1));
    }
}
