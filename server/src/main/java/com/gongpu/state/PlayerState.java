package com.gongpu.state;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一档完整进度（服务端权威）。
 * <p>字段与旧版 localStorage 存档结构对齐，便于把老玩家档迁移上来。</p>
 */
public class PlayerState {

    /** 世代，第 0 世起。 */
    public int gen = 0;

    public Life life = Life.fresh();
    public Fixed fixed = Fixed.fresh();

    /** 已赋能的属性：key → {lv: 强化等级}。第 0 世只有 HP。 */
    public Map<String, Bless> blessings = new LinkedHashMap<>();

    public BigDecimal gold = BigDecimal.ZERO;

    public Stats stats = new Stats();

    /** 装备槽 → 装备。 */
    public Map<String, Item> equipped = new LinkedHashMap<>();
    public List<Item> equipBag = new ArrayList<>();
    public int nextUid = 1;

    public List<String> passives = new ArrayList<>();
    public Map<String, BigDecimal> mastery = new LinkedHashMap<>();

    /** 药剂库存 id → 数量。 */
    public Map<String, Integer> potions = new LinkedHashMap<>();

    /** 药剂累计购买次数（动态定价用，DESIGN.md §9 防刷）。 */
    public Map<String, Integer> potionBought = new LinkedHashMap<>();

    public String zone = "village";

    /** 增益/减益剩余秒数。 */
    public Map<String, Double> buffs = new LinkedHashMap<>();
    /** 技能冷却剩余秒数。 */
    public Map<String, Double> cds = new LinkedHashMap<>();

    /** 护盾值(v3.14 护盾药剂):先于生命扣除,随 shield_pot buff 到期清零。 */
    public BigDecimal shield = BigDecimal.ZERO;

    /** 当前目标怪物（运行时态，不落库为长期数据也行）。 */
    public MobState mob;

    /** 上次结算时间（epoch 毫秒），服务端推进的唯一依据。 */
    public long lastTickAt = 0;

    public long lastSaveAt = 0;

    /** 死亡态：true 时世界暂停，只能回档或重开。 */
    public boolean dead = false;
    public String deathCause;

    public String talent;

    /** 单次击杀产生的掉落件数（运行时暂存，不落库）。 */
    public transient int lastDropCount = 0;

    /** 世界公告展示名（GameController 加载后填充，不落库）。 */
    public transient String bulletinName = null;

    /** 已首杀的 Boss id 集合（每档一次性奖励标记）。 */
    public java.util.Set<String> bossKills = new java.util.LinkedHashSet<>();

    /** 怪物图鉴：已击杀过的怪物 id 集合（跨世代保留，纪念性质）。 */
    public java.util.Set<String> codex = new java.util.LinkedHashSet<>();
    /** 用过的药剂种类（品药成就口径；只记种类不记次数）。 */
    public java.util.Set<String> usedPotions = new java.util.LinkedHashSet<>();

    /** 已解锁成就 id 集合（服务端权威，每次落库前评估；跨世代保留）。 */
    public java.util.Set<String> achievements = new java.util.LinkedHashSet<>();

    /** 挂机策略：自动嗑药血线（0=关闭，如 0.4 表示血量低于 40% 自动嗑血药）。 */
    public double autoHpLine = 0;
    /** 挂机策略：自动嗑蓝线（0=关闭）。 */
    public double autoMpLine = 0;
    /** 挂机策略：中毒/灼烧时自动嗑解毒剂（false=关闭）。 */
    public boolean autoCure = false;
    /** 挂机策略：燃烧生命 buff 掉了自动补（血量>60% 才补,legacy autobot 迁移）。 */
    public boolean autoBurn = false;
    /** 挂机策略：增益技能常驻（surge/rapid/heavy 掉了自动补,血危补 block/roar）。 */
    public boolean autoSkill = false;
    /** 挂机策略：血量低于此线自动逃跑回村（0=关闭）。 */
    public double autoFleeLine = 0;

    /** 新手保底（legacy 迁移）：本世非村口击杀计数（转生/重开重置，每世重新保底）。 */
    public int newbieKills = 0;
    /** 新手保底：本世已发放的保底武器件数（满 5 件停，背包满时等待不占次数）。 */
    public int newbieWeaponsGiven = 0;
    /** 新手保底：本世「回春戒」是否已发放（≥10 杀触发；背包满不置位，下次击杀重试）。 */
    public boolean ringGranted = false;
    /** 自动飞升（legacy autobot autoRebirth）：通关线达成即自动转生。 */
    public boolean autoRebirth = false;
    /** 挂机自动售卖：0=关；1~4=品质低于该档的掉落自动折现金币不进背包（4=紫及以下全折现，背包不进装备）（legacy autoSell+sellKeepQ）。 */
    public int autoSellKeep = 0;
    /** 挂机自动管护(v3.13)：每次结算自动「卖白装→同名吞噬→穿最强换装」，无需手点。 */
    public boolean autoEquipBest = false;
    /** 自动进食：饱食回血（regen_food）断档时自动吃一份食物续上（村里不吃，防浪费）。 */
    public boolean autoEat = false;
    /** 尸检用：最近 10 条击杀序列（"存活秒数 怪名"，环形覆盖）。 */
    public java.util.List<String> recentKills = new java.util.ArrayList<>();

    /** [legacy] v3.12 前 baglord 按天积累的额外栏数;现为"觉醒即无限",字段仅保留兼容旧档。 */
    public int extraSlots = 0;
    /** 上次发放额外栏的日期（ISO yyyy-MM-dd，跨天判定用）。 */
    /** [legacy] v3.12 前每日栏位的跨天判定;现仅保留兼容旧档 JSON。 */
    public String lastSlotDay = null;

    /** 无限属性点天赋(statlord):当前自由属性点(每杀一怪 +1;BigDecimal 承接后期海量击杀)。 */
    public java.math.BigDecimal freePoints = java.math.BigDecimal.ZERO;
    /** 已自由加点的属性:statKey → 累计加点值(PanelCalculator 并入 flat 区,全部属性走既有读法)。 */
    public java.util.Map<String, java.math.BigDecimal> statPoints = new java.util.LinkedHashMap<>();

    /** 进行中的委托：code → 接取时的 kills 基线（交付时用 kills - 基线 判定完成度）。 */
    public java.util.Map<String, java.math.BigDecimal> activeQuests = new java.util.LinkedHashMap<>();
    /** 已完成的非重复委托 code 集合（repeatable=false 只能领一次）。 */
    public java.util.Set<String> doneQuests = new java.util.LinkedHashSet<>();

    /** 材料背包：材料 code（或图纸 bp_配方code）→ 数量（DESIGN.md §9 图纸合成）。 */
    public java.util.Map<String, Integer> materials = new java.util.LinkedHashMap<>();

    /** 本次结算的 Boss 首杀播报（运行时暂存，不落库）。 */
    public transient String bossFirstKillName = null;
    public transient java.math.BigDecimal bossFirstKillGold = null;

    public boolean eternal() {
        return "eternal".equals(talent);
    }

    public static class Bless {
        public int lv = 0;

        public Bless() {
        }

        public Bless(int lv) {
            this.lv = lv;
        }
    }

    public static class Item {
        public int uid;
        public String name;
        public String emoji;
        public String slot;
        public Map<String, BigDecimal> base = new LinkedHashMap<>();
        public List<AffixInst> affixes = new ArrayList<>();
        public int quality;
        public int enhance;
        public String desc;
        /** 装备锁定：锁定件不被一键卖/自动售卖/管护吞噬等批量流程处置（防分级售卖误删心头好）。 */
        public boolean locked;
    }

    public static class AffixInst {
        public String name;
        public String key;
        public String type;
        public BigDecimal val;
        public String rarity;
        public String sideKey;
        public BigDecimal sideVal;
    }

    public static class MobState {
        public String id;
        public String name;
        public String emoji;
        public BigDecimal maxHP = BigDecimal.ZERO;
        public BigDecimal curHP = BigDecimal.ZERO;
        public BigDecimal PATK = BigDecimal.ZERO;
        public BigDecimal MATK = BigDecimal.ZERO;
        public BigDecimal PDEF = BigDecimal.ZERO;
        public BigDecimal MDEF = BigDecimal.ZERO;
        public double ASPD;
        public double RNG;
        public double SPD;
        public long gold;
        public boolean boss;
        public double silenceCd;
        /** 怪物词条 id（GameConst.MOB_TRAITS 的键）；null=普通怪。旧档无此字段即 null。 */
        public String trait;
    }

    public static class Stats {
        public BigDecimal punches = BigDecimal.ZERO;
        public BigDecimal lifetimePunches = BigDecimal.ZERO;
        public BigDecimal kills = BigDecimal.ZERO;
        /** 终身击杀（跨世不清零；运营统计口径。当世 kills 每世重置、不可用作累计）。 */
        public BigDecimal lifetimeKills = BigDecimal.ZERO;
        public long deaths = 0;
        /** 累计合成次数 / 附魔次数（终身不清零，成就里程碑用）。 */
        public long crafts = 0;
        public long enchants = 0;
        public long born = 0;
        public long playSeconds = 0;
        public long bestMaxHP = 0;
        public int newbieKills = 0;
        public long rebirths = 0;
        public int highestGen = 0;
    }
}
