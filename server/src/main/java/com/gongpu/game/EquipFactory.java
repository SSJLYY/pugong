package com.gongpu.game;

import com.gongpu.common.Num;
import com.gongpu.state.PlayerState;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 装备生成：底子 + 随机词条。
 * <p>装备只能靠打怪爆或图纸合成，商店不卖成品（DESIGN.md §16 第 14 条）。</p>
 */
public final class EquipFactory {

    private EquipFactory() {
    }

    /** 底子定义。 */
    private record Base(String name, String emoji, String slot, Map<String, String> stats, int tier, String desc) {
    }

    private static final List<Base> WEAPONS = List.of(
            new Base("新手木棍", "🥢", "weapon", Map.of("PATK", "2", "RNG", "1"), 0, "白给"),
            new Base("兔骨匕首", "🦴", "weapon", Map.of("PATK", "2", "ASPD", "0.15", "RNG", "1"), 0, "攻速启蒙"),
            new Base("铁剑", "🗡️", "weapon", Map.of("PATK", "8", "RNG", "1"), 1, "标准"),
            new Base("匕首", "🔪", "weapon", Map.of("PATK", "4", "ASPD", "0.3", "RNG", "1"), 1, "攻速流"),
            new Base("轻弩", "🎯", "bow", Map.of("PATK", "4", "ASPD", "-0.05", "RNG", "4"), 1, "新手远程"),
            new Base("长枪", "🔱", "weapon", Map.of("PATK", "12", "RNG", "3"), 2, "近战手长者"),
            new Base("法杖", "🪄", "weapon", Map.of("MATK", "20", "RNG", "6"), 2, "纯魔路线"),
            new Base("双手大剑", "⚔️", "weapon", Map.of("PATK", "30", "ASPD", "-0.3", "RNG", "2"), 3, "输出暴增成长变慢"),
            new Base("猎弓", "🏹", "bow", Map.of("PATK", "10", "RNG", "5", "ASPD", "-0.1"), 1, "远程入门"),
            new Base("长弓", "🏹", "bow", Map.of("PATK", "18", "RNG", "7", "ASPD", "-0.2"), 2, "风筝核心"),
            new Base("附魔弓", "🏹", "bow", Map.of("PATK", "12", "MATK", "12", "RNG", "8", "ASPD", "-0.2"), 3, "物魔双修"));

    private static final Map<String, List<Base>> ARMOR = buildArmor();

    private static Map<String, List<Base>> buildArmor() {
        Map<String, List<Base>> m = new LinkedHashMap<>();
        m.put("offhand", List.of(
                new Base("木盾", "🛡️", "offhand", Map.of("PDEF", "4", "SPD", "-5"), 0, "皇上点名款"),
                new Base("塔盾", "🛡️", "offhand", Map.of("PDEF", "15", "SPD", "-20"), 2, "乌龟流")));
        m.put("head", List.of(
                new Base("皮帽", "🎩", "head", Map.of("PDEF", "2"), 0, ""),
                new Base("铁盔", "⛑️", "head", Map.of("PDEF", "5", "MDEF", "2"), 1, "")));
        m.put("chest", List.of(
                new Base("皮甲", "🥋", "chest", Map.of("PDEF", "3", "SPD", "3"), 0, "风筝流"),
                new Base("锁子甲", "👕", "chest", Map.of("PDEF", "10", "SPD", "-5"), 2, "")));
        m.put("legs", List.of(
                new Base("皮裤", "👖", "legs", Map.of("PDEF", "2", "SPD", "1"), 0, ""),
                new Base("铁腿甲", "🦵", "legs", Map.of("PDEF", "6", "SPD", "-3"), 2, "")));
        m.put("boots", List.of(
                new Base("草鞋", "👟", "boots", Map.of("SPD", "5"), 0, ""),
                new Base("疾风靴", "🥾", "boots", Map.of("SPD", "15"), 1, "跑图逃跑")));
        m.put("ring", List.of(
                new Base("铜戒", "💍", "ring1", Map.of("PATK", "1"), 0, ""),
                new Base("蜂鸟戒指", "💍", "ring1", Map.of("ASPD", "0.1"), 3, "最稀有掉落")));
        m.put("necklace", List.of(
                new Base("骨链", "📿", "necklace", Map.of("PDEF", "1", "MDEF", "1"), 0, ""),
                new Base("魔力项链", "📿", "necklace", Map.of("MATK", "5", "MREGEN", "2"), 1, "")));
        return Map.copyOf(m);
    }

    /**
     * 生成一件装备。
     *
     * @param boss 是否 Boss 掉落（必掉 + 品质保底紫装）
     */
    public static PlayerState.Item generate(PlayerState s, String zoneId, boolean boss) {
        GameConst.ZoneDef zone = GameConst.ZONES.get(zoneId);
        if (zone == null || zone.safe()) {
            return null;
        }

        List<String> order = new ArrayList<>(GameConst.ZONES.keySet());
        int zoneIdx = order.indexOf(zoneId);
        int tierBoost = Math.min(3, Math.max(0, zoneIdx / 2));

        // 品质：Boss 必出紫装，25% 升史诗
        int qIdx;
        if (boss) {
            qIdx = ThreadLocalRandom.current().nextDouble() < 0.25 ? 3 : 2;
        } else {
            double roll = ThreadLocalRandom.current().nextDouble() * 100 + tierBoost * 15;
            qIdx = roll < 50 ? 0 : roll < 75 ? 1 : roll < 92 ? 2 : 3;
        }
        int affixCount = GameConst.QUALITY_AFFIX_COUNT[qIdx];

        // 选底子：DB 配置优先，空表回落硬编码
        com.gongpu.cfg.GameData.Tpl tpl = pickTpl(zoneId, tierBoost);
        String name;
        String emoji;
        String slot;
        Map<String, BigDecimal> baseStats;
        String desc;
        if (tpl != null) {
            name = tpl.name();
            emoji = tpl.emoji();
            slot = tpl.slot();
            baseStats = tpl.base();
            desc = tpl.remark();
        } else {
            Base base = pickBase(tierBoost);
            name = base.name();
            emoji = base.emoji();
            slot = base.slot();
            desc = base.desc();
            baseStats = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : base.stats().entrySet()) {
                baseStats.put(e.getKey(), new BigDecimal(e.getValue()));
            }
        }

        PlayerState.Item it = new PlayerState.Item();
        it.uid = s.nextUid++;
        it.name = name;
        it.emoji = emoji;
        it.slot = slot;
        it.quality = qIdx;
        it.enhance = 0;
        it.desc = desc;
        it.zone = zoneId;
        it.code = tpl != null ? tpl.code() : null;
        for (Map.Entry<String, BigDecimal> e : baseStats.entrySet()) {
            it.base.put(e.getKey(), e.getValue());
        }

        // 刷词条（同名词条不重复）：DB 配置优先
        List<GameConst.Affix> pool = new ArrayList<>(com.gongpu.cfg.GameData.instance().affixPool());
        for (int i = 0; i < affixCount && !pool.isEmpty(); i++) {
            GameConst.Affix a = pool.remove(ThreadLocalRandom.current().nextInt(pool.size()));
            double v = a.min() + ThreadLocalRandom.current().nextDouble() * (a.max() - a.min());
            PlayerState.AffixInst inst = new PlayerState.AffixInst();
            inst.name = a.name();
            inst.key = a.key();
            inst.type = a.type();
            inst.val = Num.of(round(v));
            inst.rarity = a.rarity();
            inst.sideKey = a.sideKey();
            inst.sideVal = a.sideVal() == null ? null : Num.of(a.sideVal());
            it.affixes.add(inst);
        }
        return it;
    }

    /** DB 装备池筛选：按区域 min_zone 与 tier 过滤；空池返回 null（回落硬编码）。 */
    private static com.gongpu.cfg.GameData.Tpl pickTpl(String zoneId, int tierBoost) {
        com.gongpu.cfg.GameData gd = com.gongpu.cfg.GameData.instance();
        if (!gd.hasEquipment()) {
            return null;
        }
        List<com.gongpu.cfg.GameData.Tpl> all = gd.equipmentPool();
        List<String> order = new ArrayList<>(GameConst.ZONES.keySet());
        int zoneIdx = order.indexOf(zoneId);
        List<com.gongpu.cfg.GameData.Tpl> eligible = new ArrayList<>();
        for (com.gongpu.cfg.GameData.Tpl t : all) {
            // min_zone 门槛：模板声明的最低区域不得深于当前区域
            if (t.minZone() != null && !t.minZone().isBlank()) {
                int need = order.indexOf(t.minZone());
                if (need > zoneIdx) {
                    continue;
                }
            }
            // tier 门槛：高 tier 模板在浅区域低概率出现（同硬编码逻辑）
            if (t.tier() > tierBoost && ThreadLocalRandom.current().nextDouble() >= 0.3) {
                continue;
            }
            eligible.add(t);
        }
        if (eligible.isEmpty()) {
            return null;
        }
        return eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
    }

    private static Base pickBase(int tierBoost) {
        List<Base> pool = new ArrayList<>();
        pool.addAll(WEAPONS);
        for (List<Base> list : ARMOR.values()) {
            pool.addAll(list);
        }
        int minTier = Math.min(3, tierBoost);
        List<Base> filtered = pool.stream()
                .filter(b -> b.tier() >= minTier || ThreadLocalRandom.current().nextDouble() < 0.3)
                .toList();
        List<Base> use = filtered.isEmpty() ? pool : filtered;
        return use.get(ThreadLocalRandom.current().nextInt(use.size()));
    }

    private static double round(double v) {
        return Math.round(v * 10000d) / 10000d;
    }

    // ==================== 新手保底(legacy 迁移) ====================

    /** 新手保底武器名单(legacy NEWBIE_WEAPONS):前 5 杀按计数轮换发放。 */
    private static final List<String> NEWBIE_WEAPONS =
            List.of("新手木棍", "兔骨匕首", "铁剑", "匕首", "轻弩");

    /** 第 newbieKills 杀的新手保底武器:精良品质 + 1 条随机词条(保留刷词条乐趣)。 */
    public static PlayerState.Item generateNewbieWeapon(PlayerState s) {
        Base base = null;
        for (Base b : WEAPONS) {
            if (NEWBIE_WEAPONS.get((s.newbieKills - 1) % NEWBIE_WEAPONS.size()).equals(b.name())) {
                base = b;
                break;
            }
        }
        if (base == null) {
            base = WEAPONS.get(0);
        }
        PlayerState.Item it = new PlayerState.Item();
        it.uid = s.nextUid++;
        it.name = base.name();
        it.emoji = base.emoji();
        it.slot = base.slot();
        it.quality = 1;
        it.enhance = 0;
        it.desc = "新手保底";
        for (Map.Entry<String, String> e : base.stats().entrySet()) {
            it.base.put(e.getKey(), new BigDecimal(e.getValue()));
        }
        rollAffixesInto(it, 1);
        return it;
    }

    /** 新手保底戒指「回春戒」:史诗,每秒回复 1% 生命上限(legacy 回春戒同款)。 */
    public static PlayerState.Item generateNewbieRing(PlayerState s) {
        PlayerState.Item it = new PlayerState.Item();
        it.uid = s.nextUid++;
        it.name = "回春戒";
        it.emoji = "💍";
        it.slot = "ring1";
        it.quality = 3;
        it.enhance = 0;
        it.desc = "新手保底 · 生命如泉涌";
        it.base.put("PATK", new BigDecimal("2"));
        it.base.put("REGEN", new BigDecimal("3"));
        PlayerState.AffixInst spring = new PlayerState.AffixInst();
        spring.name = "生命泉涌";
        spring.key = "REGEN_HP";
        spring.type = "pct_hp";
        spring.val = Num.of(0.01);
        spring.rarity = "epic";
        it.affixes.add(spring);
        PlayerState.AffixInst sharp = new PlayerState.AffixInst();
        sharp.name = "锋利";
        sharp.key = "PATK";
        sharp.type = "flat";
        sharp.val = Num.of(12);
        sharp.rarity = "common";
        it.affixes.add(sharp);
        PlayerState.AffixInst vigorous = new PlayerState.AffixInst();
        vigorous.name = "坚韧";
        vigorous.key = "REGEN";
        vigorous.type = "flat";
        vigorous.val = Num.of(2);
        vigorous.rarity = "common";
        it.affixes.add(vigorous);
        return it;
    }

    /** 从公共词条池随机抽 count 条塞入装备(同名不重复),保底装共用。 */
    private static void rollAffixesInto(PlayerState.Item it, int count) {
        List<GameConst.Affix> pool = new ArrayList<>(com.gongpu.cfg.GameData.instance().affixPool());
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            GameConst.Affix a = pool.remove(ThreadLocalRandom.current().nextInt(pool.size()));
            double v = a.min() + ThreadLocalRandom.current().nextDouble() * (a.max() - a.min());
            PlayerState.AffixInst inst = new PlayerState.AffixInst();
            inst.name = a.name();
            inst.key = a.key();
            inst.type = a.type();
            inst.val = Num.of(round(v));
            inst.rarity = a.rarity();
            inst.sideKey = a.sideKey();
            inst.sideVal = a.sideVal() == null ? null : Num.of(a.sideVal());
            it.affixes.add(inst);
        }
    }

    /** 洗炼：重刷词条（保留底子与强化等级；lockIdx 指定一条锁定的词条不动）。 */
    public static void rerollAffixes(PlayerState.Item it, int lockIdx) {
        // 锁定词条（原样保留）
        PlayerState.AffixInst locked = null;
        if (lockIdx >= 0 && lockIdx < it.affixes.size()) {
            locked = it.affixes.get(lockIdx);
        }
        it.affixes.clear();
        List<GameConst.Affix> pool = new ArrayList<>(com.gongpu.cfg.GameData.instance().affixPool());
        if (locked != null) {
            // 锁定词条不放回池（同名不重复）
            final String lockName = locked.name;
            pool.removeIf(a -> a.name().equals(lockName));
            it.affixes.add(locked);
        }
        int count = GameConst.QUALITY_AFFIX_COUNT[Math.max(0, Math.min(3, it.quality))];
        for (int i = it.affixes.size(); i < count && !pool.isEmpty(); i++) {
            GameConst.Affix a = pool.remove(ThreadLocalRandom.current().nextInt(pool.size()));
            double v = a.min() + ThreadLocalRandom.current().nextDouble() * (a.max() - a.min());
            PlayerState.AffixInst inst = new PlayerState.AffixInst();
            inst.name = a.name();
            inst.key = a.key();
            inst.type = a.type();
            inst.val = Num.of(round(v));
            inst.rarity = a.rarity();
            inst.sideKey = a.sideKey();
            inst.sideVal = a.sideVal() == null ? null : Num.of(a.sideVal());
            it.affixes.add(inst);
        }
    }

    /** 图纸合成产出：指定底子模板 + 随机词条（品质由配方传入）。 */
    public static PlayerState.Item generateCraft(PlayerState s, String productCode, int quality) {
        com.gongpu.cfg.GameData.Tpl tpl = null;
        for (com.gongpu.cfg.GameData.Tpl t : com.gongpu.cfg.GameData.instance().equipmentPool()) {
            if (t.code().equals(productCode)) {
                tpl = t;
                break;
            }
        }
        if (tpl == null) {
            return null;
        }
        PlayerState.Item it = new PlayerState.Item();
        it.uid = s.nextUid++;
        it.name = tpl.name();
        it.emoji = tpl.emoji();
        it.slot = tpl.slot();
        it.quality = Math.max(0, Math.min(3, quality));
        it.enhance = 0;
        it.desc = tpl.remark();
        it.code = tpl.code();
        it.base.putAll(tpl.base());
        // 词条随机（品质决定条数）
        List<GameConst.Affix> pool = new ArrayList<>(com.gongpu.cfg.GameData.instance().affixPool());
        int affixCount = GameConst.QUALITY_AFFIX_COUNT[it.quality];
        for (int i = 0; i < affixCount && !pool.isEmpty(); i++) {
            GameConst.Affix a = pool.remove(ThreadLocalRandom.current().nextInt(pool.size()));
            double v = a.min() + ThreadLocalRandom.current().nextDouble() * (a.max() - a.min());
            PlayerState.AffixInst inst = new PlayerState.AffixInst();
            inst.name = a.name();
            inst.key = a.key();
            inst.type = a.type();
            inst.val = Num.of(round(v));
            inst.rarity = a.rarity();
            inst.sideKey = a.sideKey();
            inst.sideVal = a.sideVal() == null ? null : Num.of(a.sideVal());
            it.affixes.add(inst);
        }
        return it;
    }

    /** 洗炼花费：品质与强化越高越贵。 */
    public static BigDecimal rerollCost(PlayerState.Item it) {
        return Num.of(200L * (it.quality + 1) * (it.enhance + 1));
    }

    /** 强化花费。 */
    public static BigDecimal enhanceCost(PlayerState.Item it) {
        return Num.mul(Num.of(GameConst.ENHANCE_COST_BASE),
                Num.of(Math.pow(GameConst.ENHANCE_COST_MUL, it.enhance)));
    }

    /** 卖价：品质越高越值钱。 */
    public static BigDecimal sellPrice(PlayerState.Item it) {
        return Num.mul(Num.of(20L * (it.quality + 1)), Num.of(1 + it.enhance * 0.5));
    }
}
