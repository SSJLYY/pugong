package com.gongpu.cfg;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 合成配方主表：图纸 + 材料 + 金币 → 指定底子装备（词条仍随机，DESIGN.md §6/§9）。 */
@Entity
@Table(name = "cfg_craft")
public class CraftTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 32)
    private String name;

    @Column(length = 8)
    private String emoji;

    /** 产出的装备模板 code（cfg_equipment_template.code）。 */
    @Column(name = "product_code", nullable = false, length = 32)
    private String productCode;

    /** 所需材料 JSON，如 {"ore":5,"wolf_fang":3}（key 为材料 code）。 */
    @Column(name = "mats_json", nullable = false, columnDefinition = "TEXT")
    private String matsJson = "{}";

    /** 所需金币。 */
    @Column(name = "gold_cost", nullable = false)
    private Integer goldCost = 0;

    /** 是否需要图纸（true：消耗对应 code 的图纸 1 张）。 */
    @Column(name = "need_blueprint", nullable = false)
    private Boolean needBlueprint = true;

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
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getMatsJson() { return matsJson; }
    public void setMatsJson(String matsJson) { this.matsJson = matsJson; }
    public Integer getGoldCost() { return goldCost; }
    public void setGoldCost(Integer goldCost) { this.goldCost = goldCost; }
    public Boolean getNeedBlueprint() { return needBlueprint; }
    public void setNeedBlueprint(Boolean needBlueprint) { this.needBlueprint = needBlueprint; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
