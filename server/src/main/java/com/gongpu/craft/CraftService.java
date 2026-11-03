package com.gongpu.craft;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gongpu.cfg.CraftTemplate;
import com.gongpu.cfg.GameData;
import com.gongpu.common.ApiException;
import com.gongpu.common.Num;
import com.gongpu.game.EquipFactory;
import com.gongpu.game.GameService;
import com.gongpu.state.PlayerState;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 图纸合成（DESIGN §6/§9）：图纸 + 材料 + 金币 → 指定底子装备（词条随机）。
 * <p>配方/图纸池/产物模板早已配齐（cfg_craft + EquipFactory.generateCraft +
 * GameData.randomCraftBlueprint），本服务把掉落与合成两端接通。
 * 图纸以材料键 {@code bp_<配方code>} 存于 materials；品质口径：基础配方 1（优秀）、
 * 图纸配方 2（稀有），25% 概率升一品（封顶 3 史诗）。</p>
 */
@Service
public class CraftService {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 图纸材料键前缀。 */
    public static String blueprintKey(String craftCode) {
        return "bp_" + craftCode;
    }

    static Map<String, Integer> parseMats(String matsJson) {
        if (matsJson == null || matsJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Integer> m = JSON.readValue(matsJson, new TypeReference<>() {});
            return m == null ? Map.of() : m;
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** 合成板：全部启用配方 + 玩家持有量 + 可合成标记（前端只渲染）。 */
    public Map<String, Object> board(PlayerState s) {
        List<CraftTemplate> crafts = new ArrayList<>(GameData.instance().snapshot().crafts());
        crafts.removeIf(t -> !Boolean.TRUE.equals(t.getEnabled()));
        crafts.sort(Comparator.comparingInt(t -> t.getSortNo() == null ? 0 : t.getSortNo()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CraftTemplate t : crafts) {
            boolean itemProduct = isItemProduct(t.getProductCode());
            GameData.Tpl tpl = itemProduct ? null : productTpl(t.getProductCode());
            com.gongpu.cfg.ItemTemplate itpl = itemProduct ? itemTpl(t.getProductCode()) : null;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", t.getCode());
            row.put("name", t.getName());
            row.put("emoji", t.getEmoji());
            row.put("productCode", t.getProductCode());
            row.put("productName", itemProduct
                    ? (itpl == null ? t.getProductCode() : itpl.getName())
                    : (tpl == null ? t.getProductCode() : tpl.name()));
            row.put("productEmoji", itemProduct
                    ? (itpl == null ? "❓" : (itpl.getEmoji() == null ? "🎁" : itpl.getEmoji()))
                    : (tpl == null ? "❓" : tpl.emoji()));
            row.put("productSlot", itemProduct ? "item" : (tpl == null ? "" : tpl.slot()));
            row.put("goldCost", t.getGoldCost() == null ? 0 : t.getGoldCost());
            row.put("needBlueprint", Boolean.TRUE.equals(t.getNeedBlueprint()));
            row.put("blueprintHave", s.materials.getOrDefault(blueprintKey(t.getCode()), 0));
            List<Map<String, Object>> mats = new ArrayList<>();
            boolean matsOk = true;
            for (Map.Entry<String, Integer> e : parseMats(t.getMatsJson()).entrySet()) {
                int have = s.materials.getOrDefault(e.getKey(), 0);
                if (have < e.getValue()) {
                    matsOk = false;
                }
                String[] info = com.gongpu.game.GameConst.MAT_INFO.get(e.getKey());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("code", e.getKey());
                m.put("name", info == null ? e.getKey() : info[0]);
                m.put("emoji", info == null ? "🧩" : info[1]);
                m.put("have", have);
                m.put("need", e.getValue());
                mats.add(m);
            }
            row.put("mats", mats);
            boolean bpOk = !Boolean.TRUE.equals(t.getNeedBlueprint())
                    || s.materials.getOrDefault(blueprintKey(t.getCode()), 0) >= 1;
            boolean goldOk = Num.gte(s.gold, Num.of(t.getGoldCost() == null ? 0 : t.getGoldCost()));
            // 物品类产物（烹饪等）进药剂栏叠加，不占背包
            boolean bagOk = itemProduct || s.equipBag.size() < GameService.bagCap(s);
            row.put("canCraft", bpOk && matsOk && goldOk && bagOk);
            row.put("blockReason", !bpOk ? "缺图纸（打怪掉落）"
                    : !matsOk ? "材料不足"
                    : !goldOk ? "金币不足"
                    : !bagOk ? "背包已满" : "");
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("crafts", rows);
        return out;
    }

    /** 执行合成：全校验通过才扣料，产物入背包。 */
    public Map<String, Object> craft(PlayerState s, String code) {
        CraftTemplate t = GameData.instance().craft(code);
        if (t == null || !Boolean.TRUE.equals(t.getEnabled())) {
            throw ApiException.badRequest("配方不存在: " + code);
        }
        boolean needBp = Boolean.TRUE.equals(t.getNeedBlueprint());
        String bpKey = blueprintKey(code);
        if (needBp && s.materials.getOrDefault(bpKey, 0) < 1) {
            throw ApiException.badRequest("缺少图纸（打怪或 Boss 掉落）");
        }
        Map<String, Integer> mats = parseMats(t.getMatsJson());
        for (Map.Entry<String, Integer> e : mats.entrySet()) {
            if (s.materials.getOrDefault(e.getKey(), 0) < e.getValue()) {
                throw ApiException.badRequest("材料不足");
            }
        }
        BigDecimal goldCost = Num.of(t.getGoldCost() == null ? 0 : t.getGoldCost());
        if (Num.lt(s.gold, goldCost)) {
            throw ApiException.badRequest("金币不足");
        }
        // 产物分两类：装备（默认）与物品（product_code 以 "item:" 前缀 → 药剂/食物，进药剂栏叠加）
        boolean itemProduct = isItemProduct(t.getProductCode());
        PlayerState.Item item = null;
        int quality = -1;
        if (itemProduct) {
            if (itemTpl(t.getProductCode()) == null) {
                throw ApiException.badRequest("产物配置缺失: " + t.getProductCode());
            }
        } else {
            if (s.equipBag.size() >= GameService.bagCap(s)) {
                throw ApiException.badRequest("背包已满，先卖掉或丢弃一些装备");
            }
            quality = needBp ? 2 : 1;
            if (ThreadLocalRandom.current().nextDouble() < 0.25) {
                quality = Math.min(3, quality + 1);
            }
            item = EquipFactory.generateCraft(s, t.getProductCode(), quality);
            if (item == null) {
                throw ApiException.badRequest("产物配置缺失: " + t.getProductCode());
            }
        }
        // 校验全过 → 扣料发放
        if (needBp) {
            consumeMat(s, bpKey, 1);
        }
        mats.forEach((k, v) -> consumeMat(s, k, v));
        s.gold = Num.sub(s.gold, goldCost);
        s.stats.crafts++;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        if (itemProduct) {
            String itemCode = t.getProductCode().substring("item:".length());
            s.potions.merge(itemCode, 1, Integer::sum);
            out.put("itemName", itemTpl(t.getProductCode()).getName());
        } else {
            s.equipBag.add(item);
            out.put("itemName", item.name);
            out.put("quality", item.quality);
        }
        return out;
    }

    /** 物品类产物约定：product_code = "item:<物品code>"（烹饪等配方，数据侧既有约定）。 */
    static boolean isItemProduct(String productCode) {
        return productCode != null && productCode.startsWith("item:");
    }

    private static com.gongpu.cfg.ItemTemplate itemTpl(String productCode) {
        String code = productCode.substring("item:".length());
        for (com.gongpu.cfg.ItemTemplate t : GameData.instance().snapshot().items()) {
            if (code.equals(t.getCode())) {
                return t;
            }
        }
        return null;
    }

    private static void consumeMat(PlayerState s, String key, int n) {
        int left = s.materials.getOrDefault(key, 0) - n;
        if (left > 0) {
            s.materials.put(key, left);
        } else {
            s.materials.remove(key);
        }
    }

    private static GameData.Tpl productTpl(String productCode) {
        for (GameData.Tpl t : GameData.instance().equipmentPool()) {
            if (t.code().equals(productCode)) {
                return t;
            }
        }
        return null;
    }
}
