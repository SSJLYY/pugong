package com.gongpu.stats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** 每日运营统计（DESIGN.md 运营章节，定时聚合落库）。 */
@Entity
@Table(name = "stat_daily")
public class StatDaily {

    @Id
    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Column(nullable = false)
    private String punches = "0";

    @Column(nullable = false)
    private String kills = "0";

    @Column(nullable = false)
    private Long deaths = 0L;

    @Column(nullable = false)
    private Long rebirths = 0L;

    @Column(name = "active_saves", nullable = false)
    private Long activeSaves = 0L;

    public StatDaily() {
    }

    public StatDaily(LocalDate day) {
        this.day = day;
    }

    public LocalDate getDay() { return day; }
    public void setDay(LocalDate day) { this.day = day; }
    public String getPunches() { return punches; }
    public void setPunches(String punches) { this.punches = punches; }
    public String getKills() { return kills; }
    public void setKills(String kills) { this.kills = kills; }
    public Long getDeaths() { return deaths; }
    public void setDeaths(Long deaths) { this.deaths = deaths; }
    public Long getRebirths() { return rebirths; }
    public void setRebirths(Long rebirths) { this.rebirths = rebirths; }
    public Long getActiveSaves() { return activeSaves; }
    public void setActiveSaves(Long activeSaves) { this.activeSaves = activeSaves; }
}
