package com.gongpu.cfg;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 商品主表：商店可购物品（药剂/消耗品）。 */
@Entity
@Table(name = "cfg_item")
public class ItemTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 32)
    private String name;

    @Column(length = 8)
    private String emoji;

    @Column(nullable = false, length = 16)
    private String kind = "potion";

    @Column(nullable = false)
    private Integer price = 0;

    /** 效果 JSON：{"healHP":0.2} / {"buff":"spd_pot","buffVal":0.2,"buffDur":30} 等。 */
    @Column(name = "effect_json", nullable = false, columnDefinition = "TEXT")
    private String effectJson = "{}";

    @Column(length = 64)
    private String desc;

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
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public Integer getPrice() { return price; }
    public void setPrice(Integer price) { this.price = price; }
    public String getEffectJson() { return effectJson; }
    public void setEffectJson(String effectJson) { this.effectJson = effectJson; }
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
