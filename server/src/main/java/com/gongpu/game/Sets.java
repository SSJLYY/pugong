package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.PlayerState;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 装备套装：六个区域各一套四件套，穿戴 2 件 / 4 件激活分档加成。
 * <p>加成走面板 flat 区（与装备词条同口径：装备本体收益，不受沉默压制）。
 * 成员以模板 code 判定；旧档装备无 code 时按名回退查模板池（快照身份缓存）。</p>
 */
public final class Sets {

    private Sets() {
    }

    /** 套装定义：bonus2 = 穿 2 件加成，bonus4 = 穿满 4 件的额外加成（与 bonus2 叠加）。 */
    public record SetDef(String id, String name, String emoji, List<String> members,
                         Map<String, BigDecimal> bonus2, Map<String, BigDecimal> bonus4) {
    }

    private static Map<String, BigDecimal> b(String... kv) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], new BigDecimal(kv[i + 1]));
        }
        return Map.copyOf(m);
    }

    public static final List<SetDef> ALL = List.of(
            new SetDef("farwalker", "远行皮装", "🌾",
                    List.of("leather_cap", "leather_armor", "leather_pants", "straw_sandals"),
                    b("maxHP", "150"), b("maxHP", "350", "REGEN", "5")),
            new SetDef("vinetalk", "藤语猎装", "🌲",
                    List.of("iron_helmet", "vine_armor", "load_belt", "swift_boots"),
                    b("PATK", "10"), b("PATK", "25", "CRIT", "0.03")),
            new SetDef("ironwall", "铁壁矿装", "⛏️",
                    List.of("miner_helm", "chain_mail", "iron_greaves", "tower_shield"),
                    b("PDEF", "10"), b("PDEF", "25", "MDEF", "15")),
            new SetDef("marshwalk", "泥沼行装", "🐸",
                    List.of("marsh_robe", "marsh_leggings", "marsh_boots", "marsh_charm"),
                    b("REGEN", "15"), b("REGEN", "40", "maxHP", "800")),
            new SetDef("snowwolf", "雪狼战装", "🏔️",
                    List.of("snow_hood", "snow_wolf_armor", "frost_greaves", "snow_boots"),
                    b("MATK", "15"), b("MATK", "40", "EVA", "0.03")),
            new SetDef("molten", "熔火战装", "🌋",
                    List.of("lava_helm", "dragon_scale_mail", "lava_greaves", "lava_aegis"),
                    b("PATK", "30", "MATK", "30"), b("PATK", "80", "MATK", "80", "CRITD", "0.20")));

    private static final Map<String, SetDef> BY_MEMBER = buildByMember();
    private static final Map<String, SetDef> BY_ID = buildById();

    private static Map<String, SetDef> buildByMember() {
        Map<String, SetDef> m = new LinkedHashMap<>();
        for (SetDef d : ALL) {
            for (String code : d.members()) {
                m.put(code, d);
            }
        }
        return Map.copyOf(m);
    }

    private static Map<String, SetDef> buildById() {
        Map<String, SetDef> m = new LinkedHashMap<>();
        for (SetDef d : ALL) {
            m.put(d.id(), d);
        }
        return Map.copyOf(m);
    }

    public static SetDef byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    /** 某模板 code 所属套装（非套装件返回 null）。 */
    public static SetDef byMember(String code) {
        return code == null ? null : BY_MEMBER.get(code);
    }

    // ---- 旧档按名回退：name → code 映射按 GameData 快照身份缓存 ----

    private static volatile GameData.Snapshot nameSnap;
    private static volatile Map<String, String> nameToCode = Map.of();

    /** 装备的模板 code：字段优先；旧档（code 为空）按装备名回退查模板池。 */
    public static String codeOf(PlayerState.Item it) {
        if (it == null) {
            return null;
        }
        if (it.code != null && !it.code.isBlank()) {
            return it.code;
        }
        if (it.name == null) {
            return null;
        }
        GameData gd;
        try {
            gd = GameData.instance();
        } catch (RuntimeException e) {
            return null;
        }
        GameData.Snapshot snap = gd.snapshot();
        if (snap != nameSnap) {
            Map<String, String> m = new LinkedHashMap<>();
            for (GameData.Tpl t : gd.equipmentPool()) {
                m.putIfAbsent(t.name(), t.code());
            }
            nameToCode = Map.copyOf(m);
            nameSnap = snap;
        }
        return nameToCode.get(it.name);
    }

    /** 已穿戴装备中每套的件数（key = 套装 id，含 0 件的套装）。 */
    public static Map<String, Integer> wornCounts(PlayerState s) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (SetDef d : ALL) {
            out.put(d.id(), 0);
        }
        if (s == null || s.equipped == null) {
            return out;
        }
        for (PlayerState.Item it : s.equipped.values()) {
            SetDef d = byMember(codeOf(it));
            if (d != null) {
                out.merge(d.id(), 1, Integer::sum);
            }
        }
        return out;
    }

    /** 当前已激活的套装加成合计（flat 区口径，key 与 PanelCalculator flat 键一致）。 */
    public static Map<String, BigDecimal> activeBonus(PlayerState s) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        Map<String, Integer> counts = wornCounts(s);
        for (SetDef d : ALL) {
            int n = counts.getOrDefault(d.id(), 0);
            if (n >= 2) {
                d.bonus2().forEach((k, v) -> out.merge(k, v, Num::add));
            }
            if (n >= 4) {
                d.bonus4().forEach((k, v) -> out.merge(k, v, Num::add));
            }
        }
        return out;
    }
}
