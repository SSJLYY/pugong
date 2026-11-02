package com.gongpu.cfg;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 技能主表：主动技能与被动技能统一管理。 */
@Entity
@Table(name = "cfg_skill")
public class SkillTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 32)
    private String name;

    @Column(length = 8)
    private String emoji;

    /** active=主动 / passive=被动。 */
    @Column(nullable = false, length = 8)
    private String kind = "active";

    /** 学习价格（金币），0=不可购买（任务/掉落获得）。 */
    @Column(nullable = false)
    private Integer price = 300;

    /** 耗蓝。 */
    @Column(nullable = false)
    private Integer mp = 0;

    /** 冷却秒。 */
    @Column(nullable = false)
    private Integer cd = 0;

    /** 持续秒。 */
    @Column(nullable = false)
    private Integer dur = 0;

    /** 效果 JSON：主动 {"heavy":5} / 被动 {"effect":"combo","chance":0.15}。 */
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
    public Integer getMp() { return mp; }
    public void setMp(Integer mp) { this.mp = mp; }
    public Integer getCd() { return cd; }
    public void setCd(Integer cd) { this.cd = cd; }
    public Integer getDur() { return dur; }
    public void setDur(Integer dur) { this.dur = dur; }
    public String getEffectJson() { return effectJson; }
    public void setEffectJson(String effectJson) { this.effectJson = effectJson; }
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
