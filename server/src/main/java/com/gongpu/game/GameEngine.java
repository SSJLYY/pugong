package com.gongpu.game;

import com.gongpu.common.Num;
import com.gongpu.state.PlayerState;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 权威结算引擎。
 * <p>设计要点：</p>
 * <ul>
 *   <li><b>时间驱动</b>：唯一输入是「距上次结算过去了多少秒」，客户端无法伪造出拳次数。</li>
 *   <li><b>批量数学</b>：后期攻速可达 1e44 次/秒，绝不逐次循环，全部按期望值批量结算，
 *       仅在小数值域（&lt;= 2000 拳）走逐击模拟以保留暴击手感。</li>
 *   <li><b>死亡即停</b>：curHP 归零立刻进入 death 态，世界冻结，只能回档或重开。</li>
 * </ul>
 */
public final class GameEngine {

    /** 结算结果：便于接口层拼装返回体。 */
    public record AdvanceResult(
            boolean died,
            String deathCause,
            double seconds,
            String punches,
            int kills,
            int drops,
            boolean cleared,
            boolean bossSpawned,
            String bossFirstKill,
            String bossFirstKillGold) {
    }

    private GameEngine() {
    }

    // ==================== 主循环 ====================

    /**
     * 推进到 now（epoch 毫秒）。
     *
     * @param eff 效率系数（离线 0.5，在线 1.0）
     */
    public static AdvanceResult advance(PlayerState s, long now, double eff, boolean offline) {
        if (s.dead) {
            return new AdvanceResult(true, s.deathCause, 0, "0", 0, 0, false, false, null, null);
        }
        if (s.lastTickAt <= 0) {
            s.lastTickAt = now;
            return new AdvanceResult(false, null, 0, "0", 0, 0, false, false, null, null);
        }
        double seconds = Math.max(0, (now - s.lastTickAt) / 1000.0);
        if (seconds <= 0) {
            // 时钟回拨（NTP 校正/改时区）：重锚 lastTickAt，否则回拨窗口内每帧都算负时长、
            // 玩家会被冻结推进直到真实时间追过旧锚点（最坏数小时零收益）
            if (now < s.lastTickAt) {
                s.lastTickAt = now;
            }
            return new AdvanceResult(false, null, 0, "0", 0, 0, false, false, null, null);
        }
        double capped = Math.min(seconds, GameConst.MAX_ADVANCE_SECONDS);
        if (offline) {
            capped = Math.min(capped, GameConst.OFFLINE_CAP_HOURS * 3600.0);
        }
        // 在线小额推进不折算，离线按效率折算
        double effective = offline ? capped * eff : capped;

        s.lastTickAt = now;
        s.stats.playSeconds += (long) capped;

        BigDecimal punchesBefore = s.stats.punches;
        int killsBefore = toLongSaturated(s.stats.kills) > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) toLongSaturated(s.stats.kills);

        tickBuff(s, capped);
        if (s.dead) {
            return new AdvanceResult(true, s.deathCause, capped, "0", 0, 0, false, false, null, null);
        }

        PanelCalculator.Panel p = PanelCalculator.of(s);

        // —— 出拳与成长 ——
        BigDecimal punches = Num.floor(Num.mul(p.ASPD(), Num.of(effective)));
        boolean bossSpawned = false;
        int drops = 0;

        // —— 枪械·纯魔法弹：每发子弹耗蓝（BULLET_MP×发数），无蓝哑火（本段普攻全部不结算）——
        if (punches.signum() > 0 && Num.gt(p.BULLETMP(), Num.ZERO)) {
            // 可负担的弹药数 = min(发数, 蓝量 ÷ 每发耗蓝)
            java.math.BigDecimal manaNeed = Num.mul(p.BULLETMP(), punches);
            if (Num.lt(s.life.curMP, manaNeed)) {
                java.math.BigDecimal affordable = Num.floor(Num.div(s.life.curMP, p.BULLETMP()));
                // 至少能打几发就打几发；一发都打不起 → 完全哑火(保留零头蓝,回蓝后即恢复,
                // 清零会让玩家 0<蓝<一发 时白攒的蓝蒸发)
                if (Num.lte(affordable, Num.ZERO)) {
                    punches = Num.ZERO;
                } else {
                    s.life.curMP = Num.sub(s.life.curMP, Num.mul(p.BULLETMP(), affordable));
                    punches = affordable;
                }
            } else {
                s.life.curMP = Num.sub(s.life.curMP, manaNeed);
            }
            // 哑火时蓝回复照常，下个 tick 有蓝即恢复射击
        }

        if (punches.signum() > 0) {
            grow(s, punches, p);
            s.stats.punches = Num.add(s.stats.punches, punches);
            s.stats.lifetimePunches = Num.add(s.stats.lifetimePunches, punches);
            s.stats.bestMaxHP = Math.max(s.stats.bestMaxHP, toLongSaturated(s.life.maxHP));

            // —— 伤害与击杀 ——
            // 设计 §2.2/§2.3:miss 不触发任何效果 → 伤害端同样过 ACC×连击漏斗(与成长同口径);
            // ±10% 伤害浮动(批量帧一次抽样,长期期望不变,短帧有波动手感)
            if (s.mob != null) {
                BigDecimal hits = Num.mul(Num.mul(punches, p.ACC()), comboMul(s));
                double variance = 1 + (Math.random() * 2 - 1) * GameConst.DMG_VARIANCE;
                hits = Num.mul(hits, Num.of(variance));
                drops = dealDamage(s, p, hits, effective);
                if (s.mob == null) {
                    // 目标已被击杀并在 dealDamage 内重生
                }
            }
        }

        // —— 挂机策略：自动嗑药（必须在怪物攻击前，血线破了先补再挨打）——
        if (!s.dead) {
            autoPotion(s);
        }

        // 词条·再生（第八批）：交战帧每秒回复上限 3%（在反击前结算，封顶不超上限；
        // 置于 advance 主流程而非 mobAttack 内——怪没打中玩家时也应回复）
        if (!s.dead && s.mob != null && "regen".equals(s.mob.trait)
                && s.mob.curHP != null && s.mob.maxHP != null) {
            BigDecimal heal = Num.mul(s.mob.maxHP, Num.of(GameConst.TRAIT_REGEN_PCT * effective));
            s.mob.curHP = Num.min(s.mob.maxHP, Num.add(s.mob.curHP, heal));
        }

        // —— 怪物反击 ——
        if (!s.dead && s.mob != null) {
            mobAttack(s, p, effective);
        }

        // —— 持续伤害与回复 ——
        if (!s.dead) {
            dotTick(s, effective);
            regenTick(s, effective);
            autoPotion(s);
            autoBuffTick(s);
            autoEatTick(s);
        }

        // —— 通关判定 ——
        boolean cleared = Num.gte(PanelCalculator.of(s).ASPD(), GameConst.CLEAR_ASPD);

        // 与 killsBefore 同口径做 int 饱和，防超 2^31 后 intValue() 回绕出现负增量
        int killsAfter = toLongSaturated(s.stats.kills) > Integer.MAX_VALUE
                ? Integer.MAX_VALUE : (int) toLongSaturated(s.stats.kills);
        // Boss 首杀播报（采集后清空，避免重复下发）
        String bossFirstKill = s.bossFirstKillName;
        String bossFirstKillGold = s.bossFirstKillGold == null ? null : Num.plain(s.bossFirstKillGold);
        s.bossFirstKillName = null;
        s.bossFirstKillGold = null;
        return new AdvanceResult(s.dead, s.deathCause, capped,
                Num.plain(Num.sub(s.stats.punches, punchesBefore)),
                Math.max(0, killsAfter - killsBefore), drops, cleared, bossSpawned,
                bossFirstKill, bossFirstKillGold);
    }

    // ==================== 成长 ====================

    /** 连击乘数(被动 combo):伤害与成长共用同一漏斗(设计 §2.2 miss/连击对两者同效)。 */
    static BigDecimal comboMul(PlayerState s) {
        return s.passives.contains("combo") ? Num.of(1 + GameConst.COMBO_CHANCE) : Num.of(1);
    }

    /** 普攻赋能的永久成长。 */
    public static void grow(PlayerState s, BigDecimal punches) {
        grow(s, punches, PanelCalculator.of(s));
    }

    /** 同 grow,但复用调用方已构建的面板(advance 主面板):grow 只读 ACC,
     *  而 ACC 不受 grow 期间的生命/蓝量变动影响,复用与重算等价,省一次全量构建。 */
    static void grow(PlayerState s, BigDecimal punches, PanelCalculator.Panel p) {
        BigDecimal hitRate = p.ACC();
        BigDecimal comboMul = comboMul(s);
        BigDecimal hits = Num.mul(Num.mul(punches, hitRate), comboMul);
        BigDecimal villageMul = "village".equals(s.zone) ? GameConst.VILLAGE_GROWTH_MUL : Num.of(1);
        // 修行药剂：成长 ×2（沉默不封药剂增益——药剂是外物）
        BigDecimal growthMul = PanelCalculator.active(s, "growth_pot") ? Num.of(2) : Num.of(1);
        BigDecimal totalHits = Num.mul(Num.mul(hits, villageMul), growthMul);

        for (Map.Entry<String, PlayerState.Bless> e : s.blessings.entrySet()) {
            String key = e.getKey();
            BigDecimal step = PanelCalculator.blessStep(s, key);
            if (step == null) {
                continue;
            }
            applyGain(s, key, Num.mul(step, totalHits));
        }

        // 健体：每 100 拳额外 +50 生命上限
        if (s.passives.contains("vitality")) {
            BigDecimal extra = Num.div(Num.mul(punches, Num.of(GameConst.VITALITY_BONUS)),
                    Num.of(GameConst.VITALITY_INTERVAL));
            s.life.maxHP = Num.add(s.life.maxHP, extra);
            s.life.curHP = Num.add(s.life.curHP, extra);
        }
    }

    private static void applyGain(PlayerState s, String key, BigDecimal gain) {
        switch (key) {
            case "HP" -> {
                s.life.maxHP = Num.add(s.life.maxHP, gain);
                s.life.curHP = Num.add(s.life.curHP, gain);
            }
            case "PATK", "MATK", "PDEF", "MDEF", "REGEN" -> s.life.set(key, Num.add(s.life.get(key), gain));
            case "ASPD", "RNG", "SPD", "CRIT", "CRITD", "ACC", "EVA" -> s.fixed.add(key, gain);
            default -> { }
        }
    }

    // ==================== 伤害 ====================

    /** 对目标造成伤害，返回本次掉落件数。包内可见:反弹击杀/溢出金币回归单测用。 */
    /**
     * 每拳期望伤害（与 dealDamage 前段同口径：暴击期望 + 防御减伤 + 缴械/堡垒/潜行词条 + 巨人乘区；
     * 不含蓄力重击等一次性 buff）。供 mobView 的击杀预估展示，改伤害公式必须同步这里。
     */
    static BigDecimal expectedPerHit(PlayerState s, PanelCalculator.Panel p, PlayerState.MobState m) {
        BigDecimal critMul = Num.add(Num.of(1), Num.mul(effCrit(p, m), Num.sub(effCritD(p, m), Num.of(1))));
        BigDecimal pd = Num.max(Num.of(1),
                Num.sub(Num.mul(p.PATK(), critMul), Num.mul(effDef(s, m.PDEF), Num.of(GameConst.DMG_DEF_FACTOR))));
        BigDecimal md = Num.gt(p.MATK(), Num.ZERO)
                ? Num.max(Num.of(1), Num.sub(Num.mul(p.MATK(), critMul), Num.mul(effDef(s, m.MDEF), Num.of(GameConst.DMG_DEF_FACTOR))))
                : Num.ZERO;
        BigDecimal perHit = Num.add(pd, md);
        if ("disarm".equals(m.trait)) {
            perHit = Num.mul(perHit, Num.of(GameConst.TRAIT_DISARM_MUL));
        }
        if ("bulwark".equals(m.trait)) {
            perHit = Num.mul(perHit, Num.of(GameConst.TRAIT_BULWARK_MUL));
        }
        if ("stealth".equals(m.trait)) {
            perHit = Num.mul(perHit, Num.of(GameConst.TRAIT_STEALTH_MUL));
        }
        if (m.boss && s.passives.contains("boss_hunter")) {
            perHit = Num.mul(perHit, Num.of(GameConst.BOSS_HUNTER_MUL));
        }
        // 被动·处决者（第七批）：Boss 残血（<30%）专伤乘区，与巨人杀手乘算
        if (m.boss && s.passives.contains("executioner") && bossLowHp(m)) {
            perHit = Num.mul(perHit, Num.of(GameConst.EXECUTIONER_MUL));
        }
        if (m.boss && buffOn(s, p, "giant_pot")) {
            perHit = Num.mul(perHit, Num.of(GameConst.GIANT_POT_MUL));
        }
        return perHit;
    }

    /**
     * 目标有效防御（第八批：被动·破甲专精与药剂·破甲各无视 20% 双防、乘算叠加，
     * 均受沉默压制；dealDamage 与 expectedPerHit 同口径）。
     */
    static BigDecimal effDef(PlayerState s, BigDecimal rawDef) {
        if (PanelCalculator.isSilenced(s)) {
            return rawDef;
        }
        BigDecimal def = rawDef;
        if (s.passives.contains("armor_pierce")) {
            def = Num.mul(def, Num.of(1 - GameConst.ARMOR_PIERCE_RATIO));
        }
        if (PanelCalculator.active(s, "pierce_pot")) {
            def = Num.mul(def, Num.of(1 - GameConst.PIERCE_POT_RATIO));
        }
        return def;
    }

    /** 对当前目标的有效暴击率（词条·坚韧第八批：暴击率减半；dealDamage 与 expectedPerHit 同口径）。 */
    static BigDecimal effCrit(PanelCalculator.Panel p, PlayerState.MobState m) {
        if (m != null && "tenacity".equals(m.trait)) {
            return Num.mul(p.CRIT(), Num.of(GameConst.TRAIT_TENACITY_CRIT_MUL));
        }
        return p.CRIT();
    }

    /** 有效闪避（第九批）：裂空词条撕裂空间——玩家闪避减半（单点口径，mobAttack 命中结算用）。 */
    static BigDecimal effEva(PanelCalculator.Panel p, PlayerState.MobState m) {
        if (m != null && "void_rend".equals(m.trait)) {
            return Num.mul(p.EVA(), Num.of(GameConst.TRAIT_REND_EVA_MUL));
        }
        return p.EVA();
    }

    /** 有效暴伤（第八批）：星蚀词条吞噬暴击威力——暴伤的超额部分（CRITD−1）减半，与坚韧（减暴击率）分工。 */
    static BigDecimal effCritD(PanelCalculator.Panel p, PlayerState.MobState m) {
        if (m != null && "star_eclipse".equals(m.trait)) {
            return Num.add(Num.of(1), Num.mul(Num.sub(p.CRITD(), Num.of(1)),
                    Num.of(GameConst.TRAIT_ECLIPSE_CRITD_MUL)));
        }
        return p.CRITD();
    }

    /** Boss 是否残血（处决者触发口径：当前/上限 < EXECUTIONER_HP_LINE；上限非正不触发）。 */
    static boolean bossLowHp(PlayerState.MobState m) {
        return m.maxHP != null && m.maxHP.signum() > 0
                && Num.lt(Num.div(m.curHP, m.maxHP), Num.of(GameConst.EXECUTIONER_HP_LINE));
    }

    static int dealDamage(PlayerState s, PanelCalculator.Panel p, BigDecimal punches, double seconds) {
        PlayerState.MobState m = s.mob;
        if (m == null) {
            return 0;
        }
        // 词条预警（第八批）：带词条的怪首次交手时播报其词条与克制提示（每只怪仅一次）
        if (m.trait != null && !m.traitWarned) {
            m.traitWarned = true;
            GameConst.TraitDef td = GameConst.MOB_TRAITS.get(m.trait);
            if (td != null) {
                pushEvent(s, td.emoji() + " 「" + (m.name == null ? "野怪" : m.name) + "」带词条【" + td.name() + "】：" + td.desc());
            }
        }

        // 有效每拳伤害（含暴击期望）
        BigDecimal critMul = Num.add(Num.of(1), Num.mul(effCrit(p, m), Num.sub(effCritD(p, m), Num.of(1))));
        BigDecimal pd = Num.max(Num.of(1),
                Num.sub(Num.mul(p.PATK(), critMul), Num.mul(effDef(s, m.PDEF), Num.of(GameConst.DMG_DEF_FACTOR))));
        BigDecimal md = Num.gt(p.MATK(), Num.ZERO)
                ? Num.max(Num.of(1), Num.sub(Num.mul(p.MATK(), critMul), Num.mul(effDef(s, m.MDEF), Num.of(GameConst.DMG_DEF_FACTOR))))
                : Num.ZERO;
        BigDecimal perHit = Num.add(pd, md);
        // 词条·缴械：武器被压制，普攻伤害打折（与蓄力重击同为乘区，先后不影响结果）
        if ("disarm".equals(m.trait)) {
            perHit = Num.mul(perHit, Num.of(GameConst.TRAIT_DISARM_MUL));
        }
        // 词条·堡垒：皮糙肉厚，你对它造成的伤害打折（同乘区）
        if ("bulwark".equals(m.trait)) {
            perHit = Num.mul(perHit, Num.of(GameConst.TRAIT_BULWARK_MUL));
        }
        // 词条·潜行：身形飘忽攻击屡屡落空，你对它造成的伤害打折（同乘区；成长按实际挥拳另算不受影响）
        if ("stealth".equals(m.trait)) {
            perHit = Num.mul(perHit, Num.of(GameConst.TRAIT_STEALTH_MUL));
        }
        // 被动·巨人杀手：对 Boss 专伤乘区
        if (m.boss && s.passives.contains("boss_hunter")) {
            perHit = Num.mul(perHit, Num.of(GameConst.BOSS_HUNTER_MUL));
        }
        // 被动·处决者（第七批）：Boss 残血专伤（与预估口径一致，见 expectedPerHit 侧同名分支）
        if (m.boss && s.passives.contains("executioner") && bossLowHp(m)) {
            perHit = Num.mul(perHit, Num.of(GameConst.EXECUTIONER_MUL));
        }
        // 药剂·巨人：对 Boss 专伤乘区（与被动乘算叠加，沉默压制）
        if (m.boss && buffOn(s, p, "giant_pot")) {
            perHit = Num.mul(perHit, Num.of(GameConst.GIANT_POT_MUL));
        }
        if (PanelCalculator.active(s, "heavy")) {
            perHit = Num.mul(perHit, Num.of(5));
            s.buffs.remove("heavy");
        }
        // 主动·冲锋（第七批）：下一击 ×3，与蓄力重击同为一次性消费 buff（可叠乘至 ×15）
        if (PanelCalculator.active(s, "charge")) {
            perHit = Num.mul(perHit, Num.of(GameConst.CHARGE_MUL));
            s.buffs.remove("charge");
        }
        // 主动·虚空斩（第九批）：下一击 ×4，一次性消费 buff（与冲锋/重击可叠乘）
        if (PanelCalculator.active(s, "void_slash")) {
            perHit = Num.mul(perHit, Num.of(GameConst.VOID_SLASH_MUL));
            s.buffs.remove("void_slash");
        }

        BigDecimal totalDmg = Num.mul(perHit, punches);

        // 弱点打击（第七批）：每第 10 拳必暴击——该拳按暴击倍率与期望倍率之比放大，
        // 差额计入整帧伤害（节拍跨帧/跨世累计，punchAcc 持久化；暴击倍率 ≤ 期望时无加成）
        BigDecimal weakCount = weakCritsThisFrame(s, punches);
        if (Num.gt(weakCount, Num.ZERO)) {
            BigDecimal weakPerHit = Num.mul(perHit, Num.div(p.CRITD(), critMul));
            if (Num.gt(weakPerHit, perHit)) {
                totalDmg = Num.add(totalDmg, Num.mul(Num.sub(weakPerHit, perHit), weakCount));
                long add = weakCount.longValue();
                s.stats.weakCrits = add > Long.MAX_VALUE - s.stats.weakCrits
                        ? Long.MAX_VALUE : s.stats.weakCrits + add;
            }
        }

        // 主动·连环掌（第八批）：计数型双击——flurryHits 剩余次数内，每次命中追加一次同额命中
        // （置于弱点打击之后：追加命中不参与弱点节拍；与总伤害统计同进退）
        if (s.flurryHits > 0 && punches.signum() > 0) {
            double extra = Math.min(punches.doubleValue(), s.flurryHits);
            if (extra > 0) {
                totalDmg = Num.add(totalDmg, Num.mul(perHit, Num.of(extra)));
                s.flurryHits -= extra;
                s.stats.flurryExtra = Num.add(s.stats.flurryExtra, Num.of(extra));
            }
        }

        // 战绩统计（终身口径）：总伤害按帧累加，最高单击取代表拳伤（木桩陪练同样计入）
        s.stats.totalDamage = Num.add(s.stats.totalDamage, totalDmg);
        if (Num.gt(perHit, s.stats.bestHit)) {
            s.stats.bestHit = perHit;
        }

        // 无限血木桩：不打血，只结算吸血
        if (m.maxHP == null || Num.lte(m.maxHP, Num.ZERO)) {
            healByLifesteal(s, p, totalDmg);
            return 0;
        }

        healByLifesteal(s, p, totalDmg);

        // 荆棘反扎基数快照：开帧目标与其剩余血量——反扎只按它实际承受的伤害计（见帧尾结算）
        PlayerState.MobState startMob = m;
        String startTrait = m.trait;
        BigDecimal startMobHp = m.curHP == null ? BigDecimal.ZERO : m.curHP;

        // 批量结算：溢出伤害按比例折算击杀数（后期攻速爆炸时一秒百万杀）
        int drops = 0;
        BigDecimal dmgLeft = totalDmg;
        int guard = 0;
        while (Num.gt(dmgLeft, Num.ZERO) && !s.dead && guard++ < 100) {
            BigDecimal hpNeeded = m.curHP;
            if (Num.lt(dmgLeft, hpNeeded)) {
                // 打不死当前这只：扣血收尾
                m.curHP = Num.sub(m.curHP, dmgLeft);
                dmgLeft = Num.ZERO;
                break;
            }
            // 击杀当前这只
            dmgLeft = Num.sub(dmgLeft, hpNeeded);
            onMobKilled(s, m);
            drops += s.lastDropCount;
            s.lastDropCount = 0;
            if (advanceAfterKill(s, m)) {
                // Boss 死亡:回村,本帧结束
                dmgLeft = Num.ZERO;
                break;
            }
            // 自动寻下一只，剩余伤害继续结转（最多 100 只，防死循环）
            m = s.mob;
            if (m == null || m.maxHP == null || Num.lte(m.curHP, Num.ZERO)) {
                break;
            }
            if (guard >= 100) {
                // 超出护栏：剩余伤害折算成期望击杀（不再逐只模拟）
                BigDecimal extraKills = Num.floor(Num.div(dmgLeft, Num.max(hpNeeded, Num.of(1))));
                if (Num.gt(extraKills, Num.ZERO)) {
                    s.stats.kills = Num.add(s.stats.kills, extraKills);
                    s.stats.lifetimeKills = Num.add(s.stats.lifetimeKills, extraKills);
                    // 溢出期望击杀同样是真杀:自由属性点按比例发放(与 kills 口径一致)
                    if ("statlord".equals(s.talent)) {
                        s.freePoints = Num.add(s.freePoints, extraKills);
                    }
                    // 期望金币同步补发:此分支不走 onMobKilled,不补则中后期挂机收益被系统性吞掉
                    s.gold = Num.add(s.gold, Num.mul(Num.of(m.gold), Num.mul(goldMul(s), extraKills)));
                    BigDecimal zoneRate = Num.of(com.gongpu.cfg.GameData.instance().dropRate(s.zone) * dropLuckMul(s));
                    // 动态容量：负重腰带 +20，不能写死 BAG_MAX
                    int bagRoom = GameService.bagCap(s) - s.equipBag.size();
                    int extraDrops = Num.gt(zoneRate, Num.ZERO)
                            ? (int) Math.min(bagRoom, Num.mul(extraKills, zoneRate).doubleValue())
                            : 0;
                    for (int i = 0; i < extraDrops; i++) {
                        PlayerState.Item it = EquipFactory.generate(s, s.zone, false);
                        if (it == null) {
                            continue;
                        }
                        // 与 onMobKilled 同语义:掉落拦截（自动分解优先于自动售卖）走 GameService.dropIntercept
                        // (此前只改了逐杀分支,高攻速常态走本期望分支,自动售卖形同虚设)
                        String act = GameService.dropIntercept(s, it);
                        if ("smelt".equals(act)) {
                            GameService.grantDismantle(s, it);
                        } else if ("sell".equals(act)) {
                            s.gold = Num.add(s.gold, EquipFactory.sellPrice(it));
                        } else {
                            s.equipBag.add(it);
                        }
                    }
                    drops += extraDrops;
                }
                break;
            }
        }
        // 词条「荆棘」：开帧目标实际承受伤害的 10% 反扎玩家，护盾先吸；
        // 反扎基数封顶在它开帧时的剩余血量——溢出伤害打在后续连锁怪身上，不该由它反扎
        // （旧口径按整帧总伤害反扎：高伤玩家一拳溢出上亿倍于怪血量，反扎无视双防一击致命，属事故）
        // 扎死当场死亡——advance 后续阶段（嗑药/怪反击/DoT）均以 !dead 守卫，不会二次结算
        if ("thorns".equals(startTrait) && !s.dead && Num.gt(totalDmg, Num.ZERO)) {
            BigDecimal absorbed = Num.min(totalDmg, startMobHp);
            BigDecimal reflect = Num.mul(absorbed, Num.of(GameConst.TRAIT_THORNS_MUL));
            s.life.curHP = Num.sub(s.life.curHP, absorbShield(s, reflect));
            if (Num.lte(s.life.curHP, Num.ZERO)) {
                kill(s, startMob);
            }
        }
        // Boss 二阶段判定（第七批）：本帧打完仍存活的 Boss 跌破半血即狂怒（只触发一次）
        checkBossPhase(s);
        return drops;
    }

    /** Boss 二阶段（第七批）：当前 Boss 血量跌破 50% → 置 phase2 并播报；攻速/攻击加成在 mobAttack 消费。 */
    static void checkBossPhase(PlayerState s) {
        PlayerState.MobState m = s.mob;
        if (m == null || !m.boss || m.phase2 || m.maxHP == null) {
            return;
        }
        if (Num.lt(Num.div(m.curHP, m.maxHP), Num.of(GameConst.BOSS_PHASE2_HP_LINE))) {
            m.phase2 = true;
            pushEvent(s, "🔥 Boss「" + (m.name == null ? "?" : m.name) + "」狂怒了！进入二阶段：攻速 +30%、攻击 +20%");
        }
    }

    /** 弱点打击节拍（第七批）：把本帧拳数计入累加器，返回本帧触发的必暴次数（每满 10 拳一次）。 */
    private static BigDecimal weakCritsThisFrame(PlayerState s, BigDecimal punches) {
        if (s.punchAcc == null) {
            s.punchAcc = BigDecimal.ZERO;
        }
        s.punchAcc = Num.add(s.punchAcc, punches);
        BigDecimal tens = Num.floor(Num.div(s.punchAcc, Num.of(10)));
        if (Num.gt(tens, Num.ZERO)) {
            s.punchAcc = Num.sub(s.punchAcc, Num.mul(tens, Num.of(10)));
        }
        return tens;
    }

    private static void healByLifesteal(PlayerState s, PanelCalculator.Panel p, BigDecimal dmg) {
        if (Num.gt(p.LIFESTEAL(), Num.ZERO)) {
            BigDecimal heal = Num.mul(Num.mul(dmg, p.LIFESTEAL()), healMul(s));
            s.life.curHP = Num.min(Num.add(s.life.curHP, heal), s.life.maxHP);
        }
    }

    /** 词条「诅咒」（第七批）：当前对手带诅咒时，玩家一切回血减半（回复 tick/吸血/喝药同口径）。 */
    static boolean cursed(PlayerState s) {
        return s != null && s.mob != null && "curse".equals(s.mob.trait);
    }

    static BigDecimal healMul(PlayerState s) {
        return cursed(s) ? Num.of(GameConst.TRAIT_CURSE_HEAL_MUL) : BigDecimal.ONE;
    }

    /**
     * 新手保底(legacy autobot/保底fix 迁移):第 0 世起每世重新保底。
     * <ul>
     *   <li>非村口击杀计数;前 5 杀每杀必掉一把新手武器(精良,1 词条),助力脱离空手地狱</li>
     *   <li>≥10 杀必掉史诗「回春戒」(每秒回复 1% 生命上限)——本世未发过才发</li>
     *   <li>背包满时保底等待:不消耗次数/不置位,清理背包后下次击杀必得</li>
     * </ul>
     */
    static void grantNewbieDrops(PlayerState s) {   // 包内可见:保底语义单测用
        if ("village".equals(s.zone)) {
            return;
        }
        s.newbieKills++;
        boolean bagFull = s.equipBag.size() >= GameService.bagCap(s);
        // 武器保底:前 5 件;背包满则等待(不占已发件数),窗口放宽到 12 杀给清理时间
        if (s.newbieWeaponsGiven < 5 && s.newbieKills <= 12) {
            if (!bagFull) {
                PlayerState.Item w = EquipFactory.generateNewbieWeapon(s);
                if (w != null) {
                    s.equipBag.add(w);
                    s.newbieWeaponsGiven++;
                    s.bossFirstKillName = "新手保底 · " + w.name;   // 复用播报通道(金色 toast)
                    s.bossFirstKillGold = null;
                }
            }
        }
        // 回春戒保底:≥10 杀且本世未发过
        if (s.newbieKills >= 10 && !s.ringGranted) {
            PlayerState.Item ring = EquipFactory.generateNewbieRing(s);
            if (ring != null) {
                // 保底的意义在于立刻生效:有空戒指位直接穿上(躺着背包里 1% 回复等于没有),
                // 两戒都占位才进背包;背包满且无空位 → 不置位,下次击杀重试
                String freeRing = s.equipped.get("ring1") == null ? "ring1"
                        : (s.equipped.get("ring2") == null ? "ring2" : null);
                if (freeRing == null && bagFull) {
                    return;
                }
                s.ringGranted = true;
                if (freeRing != null) {
                    s.equipped.put(freeRing, ring);
                } else {
                    s.equipBag.add(ring);
                }
                s.bossFirstKillName = "新手保底 · 回春戒(每秒回复 1% 生命上限)";
                s.bossFirstKillGold = null;
            }
        }
    }

    private static void onMobKilled(PlayerState s, PlayerState.MobState m) {
        s.stats.kills = Num.add(s.stats.kills, Num.of(1));
        s.stats.lifetimeKills = Num.add(s.stats.lifetimeKills, Num.of(1));
        // 星蚀猎手口径（第八批）：击杀带星蚀词条的怪计数
        if ("star_eclipse".equals(m.trait)) {
            s.stats.eclipseKills++;
        }
        // 宠物经验（第九批）：出战宠物随击杀成长，升级时播报
        if (s.pet != null) {
            int[] petR = GameService.gainPetExp(s, GameConst.PET_EXP_PER_KILL);
            if (petR[1] > 0) {
                GameConst.PetDef pd = GameConst.PETS.get(s.pet);
                pushEvent(s, "🐾 " + (pd == null ? "宠物" : pd.name()) + "升到了 " + petR[0] + " 级！");
            }
        }
        // 无限属性点天赋:每杀一怪 +1 自由属性点
        if ("statlord".equals(s.talent)) {
            s.freePoints = Num.add(s.freePoints, Num.of(1));
        }
        // 尸检用击杀序列(设计 §3.3):最近 10 条,环形覆盖
        if (s.recentKills == null) {
            s.recentKills = new java.util.ArrayList<>();
        }
        long lived = Math.max(0, (System.currentTimeMillis() - s.stats.born) / 1000);
        s.recentKills.add(String.format("%02d:%02d %s", lived / 60, lived % 60, m.name));
        while (s.recentKills.size() > 10) {
            s.recentKills.remove(0);
        }
        // 新手保底(legacy 迁移):非村口击杀计数,每世重新保底
        grantNewbieDrops(s);
        // 图鉴：记录已击杀的怪物（Boss 记 boss_区域 id）
        if (m.boss) {
            s.codex.add("boss_" + s.zone);
            pushEvent(s, "👑 击败 Boss「" + (m.name == null ? "?" : m.name) + "」");
        } else if (m.id != null) {
            s.codex.add(m.id);
        }
        // 材料/图纸掉落（DESIGN.md §9：材料随区域，图纸低概率/Boss 高概率）
        String mat = GameConst.ZONE_MAT.get(s.zone);
        if (mat != null && Math.random() < materialChance(s)) {
            s.materials.merge(mat, 1, Integer::sum);
        }
        // 词条·晶化：击杀必额外掉 2 份本区材料（与基础 50% 掷骰独立，溢出期望击杀不带词条不享受）
        if ("crystal".equals(m.trait) && mat != null) {
            s.materials.merge(mat, GameConst.TRAIT_CRYSTAL_MATS, Integer::sum);
        }
        // 附魔卷轴：特定怪掉落（DESIGN.md §6.2/§8，40% 掉率）
        String scroll = GameConst.SCROLL_DROP.get(m.id);
        if (scroll != null && Math.random() < 0.4) {
            s.materials.merge(scroll, 1, Integer::sum);
        }
        // 宝石掉落（第八批）：Boss 必掉 1 颗随机宝石、词条怪 10%——宝石是镶嵌系统的唯一产出源
        if (m.boss || (m.trait != null && Math.random() < gemDropRate(s))) {
            String gem = randomGem();
            if (gem != null) {
                s.materials.merge(gem, 1, Integer::sum);
                GameConst.GemDef gd = GameConst.GEMS.get(gem);
                pushEvent(s, "💎 掉落" + (gd == null ? "宝石" : gd.name()) + "（锻造页镶嵌）");
            }
        }
        // 图纸掉落（DESIGN §9：Boss 30% / 普通怪 4%，幸运药剂同吃加成）：按区域深度池随机一张，存材料键 bp_<配方>
        if (Math.random() < (m.boss ? 0.30 : 0.04) * dropLuckMul(s)) {
            String bp = com.gongpu.cfg.GameData.instance().randomCraftBlueprint(s.zone);
            if (bp != null) {
                s.materials.merge(com.gongpu.craft.CraftService.blueprintKey(bp), 1, Integer::sum);
                pushEvent(s, "📐 掉落图纸（见锻造页）");
            }
        }

        s.stats.newbieKills++;
        BigDecimal gold = Num.mul(Num.of(m.gold), Num.mul(goldMul(s), traitGoldMul(m)));
        s.gold = Num.add(s.gold, gold);

        int dropCount = 0;
        double rate = com.gongpu.cfg.GameData.instance().dropRate(s.zone) * dropLuckMul(s);
        if (m.boss) {
            rate = 1.0;
        }
        if (Math.random() < rate && s.equipBag.size() < GameService.bagCap(s)) {
            PlayerState.Item it = EquipFactory.generate(s, s.zone, m.boss);
            if (it != null) {
                // 掉落拦截：自动分解优先（转材料），其次自动售卖（折现金币），都不命中才进背包
                String act = GameService.dropIntercept(s, it);
                if ("smelt".equals(act)) {
                    GameService.grantDismantle(s, it);
                    s.lastDropCount = 1;   // 分解计一次掉落,不进批量掉落漏计
                    return;   // 不进背包
                }
                if ("sell".equals(act)) {
                    s.gold = Num.add(s.gold, EquipFactory.sellPrice(it));
                    s.lastDropCount = 1;   // 折现计一次掉落,不进批量掉落漏计
                    return;   // 不进背包
                }
                s.equipBag.add(it);
                dropCount = 1;
                // 史诗掉落 → 世界公告（异步，不影响战斗结算）
                if (it.quality >= 3) {
                    pushEvent(s, "🟣 掉落史诗「" + (it.name == null ? "装备" : it.name) + "」");
                    try {
                        com.gongpu.stats.SocialService.publishBulletin(
                                s.bulletinName == null ? "无名武夫" : s.bulletinName,
                                it, zoneName(s.zone));
                    } catch (RuntimeException ignored) {
                        // 公告失败不影响掉落
                    }
                }
            }
        }
        s.lastDropCount = dropCount;
    }

    private static BigDecimal goldMul(PlayerState s) {
        BigDecimal mul = "goldhand".equals(s.talent) ? Num.of(1.20) : Num.of(1);
        // 聚宝药剂：击杀金币 +50%（经济类增益不走战斗面板，与鸿运药剂同口径不查沉默）
        if (buffLeft(s, "fortune_pot") > 0) {
            mul = Num.mul(mul, Num.of(GameConst.FORTUNE_POT_MUL));
        }
        return mul;
    }

    /** 战斗日志：追加一条事件，环形保留最近 20 条（旧档 events 为 null 时懒建）。 */
    /** 随机宝石（第八批）：一阶六种等权（二阶为融合产物，不进掉落池）。 */
    static String randomGem() {
        java.util.List<String> ids = GameConst.gemTier1Ids();
        return ids.isEmpty() ? null : ids.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(ids.size()));
    }

    /**
     * 金币里程碑播报（第八批）：金币跨过 10^k（k=4..15）时播报一次。
     * 由 GameController 在动作后、构建视图前调用——事件随本帧 events 下发。
     */
    public static void checkGoldMilestone(PlayerState s) {
        if (s.gold == null) {
            return;
        }
        for (int k = s.goldMilestone + 1; k <= 15; k++) {
            java.math.BigDecimal threshold = java.math.BigDecimal.TEN.pow(k);
            if (s.gold.compareTo(threshold) >= 0) {
                s.goldMilestone = k;
                pushEvent(s, "💰 金币里程碑：身家突破 " + threshold.toPlainString() + " 金！");
            } else {
                break;
            }
        }
    }

    static void pushEvent(PlayerState s, String text) {
        if (s.events == null) {
            s.events = new java.util.ArrayList<>();
        }
        s.events.add(text);
        while (s.events.size() > 20) {
            s.events.remove(0);
        }
    }

    /** 药剂 buff 是否生效（存在且未被沉默压制），引擎侧消费点统一口径。 */
    static boolean buffOn(PlayerState s, PanelCalculator.Panel p, String key) {
        return PanelCalculator.active(s, key) && (p == null || !p.silenced());
    }

    /** 词条攻速乘区：疾影常驻 ×1.5；狂暴在残血（<30%）时 ×1.5。无词条为 1。 */
    static double traitHasteMul(PlayerState.MobState m) {
        if (m == null || m.trait == null) {
            return 1.0;
        }
        if ("swift".equals(m.trait)) {
            return GameConst.TRAIT_SWIFT_HASTE;
        }
        if ("frenzy".equals(m.trait) && m.maxHP != null
                && Num.lt(m.curHP, Num.mul(m.maxHP, Num.of(GameConst.TRAIT_FRENZY_HP_LINE)))) {
            return GameConst.TRAIT_FRENZY_HASTE;
        }
        return 1.0;
    }

    /** 词条·富饶：击杀金币乘区（仅逐杀结算；批量溢出的期望击杀是普通怪，不吃词条）。 */
    static BigDecimal traitGoldMul(PlayerState.MobState m) {
        return m != null && "rich".equals(m.trait)
                ? Num.of(GameConst.TRAIT_RICH_GOLD_MUL)
                : Num.of(1);
    }

    /**
     * 击杀后的推进:Boss 发首杀奖励并回村(返回 true);普通怪刷下一只(返回 false)。
     * 普攻批量结算与格挡反弹两条击杀路径共用,避免流程漂移。
     */
    private static boolean advanceAfterKill(PlayerState s, PlayerState.MobState m) {
        if (m.boss) {
            s.stats.bossKillCount++;   // 终身 Boss 击杀计数（两条击杀路径共用此漏斗）
            if (m.phase2) {
                s.stats.bossPhaseKills++;   // 二阶段击破计数（第七批，破相者成就口径）
            }
            // 首杀奖励：每档每 Boss 一次，金币 1000×(区域序+1)
            if (!s.bossKills.contains(m.id)) {
                s.bossKills.add(m.id);
                List<String> zoneOrder = new ArrayList<>(GameConst.ZONES.keySet());
                int zi = Math.max(0, zoneOrder.indexOf(s.zone));
                java.math.BigDecimal bonus = Num.of(1000L * (zi + 1));
                s.gold = Num.add(s.gold, bonus);
                s.bossFirstKillName = m.name;
                s.bossFirstKillGold = bonus;
            }
            // Boss 连战：留在本区立即再刷 Boss（优先于自动推进，专刷首杀外收益/图纸）
            if (s.autoBoss) {
                spawnBoss(s);
                pushEvent(s, "👑 Boss 连战：新的 Boss 已经出现");
                return true;
            }
            // 自动推进：下一区存在且门槛达标 → 直接推进刷怪（写战斗日志）；否则回村
            if (s.autoAdvance) {
                String next = nextZoneId(s.zone);
                if (next != null && zoneReqMet(s, next)) {
                    s.zone = next;
                    s.mob = null;
                    spawnMob(s, next);
                    pushEvent(s, "🗺️ 自动推进至「" + zoneName(next) + "」");
                    return true;
                }
            }
            s.mob = null;
            s.zone = "village";
            return true;
        }
        spawnMob(s, s.zone);
        return false;
    }

    /** 区域顺序中的下一区（无下一区返回 null）。 */
    static String nextZoneId(String zone) {
        java.util.List<String> order = new ArrayList<>(GameConst.ZONES.keySet());
        int i = order.indexOf(zone);
        return i >= 0 && i + 1 < order.size() ? order.get(i + 1) : null;
    }

    /** 区域进入门槛是否达标（与 GameService.gotoZone 同口径：面板属性逐项比对）。 */
    static boolean zoneReqMet(PlayerState s, String zoneId) {
        GameConst.ZoneDef z = GameConst.ZONES.get(zoneId);
        if (z == null) {
            return false;
        }
        if (z.req() == null) {
            return true;
        }
        PanelCalculator.Panel p = PanelCalculator.of(s);
        for (java.util.Map.Entry<String, String> e : z.req().entrySet()) {
            java.math.BigDecimal need = new java.math.BigDecimal(e.getValue());
            java.math.BigDecimal have = switch (e.getKey()) {
                case "maxHP" -> p.maxHP();
                case "PDEF" -> p.PDEF();
                case "MDEF" -> p.MDEF();
                case "REGEN" -> p.REGEN();
                case "ATK" -> p.PATK().max(p.MATK());
                default -> Num.ZERO;
            };
            if (Num.lt(have, need)) {
                return false;
            }
        }
        return true;
    }

    // ==================== 怪物反击 ====================

    // 包内可见:格挡反弹击杀回归单测用
    static void mobAttack(PlayerState s, PanelCalculator.Panel p, double seconds) {
        PlayerState.MobState m = s.mob;
        if (m == null || m.ASPD <= 0) {
            return;
        }
        // 风筝：射程与移速双优，怪够不到你
        if (kite(p, m)) {
            return;
        }
        // 先手：射程长者先打；若玩家射程不足且怪射程更远，怪先够到
        double hits = m.ASPD * seconds * (1 - effEva(p, m).min(Num.of(0.95)).doubleValue());
        if (hits <= 0) {
            return;
        }
        // 词条攻速乘区：狂暴残血反扑 / 疾影常驻加速（traitHasteMul 统一）
        hits *= traitHasteMul(m);
        // Boss 二阶段（第七批）：狂怒攻速 +30%
        if (m.boss && m.phase2) {
            hits *= GameConst.BOSS_PHASE2_ASPD_MUL;
        }
        // 迟缓咒（第七批）：玩家施放的怪物减益，攻速 ×0.7（与词条/二阶段乘算）
        if (m.slowT > 0) {
            hits *= GameConst.HEX_SLOW_ASPD_MUL;
        }
        // 词条·破甲：结算时玩家的双防按比例打折
        BigDecimal defMul = "sunder".equals(m.trait)
                ? Num.of(1 - GameConst.TRAIT_SUNDER_IGNORE)
                : Num.of(1);
        BigDecimal phys = Num.max(Num.of(0),
                Num.sub(m.PATK, Num.mul(Num.mul(p.PDEF(), defMul), Num.of(GameConst.DMG_DEF_FACTOR))));
        BigDecimal mag = Num.gt(m.MATK, Num.ZERO)
                ? Num.max(Num.of(0), Num.sub(m.MATK, Num.mul(Num.mul(p.MDEF(), defMul), Num.of(GameConst.DMG_DEF_FACTOR))))
                : Num.ZERO;
        // Boss 二阶段（第七批）：狂怒攻击 +20%（双攻同乘区，在保底 1 点之前）
        if (m.boss && m.phase2) {
            phys = Num.mul(phys, Num.of(GameConst.BOSS_PHASE2_ATK_MUL));
            mag = Num.mul(mag, Num.of(GameConst.BOSS_PHASE2_ATK_MUL));
        }
        BigDecimal perHit = Num.max(Num.of(1), Num.add(phys, mag));   // 设计 §2.3 承伤同理:至少 1 点

        // 格挡：受击减半 + 反弹 50%(设计 §7.3;对减伤前的伤害反弹)
        boolean blocking = PanelCalculator.active(s, "block");
        if (blocking) {
            perHit = Num.mul(perHit, Num.of(0.5));
        }
        // 药剂·石肤：受到的伤害 -10%（与格挡乘算，沉默压制）
        if (buffOn(s, p, "stone_pot")) {
            perHit = Num.mul(perHit, Num.of(GameConst.STONE_POT_MUL));
        }
        // 技能·铁壁：受击减半（纯防御无反弹，与格挡同乘区叠加；防御技口径同格挡不查沉默）
        if (PanelCalculator.active(s, "iron_wall")) {
            perHit = Num.mul(perHit, Num.of(0.5));
        }
        BigDecimal total = Num.mul(perHit, Num.of(hits));
        if (total.signum() <= 0) {
            return;
        }
        // 护盾(v3.14 护盾药剂):先于生命扣除,吸收完后余量才进血
        total = absorbShield(s, total);
        s.life.curHP = Num.sub(s.life.curHP, total);
        // 词条·嗜血：怪按玩家实际掉血量比例吸回自身生命（封顶其上限；
        // 置于格挡反弹之前——反弹致死时怪已死，不存在吸血复活的顺序问题）
        // 词条·噬能（第九批）：怪按实际伤害比例抽玩家蓝（蓝量不低于 0）
        if ("mana_drain".equals(m.trait) && total.signum() > 0) {
            BigDecimal drain = Num.mul(total, Num.of(GameConst.TRAIT_DRAIN_MP_PCT));
            s.life.curMP = Num.max(Num.ZERO, Num.sub(s.life.curMP, drain));
        }
        if ("leech".equals(m.trait) && m.maxHP != null && total.signum() > 0) {
            BigDecimal heal = Num.mul(total, Num.of(GameConst.TRAIT_LEECH_MUL));
            m.curHP = Num.min(m.maxHP, Num.add(m.curHP, heal));
        }
        if (blocking && Num.gt(p.PATK(), Num.ZERO)) {
            // 反弹:格挡期间把承受伤害的 50% 弹回怪物(设计 §7.3 反弹 50%)
            BigDecimal reflect = Num.mul(total, Num.of(0.5));
            m.curHP = Num.sub(m.curHP, reflect);
            if (Num.lte(m.curHP, Num.ZERO)) {
                // 反弹致死走正常击杀流程——kill() 是玩家死亡函数,误用会把玩家自己"弹死",
                // 且击杀不计 kills/金币/掉落、怪物残留 0 血
                onMobKilled(s, m);
                advanceAfterKill(s, m);
                return;
            }
        }
        // 药剂·荆棘：实际承受伤害的 20% 反弹给怪（与格挡反弹可叠加，沉默压制；反弹致死同走击杀流程）
        if (buffOn(s, p, "thorn_pot") && total.signum() > 0) {
            BigDecimal thornReflect = Num.mul(total, Num.of(GameConst.THORN_POT_MUL));
            m.curHP = Num.sub(m.curHP, thornReflect);
            if (Num.lte(m.curHP, Num.ZERO)) {
                onMobKilled(s, m);
                advanceAfterKill(s, m);
                return;
            }
        }

        // 迟缓咒计时（第七批）：到期自动解除
        if (m.slowT > 0) {
            m.slowT = Math.max(0, m.slowT - seconds);
        }

        // Boss 沉默
        if (m.boss && m.silenceCd > 0) {
            m.silenceCd -= seconds;
            if (m.silenceCd <= 0) {
                m.silenceCd = GameConst.BOSS_SILENCE_CD;
                s.buffs.put("silence", GameConst.BOSS_SILENCE_DUR);
            }
        }

        // 词条·剧毒：任意物种带毒，命中概率上毒（与物种毒同口径 0.3/秒，时长较短）
        if ("venom".equals(m.trait) && Math.random() < Math.min(1, 0.3 * seconds)) {
            s.buffs.put("poison", GameConst.TRAIT_VENOM_DUR);
        }
        // 词条·时滞：黏液缠身，命中概率让玩家攻速 -20% 持续 4 秒（面板 ASPD 消费）
        if ("slow".equals(m.trait) && Math.random() < Math.min(1, GameConst.TRAIT_SLOW_CHANCE * seconds)) {
            s.buffs.put("slow", GameConst.TRAIT_SLOW_DUR);
        }
        // 词条·寒霜（第七批）：寒气侵体，命中概率上霜 5 秒、攻速 -15%（与时滞独立 buff 键、可叠加）
        if ("frost".equals(m.trait) && Math.random() < Math.min(1, GameConst.TRAIT_FROST_CHANCE * seconds)) {
            s.buffs.put("frost", GameConst.TRAIT_FROST_DUR);
        }
        // 毒 / 灼烧
        if (m.id != null) {
            if (m.id.contains("toad") && Math.random() < Math.min(1, 0.3 * seconds)) {
                s.buffs.put("poison", 10.0);
            }
            if ((m.id.contains("lava") || m.id.contains("inferno") || m.id.contains("golem"))
                    && Math.random() < Math.min(1, 0.3 * seconds)) {
                s.buffs.put("burn_dot", 10.0);
            }
        }

        if (Num.lte(s.life.curHP, Num.ZERO)) {
            kill(s, m);
        }
    }

    public static boolean kite(PanelCalculator.Panel p, PlayerState.MobState m) {
        return p.RNG().doubleValue() > m.RNG
                && p.SPD().doubleValue() >= m.SPD
                && m.RNG > 0;
    }

    // ==================== 持续伤害 / 回复 ====================

    static void dotTick(PlayerState s, double seconds) {   // 包内可见:DoT 封顶单测用
        PanelCalculator.Panel p = PanelCalculator.of(s);
        // DoT 伤害时长封顶为 buff 剩余秒(毒/灼烧仅 ~10s):
        // 离线大帧(effective 最长 8h)里 mobAttack 刚上的新毒若按全帧结算 =
        // 0.02×maxHP×14400 ≈ 288 倍生命上限,毒区离线必死(审查 P1-2)。在线 1s 帧行为不变。
        double poisonLeft = buffLeft(s, "poison");
        if (poisonLeft > 0) {
            double dur = Math.min(seconds, poisonLeft);
            BigDecimal dmg = Num.mul(Num.max(Num.of(1), Num.mul(p.maxHP(), Num.of(0.02))), Num.of(dur));
            s.life.curHP = Num.sub(s.life.curHP, absorbShield(s, dmg));
            s.buffs.put("poison", Math.max(0, poisonLeft - dur));
        }
        double burnLeft = buffLeft(s, "burn_dot");
        if (burnLeft > 0) {
            double dur = Math.min(seconds, burnLeft);
            BigDecimal dmg = Num.mul(Num.max(Num.of(1), Num.mul(p.maxHP(), Num.of(0.015))), Num.of(dur));
            s.life.curHP = Num.sub(s.life.curHP, absorbShield(s, dmg));
            s.buffs.put("burn_dot", Math.max(0, burnLeft - dur));
        }
        if (Num.lte(s.life.curHP, Num.ZERO)) {
            kill(s, s.mob);
        }
    }

    /** 幸运药剂（luck_pot）掉落倍率：生效中 1.5，否则 1.0。装备/图纸/溢出期望三处掉落同口径。 */
    /** 击杀材料掉率：基础 50%，被动「材料嗅觉」+25 个百分点（受沉默压制，与其它被动同口径）。 */
    static double materialChance(PlayerState s) {
        double chance = GameConst.MAT_BASE_CHANCE;
        if (s.passives.contains("material_sense") && !PanelCalculator.isSilenced(s)) {
            chance += GameConst.MAT_SENSE_BONUS;
        }
        return chance;
    }

    /** 词条怪宝石掉率（第八批）：基础 10%；被动·宝石嗅觉 ×1.5（受沉默压制）。 */
    static double gemDropRate(PlayerState s) {
        double rate = GameConst.GEM_TRAIT_DROP_RATE;
        if (s.passives.contains("gem_sniffer") && !PanelCalculator.isSilenced(s)) {
            rate *= GameConst.GEM_SNIFFER_MUL;
        }
        return rate;
    }

    static double dropLuckMul(PlayerState s) {
        double mul = buffLeft(s, "luck_pot") > 0 ? 1.5 : 1.0;
        // 被动·幸运星：掉落率 +10%（与鸿运药剂乘算；被动受沉默压制）
        if (s.passives.contains("lucky_star") && !PanelCalculator.isSilenced(s)) {
            mul *= GameConst.LUCKY_STAR_MUL;
        }
        // 天赋·幸运儿（第七批）：掉落率 +15%（天赋为先天禀赋，不受沉默压制）
        if ("lucky".equals(s.talent)) {
            mul *= GameConst.LUCKY_TALENT_MUL;
        }
        return mul;
    }

    private static double buffLeft(PlayerState s, String buff) {
        Double v = s.buffs.get(buff);
        return v == null ? 0 : Math.max(0, v);
    }

    /**
     * 护盾吸收(v3.14 护盾药剂):伤害先扣护盾,余量才返回给调用方扣血。
     * 护盾值随 shield_pot buff 存续;沉默不清盾(盾是已固化的吸收量,不是持续增益)。
     */
    static BigDecimal absorbShield(PlayerState s, BigDecimal dmg) {
        if (s.shield == null || s.shield.signum() <= 0 || dmg == null || dmg.signum() <= 0) {
            return dmg;
        }
        if (Num.gt(s.shield, dmg)) {
            s.shield = Num.sub(s.shield, dmg);
            return Num.ZERO;
        }
        BigDecimal left = Num.sub(dmg, s.shield);
        s.shield = Num.ZERO;
        return left;
    }

    /**
     * 挂机管家：buff 常驻链（legacy autobot autoBurn/autoSkill 迁移，语义一致）。
     * <p>全部走 {@link GameService#castSkill} 统一入口（CD/蓝耗/移速校验自带，失败抛异常即本帧跳过）。
     * 村口不自动施放；沉默期间跳过（增益全部失效，白耗蓝）。</p>
     * <ul>
     *   <li>autoSkill：涌动（蓝>50% 先行，后续技能免蓝）→ 急速 → 蓄力 常驻；血<60% 补格挡；血<30% 怒吼保命</li>
     *   <li>autoBurn：燃烧 buff 掉了且血量>60% 才补（烧当前血 25%，>60% 起步不可能烧死）</li>
     * </ul>
     */
    /** 自动进食：开启且饱食回血（regen_food）不在身、背包有食物时吃一份续上；村里不吃防浪费。 */
    private static void autoEatTick(PlayerState s) {
        if (!s.autoEat || "village".equals(s.zone)) {
            return;
        }
        if (PanelCalculator.active(s, "regen_food")) {
            return;
        }
        Integer have = s.potions.get("food");
        if (have == null || have <= 0) {
            return;
        }
        // 与 autoPotion 同款内联服用（usePotion 是实例方法，引擎层不回调用它）：
        // 食物的效果就是挂上其 buff（饱食回血），无即时回复部分
        GameConst.PotionDef def = com.gongpu.cfg.GameData.instance().potion("food");
        if (def == null || def.buff() == null) {
            return;
        }
        s.potions.put("food", have - 1);
        s.buffs.put(def.buff(), foodDur(s, def.buffDur()));
    }

    /** 食物时长口径（第七批）：铁胃天赋 ×1.5，手动服用与自动进食共用。 */
    static double foodDur(PlayerState s, double baseDur) {
        return "iron_gut".equals(s.talent) ? baseDur * GameConst.IRON_GUT_MUL : baseDur;
    }

    private static void autoBuffTick(PlayerState s) {
        if (s.dead || (!s.autoBurn && !s.autoSkill)) {
            return;
        }
        if ("village".equals(s.zone) || PanelCalculator.isSilenced(s)) {
            return;
        }
        PanelCalculator.Panel p = PanelCalculator.of(s);
        double hpRatio = Num.div(s.life.curHP, Num.max(p.maxHP(), Num.of(1))).doubleValue();
        double mpRatio = Num.div(s.life.curMP, Num.max(p.MP(), Num.of(1))).doubleValue();
        try {
            if (s.autoSkill) {
                if (!PanelCalculator.active(s, "surge") && mpRatio > 0.5) {
                    GameService.castSkill(s, "surge");
                }
                if (!PanelCalculator.active(s, "rapid")) {
                    GameService.castSkill(s, "rapid");
                }
                if (!PanelCalculator.active(s, "heavy")) {
                    GameService.castSkill(s, "heavy");
                }
                if (hpRatio < 0.6 && !PanelCalculator.active(s, "block")) {
                    GameService.castSkill(s, "block");
                }
                if (hpRatio < 0.30 && s.mob != null && s.mob.SPD > 0) {
                    GameService.castSkill(s, "roar");
                }
            }
            if (s.autoBurn && hpRatio > 0.6 && !PanelCalculator.active(s, "burn")) {
                GameService.castSkill(s, "burn");
            }
        } catch (RuntimeException ignored) {
            // 蓝不足/CD 中/移速不足/参数不合法：本帧跳过,下一结算帧再试
        }
    }

    /** 挂机策略：血量/蓝量跌破配置线时自动嗑药（先小药后大药）。 */
    private static void autoPotion(PlayerState s) {
        // 面板懒构建且全程复用一次:本方法只读 maxHP/MP,而嗑药/解毒只改当前血蓝与
        // 毒/灼烧 buff(均不影响上限),三个分支重算的面板上限完全一致
        PanelCalculator.Panel p = null;
        if (s.autoHpLine > 0 && !s.dead) {
            p = PanelCalculator.of(s);
            if (Num.lt(s.life.curHP, Num.mul(p.maxHP(), Num.of(s.autoHpLine)))
                    && Num.gt(s.life.curHP, Num.ZERO)) {
                // 先嗑大血药（血线已破，直接大补），再小血药
                if (useAutoPotion(s, "hp_l", p)) {
                    // 大药生效
                } else {
                    useAutoPotion(s, "hp_s", p);
                }
            }
        }
        if (s.autoMpLine > 0) {
            if (p == null) {
                p = PanelCalculator.of(s);
            }
            if (Num.lt(s.life.curMP, Num.mul(p.MP(), Num.of(s.autoMpLine)))) {
                useAutoPotion(s, "mp", p);
            }
        }
        // 管家：中毒/灼烧自动嗑解毒剂（没药自动买一瓶——与 useAutoPotion 同语义，
        // 此前本分支只查库存，与界面「没药自动买」的标注不符，断粮时解毒形同虚设）
        if (s.autoCure && (PanelCalculator.active(s, "poison") || PanelCalculator.active(s, "burn_dot"))) {
            Integer have = s.potions.get("cure");
            if (have == null || have <= 0) {
                try {
                    GameService.buyPotion(s, "cure", 1);
                } catch (RuntimeException ignored) {
                    // 金币不足/药剂不存在：静默，下帧再试
                }
                have = s.potions.get("cure");
            }
            if (have != null && have > 0) {
                s.potions.put("cure", have - 1);
                s.buffs.remove("poison");
                s.buffs.remove("burn_dot");
            }
        }
        // 管家：血量跌破逃跑线 → 自动逃跑回村（保命优先于挂机收益）
        if (s.autoFleeLine > 0 && !s.dead && !"village".equals(s.zone)) {
            if (p == null) {
                p = PanelCalculator.of(s);
            }
            // 中毒/灼烧时逃跑线上浮 +15%(legacy autobot 语义,上限 50%)——持续掉血要提前跑
            double line = s.autoFleeLine;
            if (PanelCalculator.active(s, "poison") || PanelCalculator.active(s, "burn_dot")) {
                line = Math.min(0.5, line + 0.15);
            }
            if (Num.gt(p.maxHP(), Num.ZERO)
                    && Num.lt(s.life.curHP, Num.mul(p.maxHP(), Num.of(line)))) {
                s.zone = "village";
                s.mob = null;
                spawnMob(s, s.zone);
            }
        }
    }

    /** 自动消耗一瓶药剂；没药时金币够则自动买一瓶(legacy autoCure/买药语义)；返回是否成功消耗。 */
    private static boolean useAutoPotion(PlayerState s, String id, PanelCalculator.Panel p) {
        Integer have = s.potions.get(id);
        if (have == null || have <= 0) {
            // 自动买药:挂机断粮时用金币补一瓶(买不起静默,下帧再试)
            try {
                GameService.buyPotion(s, id, 1);
            } catch (RuntimeException ignored) {
                return false;   // 金币不足/药剂不存在
            }
            have = s.potions.get(id);
            if (have == null || have <= 0) {
                return false;
            }
        }
        com.gongpu.cfg.GameData gd = com.gongpu.cfg.GameData.instance();
        GameConst.PotionDef def = gd.potion(id);
        if (def == null) {
            return false;
        }
        s.potions.put(id, have - 1);
        if (def.healHP() > 0) {
            java.math.BigDecimal heal = Num.mul(Num.mul(p.maxHP(), Num.of(def.healHP())), healMul(s));
            s.life.curHP = Num.min(Num.add(s.life.curHP, heal), p.maxHP());
        }
        if (def.healMP() > 0) {
            java.math.BigDecimal heal = Num.mul(p.MP(), Num.of(def.healMP()));
            s.life.curMP = Num.min(Num.add(s.life.curMP, heal), p.MP());
        }
        return true;
    }

    private static void regenTick(PlayerState s, double seconds) {
        PanelCalculator.Panel p = PanelCalculator.of(s);
        // 常规回血 + 「每秒回复 X% 生命上限」类词条(回春戒「生命泉涌」等)；诅咒在场时回血减半
        BigDecimal heal = Num.mul(Num.mul(Num.add(p.REGEN(), p.regenHpPerSec()), Num.of(seconds)), healMul(s));
        s.life.curHP = Num.min(Num.add(s.life.curHP, heal), p.maxHP());
        BigDecimal mpHeal = Num.mul(p.MREGEN(), Num.of(seconds));
        s.life.curMP = Num.min(Num.add(s.life.curMP, mpHeal), p.MP());
    }

    // ==================== 增益计时 ====================

    private static void tickBuff(PlayerState s, double seconds) {
        boolean eternal = s.eternal();
        for (String k : new java.util.ArrayList<>(s.buffs.keySet())) {
            if (eternal && GameConst.ETERNAL_BUFFS.contains(k)) {
                continue;
            }
            double left = s.buffs.get(k) - seconds;
            if (left <= 0) {
                s.buffs.remove(k);
                // 护盾随 shield_pot 到期清零(v3.14):盾是 buff 附属量,失去 buff 盾即消失
                if ("shield_pot".equals(k)) {
                    s.shield = BigDecimal.ZERO;
                }
            } else {
                s.buffs.put(k, left);
            }
        }
        for (String k : new java.util.ArrayList<>(s.cds.keySet())) {
            double left = s.cds.get(k) - seconds;
            if (left <= 0) {
                s.cds.remove(k);
            } else {
                s.cds.put(k, left);
            }
        }
    }

    // ==================== 死亡 / 复活 ====================

    private static void kill(PlayerState s, PlayerState.MobState m) {
        s.dead = true;
        s.life.curHP = Num.ZERO;
        s.stats.deaths++;
        // 死因带上怪物词条（如 ·🌵荆棘）：否则被反扎/毒杀时死亡画面只显示怪名，玩家无从知晓死因
        String traitTxt = "";
        if (m != null && m.trait != null) {
            GameConst.TraitDef td = GameConst.MOB_TRAITS.get(m.trait);
            if (td != null) {
                traitTxt = "·" + td.emoji() + td.name();
            }
        }
        s.deathCause = m != null ? (m.name + "（" + zoneName(s.zone) + "）" + traitTxt) : "力竭而亡";
    }

    private static String zoneName(String zone) {
        GameConst.ZoneDef z = GameConst.ZONES.get(zone);
        return z != null ? z.name() : zone;
    }

    /** 大数转 long，溢出时饱和到 Long.MAX_VALUE（用于统计字段）。 */
    private static long toLongSaturated(BigDecimal v) {
        if (v == null || v.signum() <= 0) {
            return 0L;
        }
        BigDecimal f = v.setScale(0, RoundingMode.DOWN);
        if (f.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) >= 0) {
            return Long.MAX_VALUE;
        }
        return f.longValue();
    }

    // ==================== 刷怪 ====================

    public static void spawnMob(PlayerState s, String zoneId) {
        GameConst.ZoneDef z = GameConst.ZONES.get(zoneId);
        if (z == null || z.mobs() == null || z.mobs().isEmpty()) {
            s.mob = null;
            return;
        }
        GameConst.MobDef def = z.mobs().get((int) (Math.random() * z.mobs().size()));
        s.mob = toMob(def, false);
        // 野怪出生掷词条（v3.15）：村口不出、越深越常见，Boss 走 spawnBoss 不带词条
        s.mob.trait = GameConst.rollMobTrait(zoneId);
    }

    public static void spawnBoss(PlayerState s) {
        GameConst.ZoneDef z = GameConst.ZONES.get(s.zone);
        if (z == null || z.boss() == null) {
            throw com.gongpu.common.ApiException.badRequest("该区域没有 Boss");
        }
        s.mob = toMob(z.boss(), true);
    }

    private static PlayerState.MobState toMob(GameConst.MobDef def, boolean boss) {
        PlayerState.MobState m = new PlayerState.MobState();
        m.id = def.id();
        m.name = def.name();
        m.emoji = def.emoji();
        m.maxHP = def.maxHP() == null ? null : new BigDecimal(def.maxHP());
        m.curHP = m.maxHP;
        m.PATK = new BigDecimal(def.PATK());
        m.MATK = new BigDecimal(def.MATK());
        m.PDEF = new BigDecimal(def.PDEF());
        m.MDEF = new BigDecimal(def.MDEF());
        m.ASPD = def.ASPD();
        m.RNG = def.RNG();
        m.SPD = def.SPD();
        m.gold = def.gold();
        m.boss = boss;
        m.silenceCd = boss ? GameConst.BOSS_SILENCE_CD : 0;
        return m;
    }

    // ==================== 状态视图 ====================

    public static Map<String, Object> snapshot(PlayerState s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gen", s.gen);
        m.put("zone", s.zone);
        m.put("dead", s.dead);
        m.put("gold", Num.plain(s.gold));
        m.put("life", s.life.snapshot());
        m.put("fixed", s.fixed.snapshot());
        m.put("blessings", PanelCalculator.blessLine(s));
        m.put("punches", Num.plain(s.stats.punches));
        m.put("kills", Num.plain(s.stats.kills));
        m.put("talent", s.talent);
        Map<String, Object> mob = null;
        if (s.mob != null) {
            mob = new LinkedHashMap<>();
            mob.put("id", s.mob.id);
            mob.put("name", s.mob.name);
            mob.put("emoji", s.mob.emoji);
            mob.put("curHP", s.mob.maxHP == null ? "Infinity" : Num.plain(s.mob.curHP));
            mob.put("maxHP", s.mob.maxHP == null ? "Infinity" : Num.plain(s.mob.maxHP));
            mob.put("boss", s.mob.boss);
        }
        m.put("mob", mob);
        return m;
    }
}
