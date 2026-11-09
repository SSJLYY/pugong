package com.gongpu.state;

import com.gongpu.common.Num;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 墓园：阵亡立黑碑，飞升立金碑。纯纪念，不提供任何数值加成。 */
@Service
public class GraveService {

    private final GraveRepository repo;

    public GraveService(GraveRepository repo) {
        this.repo = repo;
    }

    /** 独立事务：立碑失败不能影响回档/转生主流程。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void addDeath(long userId, PlayerState s, String epitaph) {
        Grave g = new Grave();
        g.setUserId(userId);
        g.setKind("death");
        g.setGen(s.gen);
        g.setKiller(truncate(s.deathCause, 60));
        g.setFinalHp(Num.plain(s.life.maxHP));
        g.setLivedSec(StateCodec.aliveSeconds(s));
        g.setPunches(Num.plain(s.stats.punches));
        g.setEpitaph(truncate(epitaph, 40));
        g.setCreatedAt(Instant.now());
        repo.save(g);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void addAscend(long userId, PlayerState s, String blessedKey) {
        Grave g = new Grave();
        g.setUserId(userId);
        g.setKind("ascend");
        g.setGen(s.gen);
        g.setKiller("飞升 · " + (com.gongpu.game.GameConst.BLESS_NAME.getOrDefault(blessedKey, blessedKey)));
        g.setFinalHp(Num.plain(s.life.maxHP));
        g.setLivedSec(StateCodec.aliveSeconds(s));
        g.setPunches(Num.plain(s.stats.punches));
        g.setEpitaph(null);
        g.setCreatedAt(Instant.now());
        repo.save(g);
    }

    public List<Map<String, Object>> list(long userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Grave g : repo.findTop50ByUserIdOrderByCreatedAtDesc(userId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", g.getKind());
            m.put("gen", g.getGen());
            m.put("killer", g.getKiller());
            m.put("finalHp", g.getFinalHp());
            m.put("finalHpText", Num.cn(Num.parse(g.getFinalHp(), Num.ZERO)));
            m.put("livedSec", g.getLivedSec());
            m.put("punches", g.getPunches());
            m.put("epitaph", g.getEpitaph());
            m.put("createdAt", g.getCreatedAt());
            out.add(m);
        }
        return out;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        // 第二十批·轮10：墓志铭经免登录世界墓园全服可见，先去控制字符再截断（防换行刷屏/渲染异常）
        s = com.gongpu.common.Texts.stripControl(s);
        return s.length() > max ? s.substring(0, max) : s;
    }
}
