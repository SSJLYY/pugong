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
    /** 弱点打击节拍累加器（第七批）：累计出拳数的小数余量，每满 10 拳触发一次必暴并扣回。 */
    public BigDecimal punchAcc = BigDecimal.ZERO;
    /** 连环掌剩余双击次数（第八批，计数型 buff 不走秒表 buffs；跨世保留同 punchAcc）。 */
    public double flurryHits = 0;
    /** 金币里程碑已播报到的指数（第八批）：gold ≥ 10^k 且 k 大于此值时播报一次并抬升；默认 3（万金起播）。 */
    public int goldMilestone = 3;
    /** 每日首祭日期（第七批，Asia/Shanghai）：当日已用掉「首祭免费」时等于今日日期串。 */
    public String tributeDay;

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
    /** 已领取奖励的成就 id（成就本身在 achievements 里，奖励单独领取防重复领）。 */
    public java.util.Set<String> achClaimed = new java.util.LinkedHashSet<>();

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
    /** 自动分解档位（0=关）：掉落品质低于此档直接分解为材料不进背包；与自动售卖并存时分解优先。 */
    public int autoSmeltKeep = 0;
    /** 挂机自动管护(v3.13)：每次结算自动「卖白装→同名吞噬→穿最强换装」，无需手点。 */
    public boolean autoEquipBest = false;
    /** 自动进食：饱食回血（regen_food）断档时自动吃一份食物续上（村里不吃，防浪费）。 */
    public boolean autoEat = false;
    /** 自动推进：Boss 击杀后若下一区门槛达标则直接推进（否则回村）。 */
    public boolean autoAdvance = false;
    /** Boss 连战：Boss 击杀后留在本区立即再刷 Boss（优先于自动推进）。 */
    public boolean autoBoss = false;
    /** 自动管护上次执行的背包签名（击杀/背包/已穿构成），未变跳过扫描。 */
    public long lastMaintainSig = -1;

    /** 洗炼保底计数（第七批）：连续未刷出史诗词条的洗炼次数，到 REROLL_PITY_MAX-1 时下一次必出史诗。 */
    public int rerollPity = 0;

    /** 每日任务签发日期（第七批，Asia/Shanghai 的 yyyy-MM-dd）；跨日由 DailyQuests.ensure 重签。 */
    public String dailyDay;
    /** 当日每日任务（签发时快照基线，进度 = 终身计数 − 基线，转生不清）。 */
    public List<DailyTask> dailyTasks = new ArrayList<>();
    /** 当日全清奖励是否已发（第七批第 36 轮）。 */
    public boolean dailyBonusGiven = false;

    /** 周常任务签发周（第八批，ISO 周键如 2026-W45）；跨周由 WeeklyQuests.ensure 重签。 */
    public String weeklyWeek;
    /** 本周周常任务（与每日任务共用 DailyTask 结构：基线快照口径相同）。 */
    public List<DailyTask> weeklyTasks = new ArrayList<>();
    /** 本周全清奖励是否已发（第八批）。 */
    public boolean weeklyBonusGiven = false;

    /** 每日黑市刷新日期（第七批，Asia/Shanghai）；跨日由 BlackMarket.ensure 重刷。 */
    public String marketDay;
    /** 当日黑市货单（生成时快照折扣价，整日不变）。 */
    public List<MarketOffer> marketOffers = new ArrayList<>();
    /** 当日黑市已购计数（药剂 id → 件数，每件限购由 BlackMarket.LIMIT_PER_ITEM 守卫）。 */
    public Map<String, Integer> marketBought = new LinkedHashMap<>();

    /** 黑市货单条目：price 为折后价（金币），discount 为折扣百分比（30–50）。 */
    public static class MarketOffer {
        public String id;
        public int price;
        public int discount;
    }

    /** 每日任务实例：baseline 为签发时终身计数的 plain 字符串（伤害为大数，统一字符串存）。 */
    public static class DailyTask {
        public String id;
        public String baseline;
        public long target;
        public long rewardGold;
        public boolean claimed;
    }
    /** 尸检用：最近 10 条击杀序列（"存活秒数 怪名"，环形覆盖）。 */
    public java.util.List<String> recentKills = new java.util.ArrayList<>();
    /** 战斗日志：最近事件（Boss 击杀/图纸/史诗掉落），环形保留 20 条。 */
    public java.util.List<String> events = new java.util.ArrayList<>();

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
        /** 来源区域（掉落时打标）：分解产出按区域材料结算；旧档为 null（按平原兽皮兜底）。 */
        public String zone;
        /** 模板 code（套装判定用）：掉落/合成时打标；旧档为 null，由 Sets.codeOf 按名回退查模板。 */
        public String code;
        /** 淬炼等级（0..TEMPER_MAX_LV）：底子额外乘区 +4%/级，与强化独立计层；旧档默认 0。 */
        public int temper;
        /** 已镶嵌宝石 code 列表（第八批，上限 GameConst.GEM_SOCKET_MAX）；旧档为 null，读取处兜底。 */
        public List<String> gems = new ArrayList<>();
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
        /** 迟缓咒剩余秒数（第七批）：>0 时本怪攻速 ×0.7，随存档持久化、advance 递减。 */
        public double slowT;
        /** 词条预警是否已播报（第八批，每只怪一次）。 */
        public boolean traitWarned;
        /** 怪物词条 id（GameConst.MOB_TRAITS 的键）；null=普通怪。旧档无此字段即 null。 */
        public String trait;
        /** Boss 二阶段（第七批）：血量跌破 50% 后置位，攻速/攻击提升，只触发一次。 */
        public boolean phase2;
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
        /** 终身分解装备数。 */
        public long dismantles = 0;
        /** 终身普攻总伤害 / 最高单击（代表拳伤，含暴击期望口径；转生不清零，战绩与里程碑成就用）。 */
        public BigDecimal totalDamage = BigDecimal.ZERO;
        public BigDecimal bestHit = BigDecimal.ZERO;
        /** 终身 Boss 击杀次数（每次击杀计数，区别于 bossKills 首杀名单）。 */
        public long bossKillCount = 0;
        /** 击破二阶段 Boss 次数（第七批，破相者成就口径）。 */
        public long bossPhaseKills = 0;
        public long enchants = 0;
        public long tempers = 0;
        public long rerolls = 0;
        public long potionsUsed = 0;
        public long dailyClaims = 0;
        /** 累计领取的周常任务数（第八批）。 */
        public long weeklyClaims = 0;
        /** 累计击杀带「星蚀」词条的怪物数（第八批，星蚀猎手成就口径）。 */
        public long eclipseKills = 0;
        /** 弱点打击触发次数（第七批，终身口径）。 */
        public long weakCrits = 0;
        /** 连环掌累计追加命中数（第八批）。 */
        public BigDecimal flurryExtra = BigDecimal.ZERO;
        /** 材料出售累计（第七批，终身口径）：份数与金币收入（商贾成就口径）。 */
        public long matSold = 0;
        public long matSoldGold = 0;
        /** 累计镶嵌宝石数（第八批）。 */
        public long gemsSocketed = 0;
        /** 累计直购宝石数（第八批）。 */
        public long gemsBought = 0;
        /** 成就里程碑已发档位掩码（第八批）：bit i 对应第 i 档（15/30/45/60 已领数）。 */
        public int achMilestonePaid = 0;
        /** 黑市累计购买件数（第七批，终身口径）。 */
        public long marketBuys = 0;
        public long born = 0;
        public long playSeconds = 0;
        public long bestMaxHP = 0;
        public int newbieKills = 0;
        public long rebirths = 0;
        public int highestGen = 0;
    }
}
