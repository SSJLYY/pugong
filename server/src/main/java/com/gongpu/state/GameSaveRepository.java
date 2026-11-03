package com.gongpu.state;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GameSaveRepository extends JpaRepository<GameSave, Long> {

    List<GameSave> findByUserIdOrderBySlotNoAsc(Long userId);

    Optional<GameSave> findByUserIdAndSlotNo(Long userId, Integer slotNo);

    /** 运营统计:今日有结算的存档数(DB 端 count,替代全表载内存过滤)。 */
    long countByUpdatedAtAfter(Instant t);

    /**
     * 运营统计聚合：全服四项累计一次 SUM 取回（冗余列由 SaveService.persist 维护），
     * 替代全表拉 LONGTEXT 逐档解析 JSON。返回单行 [punches, kills, deaths, rebirths]。
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(SUM(g.punchesTotal), 0), COALESCE(SUM(g.killsTotal), 0), "
                    + "COALESCE(SUM(g.deathsTotal), 0), COALESCE(SUM(g.rebirthsTotal), 0) FROM GameSave g")
    Object[] sumStatsTotals();
}
