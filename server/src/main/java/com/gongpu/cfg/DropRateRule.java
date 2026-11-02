package com.gongpu.cfg;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 掉落规则表：按区域的装备爆率（运维端可直接调）。 */
@Entity
@Table(name = "cfg_drop_rate")
public class DropRateRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 区域：village/plain/forest/mine/swamp/snow/volcano；boss 表示 Boss 通用规则。 */
    @Column(nullable = false, unique = true, length = 16)
    private String zone;

    /** 掉落概率 0~1。 */
    @Column(nullable = false)
    private Double rate = 0.0;

    /** 单次结算溢出击杀的期望掉落上限（防通胀护栏）。 */
    @Column(name = "batch_cap", nullable = false)
    private Integer batchCap = 3;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
    public Double getRate() { return rate; }
    public void setRate(Double rate) { this.rate = rate; }
    public Integer getBatchCap() { return batchCap; }
    public void setBatchCap(Integer batchCap) { this.batchCap = batchCap; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
