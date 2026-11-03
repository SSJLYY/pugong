package com.gongpu.stats;

import com.gongpu.account.UserRepository;
import com.gongpu.common.Num;
import com.gongpu.state.GameSaveRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 每日运营统计聚合（DESIGN.md 运营章节）：每小时把当日累计值快照进 stat_daily。
 * <p>口径：punches/kills/deaths/rebirths 取 game_save 冗余列 SUM（persist 时与 JSON 内
 * stats 同步维护；当日值 = 累计 - 昨日快照）；active_saves = 有当日结算的存档数（updated_at 今日）。</p>
 */
@Component
public class StatDailyAggregator {

    private static final Logger log = LoggerFactory.getLogger(StatDailyAggregator.class);

    private final StatDailyRepository stats;
    private final GameSaveRepository saves;
    private final UserRepository users;

    /** 昨日快照（进程内存缓存，重启后从 DB 重建）。 */
    private final AtomicReference<Snapshot> lastSnap = new AtomicReference<>(new Snapshot("0", "0", 0L, 0L));

    public StatDailyAggregator(StatDailyRepository stats, GameSaveRepository saves, UserRepository users) {
        this.stats = stats;
        this.saves = saves;
        this.users = users;
        rebuildFromDb();
    }

    public record Snapshot(String punches, String kills, long deaths, long rebirths) {
    }

    private void rebuildFromDb() {
        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            StatDaily prev = stats.findById(yesterday).orElse(null);
            if (prev != null) {
                lastSnap.set(new Snapshot(prev.getPunches(), prev.getKills(), prev.getDeaths(), prev.getRebirths()));
            }
        } catch (RuntimeException e) {
            log.warn("stat_daily 重建快照失败：{}", e.getMessage());
        }
    }

    /** 每小时聚合一次（当日行 upsert）。 */
    @Scheduled(fixedDelay = 3600_000L, initialDelay = 30_000L)
    @Transactional
    public void aggregate() {
        try {
            Snapshot cur = collect();
            LocalDate today = LocalDate.now();

            // 当日增量 = 当前累计 - 昨日快照（不足 0 取 0）
            Delta d = delta(cur, lastSnap.get());

            // active_saves：今日有结算（updated_at 今日）的存档数(DB 端 count,不再全表拉内存)
            java.time.Instant todayStart = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
            long activeSaves = saves.countByUpdatedAtAfter(todayStart);

            StatDaily row = stats.findById(today).orElseGet(() -> {
                StatDaily n = new StatDaily(today);
                n.setPunches("0");
                n.setKills("0");
                n.setDeaths(0L);
                n.setRebirths(0L);
                n.setActiveSaves(0L);
                return n;
            });
            row.setPunches(Num.plain(d.punches()));
            row.setKills(Num.plain(d.kills()));
            row.setDeaths(d.deaths());
            row.setRebirths(d.rebirths());
            row.setActiveSaves(activeSaves);
            stats.save(row);
            log.debug("stat_daily 聚合完成：{} punches={} kills={}", today, row.getPunches(), row.getKills());
        } catch (RuntimeException e) {
            log.warn("stat_daily 聚合失败：{}", e.getMessage());
        }
    }

    /** 增量 = 当前累计 - 基线快照（不足 0 取 0）。aggregate 与 rollDay 共用同一口径。 */
    private record Delta(java.math.BigDecimal punches, java.math.BigDecimal kills, long deaths, long rebirths) {
    }

    private static Delta delta(Snapshot cur, Snapshot base) {
        java.math.BigDecimal punches = Num.max(Num.ZERO, Num.sub(Num.parse(cur.punches(), Num.ZERO), Num.parse(base.punches(), Num.ZERO)));
        java.math.BigDecimal kills = Num.max(Num.ZERO, Num.sub(Num.parse(cur.kills(), Num.ZERO), Num.parse(base.kills(), Num.ZERO)));
        long deaths = Math.max(0, cur.deaths() - base.deaths());
        long rebirths = Math.max(0, cur.rebirths() - base.rebirths());
        return new Delta(punches, kills, deaths, rebirths);
    }

    /** 全服累计采集：冗余列一条 SUM 取回（persist 维护），不再拉全表 LONGTEXT 解析 JSON。 */
    Snapshot collect() {
        try {
            Object[] row = saves.sumStatsTotals();
            if (row == null || row.length < 4) {
                return new Snapshot("0", "0", 0L, 0L);
            }
            return new Snapshot(
                    Num.plain(asDecimal(row[0])),
                    Num.plain(asDecimal(row[1])),
                    asLong(row[2]),
                    asLong(row[3]));
        } catch (RuntimeException e) {
            log.warn("stat_daily 采集失败：{}", e.getMessage());
            return new Snapshot("0", "0", 0L, 0L);
        }
    }

    private static java.math.BigDecimal asDecimal(Object v) {
        if (v == null) {
            return Num.ZERO;
        }
        if (v instanceof java.math.BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return new java.math.BigDecimal(n.toString());
        }
        return Num.parse(String.valueOf(v), Num.ZERO);
    }

    private static long asLong(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    /** 日切：午夜后把昨日行定为最终值。与 aggregate() 同口径写「当日增量」，
     *  而不是把全服累计值冲进去（否则已完成日的运营数据全部失真）。 */
    @Scheduled(cron = "0 1 0 * * *")
    @Transactional
    public void rollDay() {
        Snapshot cur = collect();
        Delta d = delta(cur, lastSnap.get());
        LocalDate yesterday = LocalDate.now().minusDays(1);
        StatDaily row = stats.findById(yesterday).orElseGet(() -> new StatDaily(yesterday));
        row.setPunches(Num.plain(d.punches()));
        row.setKills(Num.plain(d.kills()));
        row.setDeaths(d.deaths());
        row.setRebirths(d.rebirths());
        stats.save(row);
        lastSnap.set(cur);
    }

    /** 运维端概览:今日运营行(无结算返回 null)。 */
    public StatDaily today() {
        return stats.findById(LocalDate.now()).orElse(null);
    }

    /** 运维端看板查询：最近 30 天。 */
    public java.util.List<java.util.Map<String, Object>> dashboard() {
        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        for (StatDaily d : stats.findTop30ByDayLessThanEqualOrderByDayDesc(LocalDate.now())) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("day", d.getDay());
            m.put("punches", d.getPunches());
            m.put("kills", d.getKills());
            m.put("deaths", d.getDeaths());
            m.put("rebirths", d.getRebirths());
            m.put("activeSaves", d.getActiveSaves());
            rows.add(m);
        }
        return rows;
    }
}
