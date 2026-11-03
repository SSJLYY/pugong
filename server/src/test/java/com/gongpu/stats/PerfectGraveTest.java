package com.gongpu.stats;

import com.gongpu.state.Grave;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 圆满金碑判定锁：飞升金碑集齐 12 个不同世代才算圆满，同世重复不计数，黑碑不算。 */
class PerfectGraveTest {

    private Grave grave(long userId, String kind, int gen) {
        Grave g = new Grave();
        g.setUserId(userId);
        g.setKind(kind);
        g.setGen(gen);
        return g;
    }

    @Test
    void twelveDistinctAscendGensMakePerfect() {
        List<Grave> graves = new ArrayList<>();
        for (int gen = 0; gen < 12; gen++) {
            graves.add(grave(1L, "ascend", gen));
        }
        for (int gen = 0; gen < 11; gen++) {
            graves.add(grave(2L, "ascend", gen));
        }
        graves.add(grave(2L, "ascend", 3));   // 同世重复，不补种类
        graves.add(grave(2L, "death", 11));   // 黑碑不算飞升
        Set<Long> perfect = SocialService.perfectUsers(graves);
        assertTrue(perfect.contains(1L), "集齐 12 世金碑应判圆满");
        assertFalse(perfect.contains(2L), "只有 11 个不同飞升世代不应判圆满");
    }
}
