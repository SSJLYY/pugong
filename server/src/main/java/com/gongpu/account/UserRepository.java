package com.gongpu.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /** 运维端用户列表:DB 端截断 500 条,账号量增长后不再全表载内存。 */
    java.util.List<User> findTop500ByOrderByIdAsc();

    java.util.List<User> findTop500ByUsernameContainingOrderByIdAsc(String username);

    /**
     * 原子领取每日签到：仅当今日尚未领取时置 lastClaimDay=today。
     * 返回影响行数（0 = 今天已领过），彻底避免"同账号两槽并发签到重复领奖"。
     */
    @Modifying
    @Query("UPDATE User u SET u.lastClaimDay = :day " +
           "WHERE u.id = :id AND (u.lastClaimDay IS NULL OR u.lastClaimDay < :day)")
    int claimDaily(@Param("id") long id, @Param("day") LocalDate day);

    /** 原子扣香火(复活):余额不足影响 0 行,两槽并发复活不可能双双成功。 */
    @Modifying
    @Query("UPDATE User u SET u.incense = u.incense - :cost WHERE u.id = :id AND u.incense >= :cost")
    int spendIncense(@Param("id") long id, @Param("cost") int cost);

    /** 原子加香火(被祭奠):并发祭奠不丢香火。 */
    @Modifying
    @Query("UPDATE User u SET u.incense = u.incense + :delta WHERE u.id = :id")
    int addIncense(@Param("id") long id, @Param("delta") int delta);

    /** 原子记切磋战绩。 */
    @Modifying
    @Query("UPDATE User u SET u.pvpWins = u.pvpWins + 1 WHERE u.id = :id")
    int addPvpWin(@Param("id") long id);

    /** 原子记切磋战绩。 */
    @Modifying
    @Query("UPDATE User u SET u.pvpLosses = u.pvpLosses + 1 WHERE u.id = :id")
    int addPvpLoss(@Param("id") long id);
}
