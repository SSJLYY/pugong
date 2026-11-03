package com.gongpu.state;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 一个存档槽。
 * <p>完整状态以 JSON 存 LONGTEXT（大数字段全为字符串），
 * 便于结构演进而不做频繁 DDL 迁移；需要统计的列单独冗余出来建索引。</p>
 */
@Entity
@Table(name = "game_save")
public class GameSave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 槽位号 1~3。 */
    @Column(name = "slot_no", nullable = false)
    private Integer slotNo;

    @Column(name = "slot_name", length = 32)
    private String slotName;

    @Column(name = "gen", nullable = false)
    private Integer gen = 0;

    @Column(name = "zone", length = 16)
    private String zone = "village";

    /** 冗余的生命上限（字符串，供榜单与列表展示）。 */
    @Column(name = "max_hp", length = 80)
    private String maxHp = "100";

    @Column(name = "alive_seconds", nullable = false)
    private Long aliveSeconds = 0L;

    /** 冗余终身出拳/击杀（运营统计 SUM 聚合用，persist 时与 stats 终身字段同步维护；存终身口径因当世值每世重置、聚合需单调）。 */
    @Column(name = "punches_total", precision = 65, scale = 0)
    private java.math.BigDecimal punchesTotal = java.math.BigDecimal.ZERO;

    @Column(name = "kills_total", precision = 65, scale = 0)
    private java.math.BigDecimal killsTotal = java.math.BigDecimal.ZERO;

    @Column(name = "deaths_total", nullable = false)
    private Long deathsTotal = 0L;

    @Column(name = "rebirths_total", nullable = false)
    private Long rebirthsTotal = 0L;

    @Lob
    @Column(name = "state_json", nullable = false, columnDefinition = "LONGTEXT")
    private String stateJson;

    /**
     * 存档点快照（硬核死亡制的核心）。
     * <p>只有「手动存档 / 回村自动存档」才写入这里；死亡回档读的就是它。
     * state_json 则随每次结算滚动覆盖，代表"当前活着的状态"。</p>
     */
    @Lob
    @Column(name = "snapshot_json", columnDefinition = "LONGTEXT")
    private String snapshotJson;

    /**
     * 滚动备份环（第七批）：JSON 数组 [{t,j}…]，最多 3 份主档历史（每 ≥10 分钟轮转一份），
     * 只服务于主档损坏自愈（decodeOrRecover），与死亡回档的 snapshot_json 互不相干。
     */
    @Lob
    @Column(name = "backup_ring", columnDefinition = "LONGTEXT")
    private String backupRing;

    @Column(name = "saved_at")
    private Instant savedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getSlotNo() {
        return slotNo;
    }

    public void setSlotNo(Integer slotNo) {
        this.slotNo = slotNo;
    }

    public String getSlotName() {
        return slotName;
    }

    public void setSlotName(String slotName) {
        this.slotName = slotName;
    }

    public Integer getGen() {
        return gen;
    }

    public void setGen(Integer gen) {
        this.gen = gen;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public String getMaxHp() {
        return maxHp;
    }

    public void setMaxHp(String maxHp) {
        this.maxHp = maxHp;
    }

    public Long getAliveSeconds() {
        return aliveSeconds;
    }

    public void setAliveSeconds(Long aliveSeconds) {
        this.aliveSeconds = aliveSeconds;
    }

    public String getStateJson() {
        return stateJson;
    }

    public void setStateJson(String stateJson) {
        this.stateJson = stateJson;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public String getBackupRing() {
        return backupRing;
    }

    public void setBackupRing(String backupRing) {
        this.backupRing = backupRing;
    }

    public Instant getSavedAt() {
        return savedAt;
    }

    public void setSavedAt(Instant savedAt) {
        this.savedAt = savedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public java.math.BigDecimal getPunchesTotal() {
        return punchesTotal;
    }

    public void setPunchesTotal(java.math.BigDecimal punchesTotal) {
        this.punchesTotal = punchesTotal;
    }

    public java.math.BigDecimal getKillsTotal() {
        return killsTotal;
    }

    public void setKillsTotal(java.math.BigDecimal killsTotal) {
        this.killsTotal = killsTotal;
    }

    public Long getDeathsTotal() {
        return deathsTotal;
    }

    public void setDeathsTotal(Long deathsTotal) {
        this.deathsTotal = deathsTotal;
    }

    public Long getRebirthsTotal() {
        return rebirthsTotal;
    }

    public void setRebirthsTotal(Long rebirthsTotal) {
        this.rebirthsTotal = rebirthsTotal;
    }
}
