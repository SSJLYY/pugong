package com.gongpu.state;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 墓园碑：阵亡黑碑 / 飞升金碑。纯纪念，无数值加成。 */
@Entity
@Table(name = "game_grave")
public class Grave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** death = 黑碑，ascend = 金碑。 */
    @Column(name = "kind", nullable = false, length = 16)
    private String kind;

    @Column(name = "gen", nullable = false)
    private Integer gen = 0;

    @Column(name = "killer", length = 64)
    private String killer;

    @Column(name = "final_hp", length = 80)
    private String finalHp = "0";

    @Column(name = "lived_sec", nullable = false)
    private Long livedSec = 0L;

    @Column(name = "punches", length = 80)
    private String punches = "0";

    @Column(name = "epitaph", length = 120)
    private String epitaph;

    /** 被祭奠次数（世界墓园热度）。 */
    @Column(name = "tributes", nullable = false)
    private Integer tributes = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public Integer getGen() { return gen; }
    public void setGen(Integer gen) { this.gen = gen; }
    public String getKiller() { return killer; }
    public void setKiller(String killer) { this.killer = killer; }
    public String getFinalHp() { return finalHp; }
    public void setFinalHp(String finalHp) { this.finalHp = finalHp; }
    public Long getLivedSec() { return livedSec; }
    public void setLivedSec(Long livedSec) { this.livedSec = livedSec; }
    public String getPunches() { return punches; }
    public void setPunches(String punches) { this.punches = punches; }
    public String getEpitaph() { return epitaph; }
    public void setEpitaph(String epitaph) { this.epitaph = epitaph; }
    public Integer getTributes() { return tributes; }
    public void setTributes(Integer tributes) { this.tributes = tributes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
