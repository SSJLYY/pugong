package com.gongpu.cfg;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 装备主表：装备模板（底子 + 基础属性 JSON）。词条见 {@link AffixTemplate}。 */
@Entity
@Table(name = "cfg_equipment_template")
public class EquipmentTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 唯一代码，如 iron_sword。 */
    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 32)
    private String name;

    @Column(length = 8)
    private String emoji;

    /** 部位：weapon/offhand/bow/head/chest/legs/boots/ring1/ring2/necklace。 */
    @Column(nullable = false, length = 16)
    private String slot;

    /** 品阶 0-3，越高越稀有（影响掉落筛选与底子档位）。 */
    @Column(nullable = false)
    private Integer tier = 0;

    /** 最低出现区域（掉落筛选用，如 forest）。 */
    @Column(name = "min_zone", length = 16)
    private String minZone;

    /** 底子属性 JSON，如 {"PATK":"8","RNG":"1"}。 */
    @Column(name = "base_stats", nullable = false, columnDefinition = "TEXT")
    private String baseStats = "{}";

    @Column(name = "enhance_max", nullable = false)
    private Integer enhanceMax = 10;

    /** 卖店基础价（品质与强化另算）。 */
    @Column(name = "sell_base", nullable = false)
    private Integer sellBase = 20;

    @Column(length = 64)
    private String remark;

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
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public String getSlot() { return slot; }
    public void setSlot(String slot) { this.slot = slot; }
    public Integer getTier() { return tier; }
    public void setTier(Integer tier) { this.tier = tier; }
    public String getMinZone() { return minZone; }
    public void setMinZone(String minZone) { this.minZone = minZone; }
    public String getBaseStats() { return baseStats; }
    public void setBaseStats(String baseStats) { this.baseStats = baseStats; }
    public Integer getEnhanceMax() { return enhanceMax; }
    public void setEnhanceMax(Integer enhanceMax) { this.enhanceMax = enhanceMax; }
    public Integer getSellBase() { return sellBase; }
    public void setSellBase(Integer sellBase) { this.sellBase = sellBase; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
