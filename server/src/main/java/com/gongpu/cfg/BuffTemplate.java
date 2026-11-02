package com.gongpu.cfg;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 状态主表：增益/减益效果定义（燃烧、沉默、中毒、灼烧、格挡等）。 */
@Entity
@Table(name = "cfg_buff")
public class BuffTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 32)
    private String name;

    @Column(length = 8)
    private String emoji;

    /** good=增益 / bad=减益。 */
    @Column(nullable = false, length = 8)
    private String nature = "good";

    /** passive=技能药剂持续增益 / status=战斗持续状态。 */
    @Column(nullable = false, length = 8)
    private String groupType = "passive";

    /** 效果 JSON：{"atkMul":2} / {"dotPct":0.02} / {"silence":true} 等。 */
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
    public String getNature() { return nature; }
    public void setNature(String nature) { this.nature = nature; }
    public String getGroupType() { return groupType; }
    public void setGroupType(String groupType) { this.groupType = groupType; }
    public String getEffectJson() { return effectJson; }
    public void setEffectJson(String effectJson) { this.effectJson = effectJson; }
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
