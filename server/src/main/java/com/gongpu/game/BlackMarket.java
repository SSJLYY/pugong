package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.ApiException;
import com.gongpu.common.Num;
import com.gongpu.state.PlayerState;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 每日黑市（第七批）：每天刷 3 种药剂打折卖（30–50% 折），每种限购 {@link #LIMIT_PER_ITEM} 件。
 * 货单按（日期， 存档种子）确定性生成并快照存档——整日不变、跨设备一致、转生不重刷（当日已存快照）。
 * 与每日任务同为「当日」维度：resetForNewLife 不动黑市字段，跨日由 ensure 重刷。
 */
public final class BlackMarket {

    private BlackMarket() {
    }

    /** 每种药剂每日限购件数。 */
    public static final int LIMIT_PER_ITEM = 5;

    /** 黑市药池（增益/功能向为主，基础补给不进黑市）。 */
    static final List<String> POOL = List.of(
            "atk", "def", "aspd", "crit", "critd", "lifesteal", "giant", "fortune",
            "luck", "might", "guard", "stone", "thorn", "spirit", "vital", "elixir",
            "stardust", "void", "chaos", "pet_treat",
            "phoenix", "hongmeng", "timesand", "fleet",
            "guixu", "taixu", "rage", "pierce");

    static String today() {
        return LocalDate.now(GameConst.DAILY_ZONE).toString();
    }

    /** 跨日或无货单时（重新）生成当日货单：同种子同日结果恒定。 */
    public static void ensure(PlayerState s) {
        String today = today();
        if (today.equals(s.marketDay) && s.marketOffers != null && !s.marketOffers.isEmpty()) {
            return;
        }
        Random rnd = new Random(today.hashCode() * 31L + s.stats.born);
        List<String> pool = new ArrayList<>(POOL);
        Collections.shuffle(pool, rnd);
        List<PlayerState.MarketOffer> offers = new ArrayList<>();
        for (String id : pool.subList(0, 3)) {
            GameConst.PotionDef def = GameData.instance().potion(id);
            if (def == null) {
                continue;
            }
            int discount = 30 + rnd.nextInt(21);   // 30–50%
            int price = Math.max(1, (int) Math.ceil(def.price() * (100 - discount) / 100.0));
            PlayerState.MarketOffer o = new PlayerState.MarketOffer();
            o.id = id;
            o.price = price;
            o.discount = discount;
            offers.add(o);
        }
        s.marketDay = today;
        s.marketOffers = offers;
        s.marketBought = new LinkedHashMap<>();
    }

    public static List<Map<String, Object>> viewOf(PlayerState s) {
        ensure(s);
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlayerState.MarketOffer o : s.marketOffers) {
            GameConst.PotionDef def = GameData.instance().potion(o.id);
            if (def == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.id);
            m.put("name", def.name());
            m.put("emoji", def.emoji());
            m.put("desc", def.desc());
            m.put("price", o.price);
            m.put("basePrice", def.price());
            m.put("discount", o.discount);
            m.put("bought", s.marketBought.getOrDefault(o.id, 0));
            m.put("limit", LIMIT_PER_ITEM);
            m.put("stock", s.potions.getOrDefault(o.id, 0));
            out.add(m);
        }
        return out;
    }

    /** 购买一件：校验货单/限购/金币，全过才扣款入栏。返回实付金币。 */
    public static long buy(PlayerState s, String id) {
        ensure(s);
        PlayerState.MarketOffer offer = null;
        for (PlayerState.MarketOffer o : s.marketOffers) {
            if (o.id.equals(id)) {
                offer = o;
                break;
            }
        }
        if (offer == null) {
            throw ApiException.badRequest("今日黑市没有这种药剂");
        }
        int bought = s.marketBought.getOrDefault(id, 0);
        if (bought >= LIMIT_PER_ITEM) {
            throw ApiException.badRequest("今日已达限购（" + LIMIT_PER_ITEM + " 件）");
        }
        if (Num.lt(s.gold, Num.of(offer.price))) {
            throw ApiException.badRequest("金币不足（需要 " + offer.price + "）");
        }
        s.gold = Num.sub(s.gold, Num.of(offer.price));
        s.potions.merge(id, 1, Integer::sum);
        s.marketBought.put(id, bought + 1);
        s.stats.marketBuys++;
        return offer.price;
    }
}
