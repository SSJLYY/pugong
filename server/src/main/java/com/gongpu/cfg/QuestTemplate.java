package com.gongpu.cfg;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 委托主表：冒险公会委托（DESIGN.md §5，属性门槛接取，击杀目标交付）。 */
@Entity
@Table(name = "cfg_quest")
public class QuestTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 32)
    private String name;

    @Column(length = 8)
    private String emoji;

    @Column(length = 80)
    private String desc;

    /** 目标区域（击杀该区域的怪计入进度）。 */
    @Column(name = "target_zone", nullable = false, length = 16)
    private String targetZone;

    /** 接取门槛 JSON，如 {"maxHP":500,"PDEF":5}。 */
    @Column(name = "req_json", nullable = false, columnDefinition = "TEXT")
    private String reqJson = "{}";

    /** 击杀目标数。 */
    @Column(name = "target_kills", nullable = false)
    private Integer targetKills = 10;

    /** 奖励金币。 */
    @Column(name = "reward_gold", nullable = false)
    private Integer rewardGold = 100;

    /** 奖励物品：药剂 code（如 hp_s）；NULL 则无物品奖励。 */
    @Column(name = "reward_item", length = 32)
    private String rewardItem;

    /** 物品奖励数量。 */
    @Column(name = "reward_item_count")
    private Integer rewardItemCount = 0;

    /** 是否可重复接取（日常委托）。 */
    @Column(nullable = false)
    private Boolean repeatable = true;

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
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
    public String getTargetZone() { return targetZone; }
    public void setTargetZone(String targetZone) { this.targetZone = targetZone; }
    public String getReqJson() { return reqJson; }
    public void setReqJson(String reqJson) { this.reqJson = reqJson; }
    public Integer getTargetKills() { return targetKills; }
    public void setTargetKills(Integer targetKills) { this.targetKills = targetKills; }
    public Integer getRewardGold() { return rewardGold; }
    public void setRewardGold(Integer rewardGold) { this.rewardGold = rewardGold; }
    public String getRewardItem() { return rewardItem; }
    public void setRewardItem(String rewardItem) { this.rewardItem = rewardItem; }
    public Integer getRewardItemCount() { return rewardItemCount; }
    public void setRewardItemCount(Integer rewardItemCount) { this.rewardItemCount = rewardItemCount; }
    public Boolean getRepeatable() { return repeatable; }
    public void setRepeatable(Boolean repeatable) { this.repeatable = repeatable; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
