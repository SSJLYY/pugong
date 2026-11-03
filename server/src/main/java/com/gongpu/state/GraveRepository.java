package com.gongpu.state;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GraveRepository extends JpaRepository<Grave, Long> {

    List<Grave> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    /** 世界墓园：按祭奠热度取前 N（热度相同新的在前），避免全表加载内存排序。 */
    List<Grave> findTop50ByOrderByTributesDescCreatedAtDesc();

    /** 圆满金碑判定用：近期金碑（按时间倒序取前 500），聚合每位玩家的飞升世代覆盖。 */
    List<Grave> findTop500ByKindOrderByCreatedAtDesc(String kind);

    /** 原子祭奠 +1：并发祭奠同一墓碑不丢计数（读-改-写会丢）。 */
    @Modifying
    @Query("UPDATE Grave g SET g.tributes = g.tributes + 1 WHERE g.id = :id")
    int addTribute(@Param("id") long id);
}
