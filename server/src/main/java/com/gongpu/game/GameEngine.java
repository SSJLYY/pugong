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
    static int dealDamage(PlayerState s, PanelCalculator.Panel p, BigDecimal punches, double seconds) {
        PlayerState.MobState m = s.mob;
        if (m == null) {
            return 0;
        }

        // 有效每拳伤害（含暴击期望）
        BigDecimal critMul = Num.add(Num.of(1), Num.mul(p.CRIT(), Num.sub(p.CRITD(), Num.of(1))));
        BigDecimal pd = Num.max(Num.of(1),
                Num.sub(Num.mul(p.PATK(), critMul), Num.mul(m.PDEF, Num.of(GameConst.DMG_DEF_FACTOR))));
        BigDecimal md = Num.gt(p.MATK(), Num.ZERO)
                ? Num.max(Num.of(1), Num.sub(Num.mul(p.MATK(), critMul), Num.mul(m.MDEF, Num.of(GameConst.DMG_DEF_FACTOR))))
                : Num.ZERO;
        BigDecimal perHit = Num.add(pd, md);
        // 词条·缴械：武器被压制，普攻伤害打折（与蓄力重击同为乘区，先后不影响结果）
        if ("disarm".equals(m.trait)) {
            perHit = Num.mul(perHit, Num.of(GameConst.TRAIT_DISARM_MUL));
        }
        // 被动·巨人杀手：对 Boss 专伤乘区
        if (m.boss && s.passives.contains("boss_hunter")) {
            perHit = Num.mul(perHit, Num.of(GameConst.BOSS_HUNTER_MUL));
        }
        if (PanelCalculator.active(s, "heavy")) {
            perHit = Num.mul(perHit, Num.of(5));
            s.buffs.remove("heavy");
        }

        BigDecimal totalDmg = Num.mul(perHit, punches);

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
        // 词条「荆棘」：本帧（以开帧时目标词条为准）所造伤害的 10% 反扎玩家，护盾先吸；
        // 扎死当场死亡——advance 后续阶段（嗑药/怪反击/DoT）均以 !dead 守卫，不会二次结算
        if ("thorns".equals(m.trait) && !s.dead && Num.gt(totalDmg, Num.ZERO)) {
            BigDecimal reflect = Num.mul(totalDmg, Num.of(GameConst.TRAIT_THORNS_MUL));
            s.life.curHP = Num.sub(s.life.curHP, absorbShield(s, reflect));
            if (Num.lte(s.life.curHP, Num.ZERO)) {
                kill(s, m);
            }
        }
        return drops;
    }

    private static void healByLifesteal(PlayerState s, PanelCalculator.Panel p, BigDecimal dmg) {
        if (Num.gt(p.LIFESTEAL(), Num.ZERO)) {
            BigDecimal heal = Num.mul(dmg, p.LIFESTEAL());
            s.life.curHP = Num.min(Num.add(s.life.curHP, heal), s.life.maxHP);
        }
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
        } else if (m.id != null) {
            s.codex.add(m.id);
        }
        // 材料/图纸掉落（DESIGN.md §9：材料随区域，图纸低概率/Boss 高概率）
        String mat = GameConst.ZONE_MAT.get(s.zone);
        if (mat != null && Math.random() < 0.5) {
            s.materials.merge(mat, 1, Integer::sum);
        }
        // 附魔卷轴：特定怪掉落（DESIGN.md §6.2/§8，40% 掉率）
        String scroll = GameConst.SCROLL_DROP.get(m.id);
        if (scroll != null && Math.random() < 0.4) {
            s.materials.merge(scroll, 1, Integer::sum);
        }
        // 图纸掉落（DESIGN §9：Boss 30% / 普通怪 4%，幸运药剂同吃加成）：按区域深度池随机一张，存材料键 bp_<配方>
        if (Math.random() < (m.boss ? 0.30 : 0.04) * dropLuckMul(s)) {
            String bp = com.gongpu.cfg.GameData.instance().randomCraftBlueprint(s.zone);
            if (bp != null) {
                s.materials.merge(com.gongpu.craft.CraftService.blueprintKey(bp), 1, Integer::sum);
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
        return "goldhand".equals(s.talent) ? Num.of(1.20) : Num.of(1);
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
            s.mob = null;
            s.zone = "village";
            return true;
        }
        spawnMob(s, s.zone);
        return false;
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
        double hits = m.ASPD * seconds * (1 - p.EVA().min(Num.of(0.95)).doubleValue());
        if (hits <= 0) {
            return;
        }
        // 词条攻速乘区：狂暴残血反扑 / 疾影常驻加速（traitHasteMul 统一）
        hits *= traitHasteMul(m);
        // 词条·破甲：结算时玩家的双防按比例打折
        BigDecimal defMul = "sunder".equals(m.trait)
                ? Num.of(1 - GameConst.TRAIT_SUNDER_IGNORE)
                : Num.of(1);
        BigDecimal phys = Num.max(Num.of(0),
                Num.sub(m.PATK, Num.mul(Num.mul(p.PDEF(), defMul), Num.of(GameConst.DMG_DEF_FACTOR))));
        BigDecimal mag = Num.gt(m.MATK, Num.ZERO)
                ? Num.max(Num.of(0), Num.sub(m.MATK, Num.mul(Num.mul(p.MDEF(), defMul), Num.of(GameConst.DMG_DEF_FACTOR))))
                : Num.ZERO;
        BigDecimal perHit = Num.max(Num.of(1), Num.add(phys, mag));   // 设计 §2.3 承伤同理:至少 1 点

        // 格挡：受击减半 + 反弹 50%(设计 §7.3;对减伤前的伤害反弹)
        boolean blocking = PanelCalculator.active(s, "block");
        if (blocking) {
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

        // Boss 沉默
        if (m.boss && m.silenceCd > 0) {
            m.silenceCd -= seconds;
            if (m.silenceCd <= 0) {
                m.silenceCd = GameConst.BOSS_SILENCE_CD;
                s.buffs.put("silence", GameConst.BOSS_SILENCE_DUR);
            }
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
    static double dropLuckMul(PlayerState s) {
        return buffLeft(s, "luck_pot") > 0 ? 1.5 : 1.0;
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
        s.buffs.put(def.buff(), def.buffDur());
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
        // 管家：中毒/灼烧自动嗑解毒剂
        if (s.autoCure && (PanelCalculator.active(s, "poison") || PanelCalculator.active(s, "burn_dot"))) {
            Integer have = s.potions.get("cure");
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
            java.math.BigDecimal heal = Num.mul(p.maxHP(), Num.of(def.healHP()));
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
        // 常规回血 + 「每秒回复 X% 生命上限」类词条(回春戒「生命泉涌」等)
        BigDecimal heal = Num.mul(Num.add(p.REGEN(), p.regenHpPerSec()), Num.of(seconds));
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
        s.deathCause = m != null ? (m.name + "（" + zoneName(s.zone) + "）") : "力竭而亡";
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
