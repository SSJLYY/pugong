package com.gongpu.cfg;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 词条主表：随机词缀池（装备的灵魂）。 */
@Entity
@Table(name = "cfg_affix")
public class AffixTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 32)
    private String name;

    /** 属性键：PATK/MATK/ASPD/CRIT/LIFESTEAL... */
    @Column(name = "stat_key", nullable = false, length = 24)
    private String statKey;

    /** flat=绝对值 / pct=百分比加成。 */
    @Column(nullable = false, length = 8)
    private String statType = "flat";

    @Column(nullable = false)
    private Double minVal = 0.0;

    @Column(nullable = false)
    private Double maxVal = 0.0;

    /** 稀有度：common/uncommon/rare/epic。 */
    @Column(length = 16)
    private String rarity = "common";

    /** 负面副作用键（如重甲 -3% 移速）。 */
    @Column(name = "side_key", length = 24)
    private String sideKey;

    @Column(name = "side_val")
    private Double sideVal;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatKey() { return statKey; }
    public void setStatKey(String statKey) { this.statKey = statKey; }
    public String getStatType() { return statType; }
    public void setStatType(String statType) { this.statType = statType; }
    public Double getMinVal() { return minVal; }
    public void setMinVal(Double minVal) { this.minVal = minVal; }
    public Double getMaxVal() { return maxVal; }
    public void setMaxVal(Double maxVal) { this.maxVal = maxVal; }
    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }
    public String getSideKey() { return sideKey; }
    public void setSideKey(String sideKey) { this.sideKey = sideKey; }
    public Double getSideVal() { return sideVal; }
    public void setSideVal(Double sideVal) { this.sideVal = sideVal; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
