package com.gongpu.cfg;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gongpu.common.Num;
import com.gongpu.game.GameConst;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 配置注册表：装备模板 / 词条 / 商品 / 技能 / 状态 / 爆率。
 * <p>加载策略：<b>DB 优先，内置兜底</b>——
 * 表里没有数据（或解析失败）时回落到 GameConst/EquipFactory 的硬编码默认值，
 * 保证空库也能玩；运维端改表后调用 {@link #reload()} 即时生效（全内存替换，无锁竞争）。</p>
 * <p>所有玩家路径只读本类的不可变快照，绝不直接触碰 Repository。</p>
 * <p>v3.10 性能：加载时预构建 code→模板 的不可变索引。此前 dropRate/potion/skill 等
 * 查询在持锁结算区内每帧线性扫表 + 反复 parseJson（autoPotion 每帧 4 次、
 * castSkill 每帧最多 5 次），玩家量上来后是纯浪费的热路径分配。</p>
 */
@Component
public class GameData {

    private static final Logger log = LoggerFactory.getLogger(GameData.class);
    private static final ObjectMapper M = new ObjectMapper();

    // ==================== 不可变快照 ====================

    public record Snapshot(
            List<EquipmentTemplate> equipment,
            List<AffixTemplate> affixes,
            List<ItemTemplate> items,
            List<SkillTemplate> skills,
            List<BuffTemplate> buffs,
            List<DropRateRule> dropRates,
            List<CraftTemplate> crafts) {
    }

    /** 快照 + 预构建索引，整体原子替换。 */
    private record Indexed(
            Snapshot s,
            Map<String, ItemTemplate> itemByCode,
            Map<String, GameConst.PotionDef> potionByCode,
            List<GameConst.PotionDef> potionList,
            Map<String, SkillTemplate> skillByCode,
            Map<String, GameConst.SkillDef> activeSkillByCode,
            List<GameConst.SkillDef> activeSkillList,
            List<Map<String, Object>> passiveListView,
            Map<String, Map<String, Object>> passiveEffectByCode,
            Map<String, Integer> passivePriceByCode,
            Map<String, DropRateRule> rateByZone,
            Map<String, CraftTemplate> craftByCode,
            Map<String, Integer> blueprintNeedByCode,
            // 第二十批·轮4：掉落池随快照预构建（原每次掉落重建池+逐模板解析 baseStats JSON）
            List<GameConst.Affix> affixPool,
            List<Tpl> equipmentPool) {
    }

    private final AtomicReference<Indexed> snap = new AtomicReference<>(new Indexed(
            emptySnapshot(), Map.of(), Map.of(), List.of(), Map.of(), Map.of(), List.of(),
            List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of(), List.of()));

    /** 静态入口：供 GameEngine 等静态上下文使用（构造时注册）。 */
    private static volatile GameData INSTANCE;

    public static GameData instance() {
        GameData g = INSTANCE;
        if (g == null) {
            throw new IllegalStateException("GameData 尚未初始化");
        }
        return g;
    }

    // ==================== Repositories ====================

    private final EquipmentTemplateRepository equipmentRepo;
    private final AffixTemplateRepository affixRepo;
    private final ItemTemplateRepository itemRepo;
    private final SkillTemplateRepository skillRepo;
    private final BuffTemplateRepository buffRepo;
    private final DropRateRuleRepository dropRepo;
    private final CraftTemplateRepository craftRepo;

    public GameData(EquipmentTemplateRepository equipmentRepo, AffixTemplateRepository affixRepo,
                    ItemTemplateRepository itemRepo, SkillTemplateRepository skillRepo,
                    BuffTemplateRepository buffRepo, DropRateRuleRepository dropRepo,
                    CraftTemplateRepository craftRepo) {
        this.equipmentRepo = equipmentRepo;
        this.affixRepo = affixRepo;
        this.itemRepo = itemRepo;
        this.skillRepo = skillRepo;
        this.buffRepo = buffRepo;
        this.dropRepo = dropRepo;
        this.craftRepo = craftRepo;
        INSTANCE = this;
        load();
    }

    private static Snapshot emptySnapshot() {
        return new Snapshot(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    // ==================== 加载 / 刷新 ====================

    /** 启动时加载（@PostConstruct 或配置类调用）。失败不抛——回落内置默认。 */
    public void load() {
        try {
            Snapshot s = new Snapshot(
                    equipmentRepo.findByEnabledTrueOrderBySortNoAscIdAsc(),
                    affixRepo.findByEnabledTrueOrderBySortNoAscIdAsc(),
                    itemRepo.findByEnabledTrueOrderBySortNoAscIdAsc(),
                    skillRepo.findByEnabledTrueOrderBySortNoAscIdAsc(),
                    buffRepo.findByEnabledTrueOrderBySortNoAscIdAsc(),
                    dropRepo.findByEnabledTrueOrderBySortNoAsc(),
                    craftRepo.findByEnabledTrueOrderBySortNoAscIdAsc());
            snap.set(buildIndexed(s));
            log.info("GameData 已加载：装备 {} / 词条 {} / 商品 {} / 技能 {} / 状态 {} / 爆率 {}",
                    s.equipment().size(), s.affixes().size(), s.items().size(),
                    s.skills().size(), s.buffs().size(), s.dropRates().size());
        } catch (RuntimeException e) {
            log.warn("GameData 加载失败，使用内置默认配置：{}", e.getMessage());
        }
    }

    /** 运维端改表后调用：全量重读（原子替换快照，玩家请求无感知）。 */
    public void reload() {
        load();
    }

    public Snapshot snapshot() {
        return snap.get().s();
    }

    /** 索引构建：只在 load() 里发生，玩家路径全部 O(1) 查询。 */
    private static Indexed buildIndexed(Snapshot s) {
        Map<String, ItemTemplate> itemByCode = new HashMap<>();
        Map<String, GameConst.PotionDef> potionByCode = new HashMap<>();
        List<GameConst.PotionDef> potionList = new ArrayList<>();
        for (ItemTemplate t : s.items()) {
            itemByCode.put(t.getCode(), t);
            // 原语义:potion(code) 对任意 kind 的 item 都能按 effectJson 出视图,保持一致;
            // potionList(meta 商店列表)只收 potion 类
            GameConst.PotionDef def = toPotionDef(t);
            potionByCode.put(t.getCode(), def);
            if ("potion".equals(t.getKind())) {
                potionList.add(def);
            }
        }
        Map<String, SkillTemplate> skillByCode = new HashMap<>();
        Map<String, GameConst.SkillDef> activeSkillByCode = new HashMap<>();
        List<GameConst.SkillDef> activeSkillList = new ArrayList<>();
        List<Map<String, Object>> passiveListView = new ArrayList<>();
        Map<String, Map<String, Object>> passiveEffectByCode = new HashMap<>();
        Map<String, Integer> passivePriceByCode = new HashMap<>();
        for (SkillTemplate t : s.skills()) {
            skillByCode.put(t.getCode(), t);
            if ("active".equals(t.getKind())) {
                GameConst.SkillDef def = new GameConst.SkillDef(t.getCode(), t.getName(), str(t.getEmoji()),
                        t.getMp() == null ? 0 : t.getMp(),
                        t.getCd() == null ? 0 : t.getCd(),
                        t.getDur() == null ? 0 : t.getDur(),
                        str(t.getDesc()));
                activeSkillByCode.put(t.getCode(), def);
                activeSkillList.add(def);
            } else if ("passive".equals(t.getKind())) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", t.getCode());
                m.put("name", t.getName());
                m.put("emoji", str(t.getEmoji()));
                m.put("desc", str(t.getDesc()));
                m.put("price", t.getPrice());
                passiveListView.add(m);
                passiveEffectByCode.put(t.getCode(), parseJsonStatic(t.getEffectJson()));
                passivePriceByCode.put(t.getCode(), t.getPrice() == null ? 300 : t.getPrice());
            }
        }
        Map<String, DropRateRule> rateByZone = new HashMap<>();
        for (DropRateRule r : s.dropRates()) {
            rateByZone.put(r.getZone(), r);
        }
        Map<String, CraftTemplate> craftByCode = new HashMap<>();
        Map<String, Integer> blueprintNeedByCode = new HashMap<>();
        Map<String, String> equipMinZoneByCode = new HashMap<>();
        for (EquipmentTemplate e : s.equipment()) {
            equipMinZoneByCode.put(e.getCode(), e.getMinZone());
        }
        List<String> zoneOrder = new ArrayList<>(GameConst.ZONES.keySet());
        for (CraftTemplate t : s.crafts()) {
            craftByCode.put(t.getCode(), t);
            if (t.getNeedBlueprint()) {
                String mz = equipMinZoneByCode.get(t.getProductCode());
                int need = mz == null || mz.isBlank() ? 0 : Math.max(0, zoneOrder.indexOf(mz));
                blueprintNeedByCode.put(t.getCode(), need);
            }
        }
        // 第二十批·轮4：词条池/装备池一次性构建进快照，掉落路径零重建零 JSON 解析
        List<GameConst.Affix> affixPool = new ArrayList<>();
        for (AffixTemplate t : s.affixes()) {
            affixPool.add(new GameConst.Affix(
                    t.getName(), t.getStatKey(), t.getStatType(),
                    t.getMinVal() == null ? 0 : t.getMinVal(),
                    t.getMaxVal() == null ? 0 : t.getMaxVal(),
                    t.getRarity() == null ? "common" : t.getRarity(),
                    t.getSideKey(), t.getSideVal()));
        }
        List<Tpl> equipmentPool = new ArrayList<>();
        for (EquipmentTemplate t : s.equipment()) {
            Map<String, BigDecimal> stats = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : parseJsonStatic(t.getBaseStats()).entrySet()) {
                stats.put(e.getKey(), Num.parse(String.valueOf(e.getValue()), Num.ZERO));
            }
            equipmentPool.add(new Tpl(t.getCode(), t.getName(), str(t.getEmoji()), t.getSlot(),
                    stats, t.getTier() == null ? 0 : t.getTier(),
                    t.getSellBase() == null ? 20 : t.getSellBase(),
                    t.getEnhanceMax() == null ? GameConst.ENHANCE_MAX_LV : t.getEnhanceMax(),
                    t.getMinZone(), str(t.getRemark())));
        }
        return new Indexed(s, Map.copyOf(itemByCode), Map.copyOf(potionByCode), List.copyOf(potionList),
                Map.copyOf(skillByCode), Map.copyOf(activeSkillByCode), List.copyOf(activeSkillList),
                List.copyOf(passiveListView), Map.copyOf(passiveEffectByCode), Map.copyOf(passivePriceByCode),
                Map.copyOf(rateByZone), Map.copyOf(craftByCode), Map.copyOf(blueprintNeedByCode),
                List.copyOf(affixPool), List.copyOf(equipmentPool));
    }

    private static GameConst.PotionDef toPotionDef(ItemTemplate t) {
        Map<String, Object> e = parseJsonStatic(t.getEffectJson());
        return new GameConst.PotionDef(
                t.getCode(), t.getName(), str(t.getEmoji()), str(t.getDesc()),
                dbl(e.get("healHP")), dbl(e.get("healMP")),
                str(e.get("buff")), dbl(e.get("buffVal")), dbl(e.get("buffDur")),
                t.getPrice() == null ? 0 : t.getPrice());
    }

    // ==================== 查询（玩家路径，O(1)） ====================

    /** 区域爆率：DB 配置优先，否则回落 GameConst。 */
    public double dropRate(String zone) {
        DropRateRule r = snap.get().rateByZone().get(zone);
        if (r != null) {
            return r.getRate();
        }
        return GameConst.DROP_RATE.getOrDefault(zone, 0.0);
    }

    /** 批量溢出掉落护栏。 */
    public int dropBatchCap(String zone) {
        DropRateRule r = snap.get().rateByZone().get(zone);
        if (r != null) {
            return r.getBatchCap();
        }
        return 3;
    }

    /** 商品定义（code → PotionDef 视图）。 */
    public GameConst.PotionDef potion(String code) {
        GameConst.PotionDef def = snap.get().potionByCode().get(code);
        if (def != null) {
            return def;
        }
        return GameConst.POTIONS.get(code);
    }

    /** 全部商品（meta 下发用，DB 优先）。 */
    public List<GameConst.PotionDef> potions() {
        List<GameConst.PotionDef> out = snap.get().potionList();
        if (!out.isEmpty()) {
            return out;
        }
        return new ArrayList<>(GameConst.POTIONS.values());
    }

    /** 被动技能定义（含学习价，DB 优先）。 */
    public List<Map<String, Object>> passiveList() {
        List<Map<String, Object>> out = snap.get().passiveListView();
        if (!out.isEmpty()) {
            return out;
        }
        // 空库兜底:必须新建列表(passiveListView 是不可变缓存,直接 add 会炸)
        List<Map<String, Object>> fallback = new ArrayList<>();
        for (GameConst.PassiveDef d : GameConst.PASSIVE_SKILLS.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.id());
            m.put("name", d.name());
            m.put("emoji", d.emoji());
            m.put("desc", d.desc());
            m.put("price", 300);
            fallback.add(m);
        }
        return fallback;
    }

    /** 主动技能定义（DB 优先）。 */
    public List<GameConst.SkillDef> skillList() {
        List<GameConst.SkillDef> out = snap.get().activeSkillList();
        if (!out.isEmpty()) {
            return out;
        }
        return new ArrayList<>(GameConst.ACTIVE_SKILLS.values());
    }

    /** 被动技能效果参数（供 PanelCalculator/GameEngine 使用，DB 优先）。 */
    public Map<String, Object> passiveEffect(String code) {
        return snap.get().passiveEffectByCode().get(code);
    }

    /** 被动学习价（DB 优先，默认 300）。 */
    public int passivePrice(String code) {
        return snap.get().passivePriceByCode().getOrDefault(code, 300);
    }

    /** 主动技能定义单查（施法用，DB 优先）。 */
    public GameConst.SkillDef skill(String code) {
        GameConst.SkillDef def = snap.get().activeSkillByCode().get(code);
        if (def != null) {
            return def;
        }
        return GameConst.ACTIVE_SKILLS.get(code);
    }

    /** 词条池（生成装备用，DB 优先；第二十批起走快照预构建，不再每次重建）。 */
    public List<GameConst.Affix> affixPool() {
        List<GameConst.Affix> out = snap.get().affixPool();
        if (!out.isEmpty()) {
            return out;
        }
        return GameConst.AFFIXES;
    }

    /** 装备模板底子（生成装备用，DB 优先；第二十批起走快照预构建，含 baseStats 已解析）。 */
    public List<Tpl> equipmentPool() {
        return snap.get().equipmentPool();
    }

    /** DB 是否有装备模板（为空时 EquipFactory 回落硬编码）。 */
    public boolean hasEquipment() {
        return !snap.get().s().equipment().isEmpty();
    }

    public boolean hasAffixes() {
        return !snap.get().s().affixes().isEmpty();
    }

    /** 装备模板运行时视图。 */
    public record Tpl(String code, String name, String emoji, String slot,
                      Map<String, BigDecimal> base, int tier, int sellBase,
                      int enhanceMax, String minZone, String remark) {
    }

    /** 合成配方列表（启用中）。 */
    public List<CraftTemplate> craftList() {
        return snap.get().s().crafts();
    }

    /** 单查配方。 */
    public CraftTemplate craft(String code) {
        return snap.get().craftByCode().get(code);
    }

    /** 按当前区域随机掉一张图纸（产装备的 min_zone 不深于该区域的配方中随机）。 */
    public String randomCraftBlueprint(String zone) {
        List<String> order = new ArrayList<>(com.gongpu.game.GameConst.ZONES.keySet());
        int zi = Math.max(0, order.indexOf(zone));
        List<String> pool = new ArrayList<>();
        snap.get().blueprintNeedByCode().forEach((code, need) -> {
            if (need <= zi) {
                pool.add(code);
            }
        });
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
    }

    // ==================== 工具 ====================

    private Map<String, Object> parseJson(String json) {
        return parseJsonStatic(json);
    }

    private static Map<String, Object> parseJsonStatic(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return M.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("配置 JSON 解析失败：{}", json);
            return Map.of();
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static double dbl(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
