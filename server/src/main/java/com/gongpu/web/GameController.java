package com.gongpu.web;

import com.gongpu.game.GameService;
import com.gongpu.security.AuthInterceptor;
import com.gongpu.state.PlayerState;
import com.gongpu.state.SaveService;
import com.gongpu.state.StateCodec;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏动作接口。
 * <p>客户端只发"意图"（换区、施法、买药…），全部数值由服务端结算后回推。</p>
 */
@RestController
@RequestMapping("/api/game")
public class GameController {

    public record PotionReq(String id, Integer count) {
    }

    public record IdxReq(Integer index) {
    }

    public record RerollReq(Integer index, Integer lockIndex) {
    }

    public record AutoCfgReq(Double hpLine, Double mpLine, Boolean autoCure, Double fleeLine,
                             Boolean autoBurn, Boolean autoSkill,
                             Boolean autoRebirth, Integer autoSellKeep,
                             Boolean autoEquipBest, Boolean autoEat) {
    }

    public record QuestReq(String code) {
    }

    public record StatPointReq(String stat, Integer count) {
    }

    public record EnchantReq(String scroll, Integer index) {
    }

    public record SlotReq(String slot) {
    }

    public record SkillReq(String id) {
    }

    public record RebirthReq(String key) {
    }

    public record ZoneReq(String zone) {
    }

    private final SaveService saves;
    private final GameService game;
    private final com.gongpu.state.GraveService graves;
    private final com.gongpu.stats.LeaderboardService board;
    private final com.gongpu.stats.SocialService social;
    private final com.gongpu.account.AccountService accounts;
    private final com.gongpu.config.StateCache cache;
    private final com.gongpu.quest.QuestService quest;

    public GameController(SaveService saves, GameService game,
                          com.gongpu.state.GraveService graves,
                          com.gongpu.stats.LeaderboardService board,
                          com.gongpu.stats.SocialService social,
                          com.gongpu.account.AccountService accounts,
                          com.gongpu.config.StateCache cache,
                          com.gongpu.quest.QuestService quest) {
        this.saves = saves;
        this.game = game;
        this.graves = graves;
        this.board = board;
        this.social = social;
        this.accounts = accounts;
        this.cache = cache;
        this.quest = quest;
    }

    /**
     * 统一包装：加锁 → 推进时间 → 执行动作 → 落库 → 榜单上报。
     * <p>Redis 锁防止前端轮询与玩家操作并发推进同一档（双计出拳）。</p>
     */
    private Map<String, Object> run(long uid, int slot, String username,
                                    java.util.function.Function<PlayerState, Map<String, Object>> action) {
        return cache.withLock(uid, slot, () -> runLocked(uid, slot, username, action));
    }

    private Map<String, Object> runLocked(long uid, int slot, String username,
                                          java.util.function.Function<PlayerState, Map<String, Object>> action) {
        PlayerState s = saves.load(uid, slot);
        // 用户名优先取 JWT claim（拦截器已解析），免去每秒轮询一次账号查库；
        // 无 claim 的老令牌才回源查一次
        String name = username != null ? username : accounts.require(uid).getUsername();
        s.bulletinName = name;
        com.gongpu.game.GameEngine.AdvanceResult advanceResult = saves.advanceNow(uid, slot, s);
        // 自动飞升(legacy autobot autoRebirth):通关线达成且管家开关开启 → 立即转生
        // (必须在 persist 之前改状态,否则转生不落库)
        if (advanceResult.cleared() && s.autoRebirth && !s.dead) {
            try {
                // 先选目标再校验:validateRebirth(s, null) 两个分支恒抛,
                // 旧顺序(先 validate 后选 chosen)让自动飞升从未生效过
                String chosen = com.gongpu.game.GameService.autoRebirthChoice(s);
                game.validateRebirth(s, chosen);
                try {
                    graves.addAscend(uid, s, chosen);                              // 立金碑(与手动转生一致)
                } catch (RuntimeException ignored) {
                }
                game.rebirth(s, chosen);
            } catch (RuntimeException ignored) {
                // 转生前置不满足(数据异常)则跳过,下帧再试
            }
        }
        // 挂机自动管护(v3.13):结算掉落立即「卖白装→吞噬→穿最强」,背包不堆积——
        // 不开关也零开销;开了之后每秒轮询顺带完成,无需手动点按钮
        if (s.autoEquipBest) {
            com.gongpu.game.GameService.autoMaintain(s);
        }
        Map<String, Object> extra;
        try {
            extra = action == null ? null : action.apply(s);
        } finally {
            // 动作抛异常也要落库（例如燃烧生命可能直接致死）
            // 纯轮询帧（无动作）走节流落库，避免每秒一次 MySQL 写入
            if (action == null) {
                saves.persistThrottled(uid, slot, s);
            } else {
                saves.persist(uid, slot, s, null);
            }
        }
        // 榜单（生命/金币/世代三榜统一上报,带内部节流,静默失败）
        try {
            board.submit(uid, name,
                    s.life.maxHP, s.gen, s.gold);
        } catch (RuntimeException ignored) {
            // 榜单不影响游戏
        }
        Map<String, Object> out = game.fullView(s);
        if (extra != null) {
            out.put("result", extra);
        }
        // Boss 首杀播报
        if (advanceResult != null && advanceResult.bossFirstKill() != null) {
            Map<String, Object> bk = new LinkedHashMap<>();
            bk.put("boss", advanceResult.bossFirstKill());
            bk.put("gold", advanceResult.bossFirstKillGold());
            out.put("bossFirstKill", bk);
        }
        // 离线收益摘要：挂机回来一次性提示（seconds>60 才算离线）
        if (advanceResult != null && advanceResult.seconds() > com.gongpu.game.GameConst.OFFLINE_MIN_SEC) {
            Map<String, Object> off = new LinkedHashMap<>();
            off.put("seconds", (long) advanceResult.seconds());
            off.put("punches", advanceResult.punches());
            off.put("kills", advanceResult.kills());
            off.put("drops", advanceResult.drops());
            off.put("died", advanceResult.died());
            out.put("offline", off);
        }
        return out;
    }

    @GetMapping("/{slot}/state")
    public Map<String, Object> state(HttpServletRequest req, @PathVariable int slot) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), null);
    }

    // ==================== 请求体兜底校验 ====================

    /** 签到领取失败(并发另一槽抢先)时,按 gen 镜像扣回已入账的奖励。 */
    private static void revertDailyReward(PlayerState s, int gen) {
        s.gold = com.gongpu.common.Num.sub(s.gold,
                java.math.BigDecimal.valueOf(200L + gen * 100L));
        mergeDown(s, "hp_s", 3);
        mergeDown(s, "mp", 2);
        if (gen >= 1) {
            mergeDown(s, "growth", 1);
        }
        if (gen >= 3) {
            mergeDown(s, "ironpot", 1);
        }
    }

    private static void mergeDown(PlayerState s, String potion, int count) {
        s.potions.merge(potion, -count, Integer::sum);
        if (s.potions.get(potion) != null && s.potions.get(potion) <= 0) {
            s.potions.remove(potion);
        }
    }
    // GameConst 的查找表多为 Map.of 不可变 Map,get(null) 会抛 NPE 变 500;此处统一拦成 400。

    private static String reqStr(String v, String field) {
        if (v == null || v.isBlank()) {
            throw com.gongpu.common.ApiException.badRequest("参数缺失: " + field);
        }
        return v;
    }

    private static int reqIdx(Integer v, String field) {
        if (v == null) {
            throw com.gongpu.common.ApiException.badRequest("参数缺失: " + field);
        }
        return v;
    }

    @PostMapping("/{slot}/zone")
    public Map<String, Object> zone(HttpServletRequest req, @PathVariable int slot,
                                    @RequestBody ZoneReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> game.gotoZone(s, reqStr(body.zone(), "zone")));
    }

    @PostMapping("/{slot}/skill")
    public Map<String, Object> skill(HttpServletRequest req, @PathVariable int slot,
                                     @RequestBody SkillReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> GameService.castSkill(s, reqStr(body.id(), "id")));
    }

    @PostMapping("/{slot}/potion")
    public Map<String, Object> potion(HttpServletRequest req, @PathVariable int slot,
                                      @RequestBody PotionReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> game.usePotion(s, reqStr(body.id(), "id")));
    }

    @PostMapping("/{slot}/buy")
    public Map<String, Object> buy(HttpServletRequest req, @PathVariable int slot,
                                   @RequestBody PotionReq body) {
        int count = body.count() == null ? 1 : body.count();
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> GameService.buyPotion(s, reqStr(body.id(), "id"), count));
    }

    @PostMapping("/{slot}/equip")
    public Map<String, Object> equip(HttpServletRequest req, @PathVariable int slot,
                                     @RequestBody IdxReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> game.equip(s, reqIdx(body.index(), "index")));
    }

    /** 加穿到额外装备栏（无限装备栏天赋）：进第一个空的 extra_N 栏,不占原生相位。 */
    @PostMapping("/{slot}/equip-extra")
    public Map<String, Object> equipExtra(HttpServletRequest req, @PathVariable int slot,
                                          @RequestBody IdxReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> game.equipExtra(s, reqIdx(body.index(), "index")));
    }

    @PostMapping("/{slot}/unequip")
    public Map<String, Object> unequip(HttpServletRequest req, @PathVariable int slot,
                                       @RequestBody SlotReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> game.unequip(s, reqStr(body.slot(), "slot")));
    }

    @PostMapping("/{slot}/sell")
    public Map<String, Object> sell(HttpServletRequest req, @PathVariable int slot,
                                    @RequestBody IdxReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> game.sell(s, reqIdx(body.index(), "index")));
    }

    @PostMapping("/{slot}/enhance")
    public Map<String, Object> enhance(HttpServletRequest req, @PathVariable int slot,
                                       @RequestBody IdxReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> game.enhance(s, reqIdx(body.index(), "index")));
    }

    @PostMapping("/{slot}/reroll")
    public Map<String, Object> reroll(HttpServletRequest req, @PathVariable int slot,
                                      @RequestBody RerollReq body) {
        // lockIndex 可选(旧客户端/快捷洗炼不锁),缺省 -1 = 不锁定
        Integer lock = body.lockIndex() == null ? -1 : body.lockIndex();
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> game.reroll(s, reqIdx(body.index(), "index"), lock));
    }

    @PostMapping("/{slot}/autosell")
    public Map<String, Object> autoSell(HttpServletRequest req, @PathVariable int slot,
                                        @RequestBody(required = false) Map<String, Integer> body) {
        int keep = body == null || body.get("keep") == null ? 1 : body.get("keep");
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> game.autoSell(s, keep));
    }

    @PostMapping("/{slot}/discard")
    public Map<String, Object> discard(HttpServletRequest req, @PathVariable int slot,
                                       @RequestBody IdxReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> game.discard(s, reqIdx(body.index(), "index")));
    }

    @PostMapping("/{slot}/sortbag")
    public Map<String, Object> sortBag(HttpServletRequest req, @PathVariable int slot) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> game.sortBag(s));
    }

    @PostMapping("/{slot}/lock")
    public Map<String, Object> toggleLock(HttpServletRequest req, @PathVariable int slot,
                                          @RequestBody IdxReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> game.toggleLock(s, reqIdx(body.index(), "index")));
    }

    /** 挂机策略：自动嗑药血线/蓝线。 */
    @PostMapping("/{slot}/autoconfig")
    public Map<String, Object> autoConfig(HttpServletRequest req, @PathVariable int slot,
                                          @RequestBody AutoCfgReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> game.setAutoLines(s, body.hpLine(), body.mpLine(), body.autoCure(), body.fleeLine(),
                        body.autoBurn(), body.autoSkill(), body.autoRebirth(), body.autoSellKeep(), body.autoEquipBest(), body.autoEat()));
    }

    /** 无限属性点天赋：自由属性点加点（statlord 专属）。 */
    @PostMapping("/{slot}/statpoint")
    public Map<String, Object> statPoint(HttpServletRequest req, @PathVariable int slot,
                                         @RequestBody StatPointReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req),
                s -> game.allocateStatPoint(s, reqStr(body.stat(), "stat"), reqIdx(body.count(), "count")));
    }

    /** 委托：查看公会委托板（含资格与进度）。 */
    @GetMapping("/{slot}/quests")
    public Map<String, Object> quests(HttpServletRequest req, @PathVariable int slot) {
        PlayerState s = saves.load(AuthInterceptor.userId(req), slot);
        return quest.questBoard(s);
    }

    @PostMapping("/{slot}/quest/accept")
    public Map<String, Object> questAccept(HttpServletRequest req, @PathVariable int slot,
                                           @RequestBody QuestReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> quest.accept(s, reqStr(body.code(), "code")));
    }

    @PostMapping("/{slot}/quest/deliver")
    public Map<String, Object> questDeliver(HttpServletRequest req, @PathVariable int slot,
                                            @RequestBody QuestReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> quest.deliver(s, reqStr(body.code(), "code")));
    }

    @PostMapping("/{slot}/quest/abandon")
    public Map<String, Object> questAbandon(HttpServletRequest req, @PathVariable int slot,
                                            @RequestBody QuestReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> quest.abandon(s, reqStr(body.code(), "code")));
    }

    /** 一键穿最强装备（按评分自动换装）。 */
    @PostMapping("/{slot}/autoequip")
    public Map<String, Object> autoEquip(HttpServletRequest req, @PathVariable int slot) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> game.autoEquip(s));
    }

    /** 附魔：消耗卷轴给武器/弓附加魔伤词条。 */
    @PostMapping("/{slot}/enchant")
    public Map<String, Object> enchant(HttpServletRequest req, @PathVariable int slot,
                                       @RequestBody EnchantReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req),
                s -> game.enchant(s, reqStr(body.scroll(), "scroll"), reqIdx(body.index(), "index")));
    }

    @PostMapping("/{slot}/flee")
    public Map<String, Object> flee(HttpServletRequest req, @PathVariable int slot) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> game.flee(s));
    }

    @PostMapping("/{slot}/boss")
    public Map<String, Object> boss(HttpServletRequest req, @PathVariable int slot) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> game.startBoss(s));
    }

    @PostMapping("/{slot}/learn")
    public Map<String, Object> learn(HttpServletRequest req, @PathVariable int slot,
                                     @RequestBody SkillReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> game.learnPassive(s, reqStr(body.id(), "id")));
    }

    // ==================== 觉醒 / 转生 ====================

    @PostMapping("/{slot}/talent")
    public Map<String, Object> talent(HttpServletRequest req, @PathVariable int slot,
                                      @RequestBody(required = false) Map<String, String> body) {
        long uid = AuthInterceptor.userId(req);
        String chosen = body == null ? null : body.get("key");
        return run(uid, slot, null, s -> game.drawTalent(s, chosen));
    }

    @GetMapping("/{slot}/talent")
    public Map<String, Object> talentView(HttpServletRequest req, @PathVariable int slot) {
        PlayerState s = saves.load(AuthInterceptor.userId(req), slot);
        return game.talentView(s);
    }

    /** 每日签到：每个账号每天一次，奖励金币+药剂（随世代加码）。 */
    @PostMapping("/{slot}/daily")
    public Map<String, Object> daily(HttpServletRequest req, @PathVariable int slot) {
        long uid = AuthInterceptor.userId(req);
        if (!cache.lockRetry(uid, slot)) {
            throw com.gongpu.common.ApiException.conflict("操作太快了，稍等一下");
        }
        try {
            PlayerState s = saves.load(uid, slot);
            saves.advanceNow(uid, slot, s);
            if (s.dead) {
                throw com.gongpu.common.ApiException.badRequest("已经阵亡，无法签到（先复活或重开）");
            }
            java.time.LocalDate today = java.time.LocalDate.now(com.gongpu.game.GameConst.DAILY_ZONE);
            // 友情预检:今天已签直接 400(正常重复点击路径,零副作用)
            if (today.equals(accounts.require(uid).getLastClaimDay())) {
                throw com.gongpu.common.ApiException.badRequest("今天已经签到过了，明天再来");
            }
            // 顺序:奖励入账 → 落库 → 原子领取标记。
            // 旧顺序(先领取标记后入账落库)在 persist 抛异常时标记已置、奖励蒸发;
            // 本顺序 persist 失败时标记未置(下次可重领),领取失败(并发另一槽抢先)则扣回入账。
            java.math.BigDecimal gold = java.math.BigDecimal.valueOf(200L + s.gen * 100L);
            s.gold = com.gongpu.common.Num.add(s.gold, gold);
            s.potions.merge("hp_s", 3, Integer::sum);
            s.potions.merge("mp", 2, Integer::sum);
            if (s.gen >= 1) {
                s.potions.merge("growth", 1, Integer::sum);
            }
            if (s.gen >= 3) {
                s.potions.merge("ironpot", 1, Integer::sum);
            }
            saves.persist(uid, slot, s, null);
            if (!accounts.claimDailyAtomic(uid, today)) {
                revertDailyReward(s, s.gen);
                saves.persist(uid, slot, s, null);
                throw com.gongpu.common.ApiException.badRequest("今天已经签到过了，明天再来");
            }

            Map<String, Object> reward = new LinkedHashMap<>();
            reward.put("gold", com.gongpu.common.Num.plain(gold));
            reward.put("hp_s", 3);
            reward.put("mp", 2);
            if (s.gen >= 1) reward.put("growth", 1);
            if (s.gen >= 3) reward.put("ironpot", 1);
            Map<String, Object> out = game.fullView(s);
            out.put("result", Map.of("ok", true, "reward", reward));
            return out;
        } finally {
            cache.unlock(uid, slot);
        }
    }

    @PostMapping("/{slot}/rebirth")
    public Map<String, Object> rebirth(HttpServletRequest req, @PathVariable int slot,
                                       @RequestBody RebirthReq body) {
        long uid = AuthInterceptor.userId(req);
        if (!cache.lockRetry(uid, slot)) {
            throw com.gongpu.common.ApiException.conflict("操作太快了，稍等一下");
        }
        try {
            PlayerState s = saves.load(uid, slot);
            s.bulletinName = accounts.require(uid).getUsername();
            saves.advanceNow(uid, slot, s);
            // 先无副作用校验(死亡/攻速/赋能选择):校验失败直接 400,
            // 否则金碑已立、转生失败,反复调用可刷一屏金碑
            game.validateRebirth(s, body.key());
            // 飞升前先在旧世留金碑（用飞升时的最终面板）
            try {
                graves.addAscend(uid, s, body.key());
            } catch (RuntimeException ignored) {
                // 立碑失败不阻断转生
            }
            String username = accounts.require(uid).getUsername();
            // 转生后回首世，但榜单记的是历史最好成绩
            board.submit(uid, username, s.life.maxHP, s.gen, s.gold);
            Map<String, Object> extra = game.rebirth(s, body.key());
            saves.persist(uid, slot, s, null);
            Map<String, Object> out = game.fullView(s);
            out.put("result", extra);
            return out;
        } finally {
            cache.unlock(uid, slot);
        }
    }

    @GetMapping("/{slot}/bless-options")
    public Map<String, Object> blessOptions(HttpServletRequest req, @PathVariable int slot) {
        PlayerState s = saves.load(AuthInterceptor.userId(req), slot);
        return Map.of("options", game.blessOptions(s), "gen", s.gen);
    }

    /**
     * 死亡后处理：revive=回档到该槽上次存档点；restart=就地重开（保留万世传承）。
     */
    @PostMapping("/{slot}/death")
    public Map<String, Object> death(HttpServletRequest req, @PathVariable int slot,
                                     @RequestBody Map<String, String> body) {
        long uid = AuthInterceptor.userId(req);
        if (!cache.lockRetry(uid, slot)) {
            throw com.gongpu.common.ApiException.conflict("操作太快了，稍等一下");
        }
        try {
            PlayerState s = saves.load(uid, slot);
            String action = body.getOrDefault("action", "restart");
            if (!s.dead) {
                throw com.gongpu.common.ApiException.badRequest("当前并未死亡");
            }
            if ("revive".equals(action)) {
                if (!saves.hasSnapshot(uid, slot)) {
                    throw com.gongpu.common.ApiException.badRequest("从未存档，无法回档——只能重新开档");
                }
                try {
                    graves.addDeath(uid, s, body.get("epitaph"));
                } catch (RuntimeException ignored) {
                    // 立碑失败不应阻断回档
                }
                PlayerState restored = saves.rollback(uid, slot);
                return game.fullView(restored);
            }
            try {
                graves.addDeath(uid, s, body.get("epitaph"));
            } catch (RuntimeException ignored) {
                // 立碑失败不应阻断重开
            }
            return game.fullView(saves.restart(s, uid, slot));
        } finally {
            cache.unlock(uid, slot);
        }
    }

    @GetMapping("/codes")
    public Map<String, Object> codes() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("zones", com.gongpu.game.GameConst.ZONES.keySet());
        m.put("skills", com.gongpu.game.GameConst.ACTIVE_SKILLS.keySet());
        m.put("passives", com.gongpu.game.GameConst.PASSIVE_SKILLS.keySet());
        m.put("potions", com.gongpu.game.GameConst.POTIONS.keySet());
        return m;
    }
}
