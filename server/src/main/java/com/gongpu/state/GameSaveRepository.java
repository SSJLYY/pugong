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
}
