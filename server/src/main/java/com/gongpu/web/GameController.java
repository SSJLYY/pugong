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

    public record RerollReq(Integer index, Integer lockIndex, Integer lockIndex2) {
    }

    public record AutoCfgReq(Double hpLine, Double mpLine, Boolean autoCure, Double fleeLine,
                             Boolean autoBurn, Boolean autoSkill,
                             Boolean autoRebirth, Integer autoSellKeep,
                             Boolean autoEquipBest, Boolean autoEat, Integer autoSmeltKeep,
                             Boolean autoAdvance, Boolean autoBoss) {
    }

    public record QuestReq(String code) {
    }

    /** count 用字符串收大数：Jackson 对 String 字段兼容 JSON 数字与字符串两种写法（旧前端发数字不破）。 */
    public record StatPointReq(String stat, String count) {
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
    private final com.gongpu.craft.CraftService crafts;

    public GameController(SaveService saves, GameService game,
                          com.gongpu.state.GraveService graves,
                          com.gongpu.stats.LeaderboardService board,
                          com.gongpu.stats.SocialService social,
                          com.gongpu.account.AccountService accounts,
                          com.gongpu.config.StateCache cache,
                          com.gongpu.quest.QuestService quest,
                          com.gongpu.craft.CraftService crafts) {
        this.saves = saves;
        this.game = game;
        this.graves = graves;
        this.board = board;
        this.social = social;
        this.accounts = accounts;
        this.cache = cache;
        this.quest = quest;
        this.crafts = crafts;
    }

    /**
     * 统一包装：加锁 → 推进时间 → 执行动作 → 落库 → 榜单上报。
     * <p>Redis 锁防止前端轮询与玩家操作并发推进同一档（双计出拳）。</p>
     */
    private Map<String, Object> run(long uid, int slot, String username,
                                    java.util.function.Function<PlayerState, Map<String, Object>> action) {
        return run(uid, slot, username, null, action);
    }

    /** 带审计标签的动作入口（第七批）：经济类端点传标签，runLocked 按金币变动记审计行。 */
    private Map<String, Object> run(long uid, int slot, String username, String auditLabel,
                                    java.util.function.Function<PlayerState, Map<String, Object>> action) {
        return cache.withLock(uid, slot, () -> runLocked(uid, slot, username, auditLabel, action));
    }

    private Map<String, Object> runLocked(long uid, int slot, String username,
                                          java.util.function.Function<PlayerState, Map<String, Object>> action) {
        return runLocked(uid, slot, username, null, action);
    }

    // 写端点限流（第八批）：同一账号 10 秒内写动作超过 60 次返回 429。
    // 纯轮询（action==null 的 GET /state）不计；正常游玩（含连点技能/批量按钮）远低于此线，
    // 只挡脚本高频刷写。窗口为进程级滑动窗口，重启清零可接受。
    private static final int WRITE_LIMIT = 60;
    private static final long WRITE_WINDOW_MS = 10_000L;
    private static final java.util.concurrent.ConcurrentHashMap<Long, java.util.ArrayDeque<Long>>
            WRITE_TIMES = new java.util.concurrent.ConcurrentHashMap<>();

    public static void checkWriteRate(long uid, long now) {
        java.util.ArrayDeque<Long> q = WRITE_TIMES.computeIfAbsent(uid, k -> new java.util.ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > WRITE_WINDOW_MS) {
                q.pollFirst();
            }
            if (q.size() >= WRITE_LIMIT) {
                throw com.gongpu.common.ApiException.tooManyRequests("操作太频繁，请稍后再试");
            }
            q.addLast(now);
        }
        // 第二十批·轮7：每用户条目永驻（队列清了 key 不删），历史玩家单调累积——
        // 超阈值惰性清掉窗口已空的条目（同 LeaderboardService.throttle 范式）
        if (WRITE_TIMES.size() > 4096) {
            WRITE_TIMES.entrySet().removeIf(e -> {
                java.util.ArrayDeque<Long> dq = e.getValue();
                synchronized (dq) {
                    return dq.isEmpty() || now - dq.peekLast() > WRITE_WINDOW_MS * 6;
                }
            });
        }
    }

    private Map<String, Object> runLocked(long uid, int slot, String username, String auditLabel,
                                          java.util.function.Function<PlayerState, Map<String, Object>> action) {
        if (action != null) {
            checkWriteRate(uid, System.currentTimeMillis());
        }
        PlayerState s = saves.load(uid, slot);
        // 用户名优先取 JWT claim（拦截器已解析），免去每秒轮询一次账号查库；
        // 无 claim 的老令牌才回源查一次
        String name = username != null ? username : accounts.require(uid).getUsername();
        s.bulletinName = name;
        long frameT0 = System.nanoTime();
        com.gongpu.game.GameEngine.AdvanceResult advanceResult = saves.advanceNow(uid, slot, s);
        TimingInterceptor.recordFrame((System.nanoTime() - frameT0) / 1_000_000);
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
        // 每日系统签发（第七批实测修复）：每日任务/黑市的签发必须在落库前完成——
        // 落库在动作后的 finally 里、fullView 在落库之后才建，签发若只活在视图里，
        // 下一帧读档又是未签发状态（dailyDay 为空）→ 每次重签、基线永远贴着当前计数、进度恒 0
        com.gongpu.game.DailyQuests.ensure(s);
        com.gongpu.game.WeeklyQuests.ensure(s);
        com.gongpu.game.MonthlyQuests.ensure(s);
        com.gongpu.game.BlackMarket.ensure(s);
        // 经济审计基线：advance/autoMaintain 之后、动作之前取金币（挂机收益不算进动作账）
        java.math.BigDecimal goldBefore = auditLabel == null ? null : s.gold;
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
        if (auditLabel != null) {
            EconomyAudit.record(uid, slot, auditLabel, goldBefore, s.gold);
        }
        // 榜单（生命/金币/世代三榜统一上报,带内部节流,静默失败）
        try {
            board.submit(uid, name,
                    s.life.maxHP, s.gen, s.gold, s.stats.totalDamage);
        } catch (RuntimeException ignored) {
            // 榜单不影响游戏
        }
        // 金币里程碑播报（第八批）：动作结算后、视图构建前检查，事件随本帧下发
        com.gongpu.game.GameEngine.checkGoldMilestone(s);
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
    private static void revertDailyReward(PlayerState s, int gen, long streakGold, boolean weekly) {
        s.gold = com.gongpu.common.Num.sub(s.gold,
                java.math.BigDecimal.valueOf(200L + gen * 100L + streakGold));
        mergeDown(s, "hp_s", 3);
        mergeDown(s, "mp", 2);
        if (weekly) {
            mergeDown(s, "hp_l", 2);
        }
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

    /** 大数参数解析（加点 count 专用）：任意精度十进制字符串，格式不对回 400。 */
    private static java.math.BigDecimal reqCount(String v, String field) {
        if (v == null || v.isBlank()) {
            throw com.gongpu.common.ApiException.badRequest("参数缺失: " + field);
        }
        try {
            return new java.math.BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            throw com.gongpu.common.ApiException.badRequest("参数格式不正确: " + field);
        }
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
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "buy", s -> GameService.buyPotion(s, reqStr(body.id(), "id"), count));
    }

    public record BuyFillReq(String id, Integer target) {
    }

    /** 补给：把某药剂买到目标库存（金币不足部分买入）。 */
    @PostMapping("/{slot}/buyfill")
    public Map<String, Object> buyFill(HttpServletRequest req, @PathVariable int slot,
                                       @RequestBody BuyFillReq body) {
        int target = body.target() == null ? 10 : body.target();
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "buyfill", s -> GameService.buyFill(s, reqStr(body.id(), "id"), target));
    }

    /** 一键补给包（第七批）：出门四件套（血药 10/蓝药 5/解毒 3/食物 3）一次补齐。 */
    @PostMapping("/{slot}/buyfill-kit")
    public Map<String, Object> buyFillKit(HttpServletRequest req, @PathVariable int slot) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "buyfill-kit", GameService::buyFillKit);
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
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "sell", s -> game.sell(s, reqIdx(body.index(), "index")));
    }

    @PostMapping("/{slot}/dismantle")
    public Map<String, Object> dismantle(HttpServletRequest req, @PathVariable int slot,
                                         @RequestBody IdxReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> game.dismantle(s, reqIdx(body.index(), "index")));
    }

    @PostMapping("/{slot}/dismantle-all")
    public Map<String, Object> dismantleAll(HttpServletRequest req, @PathVariable int slot,
                                            @RequestBody(required = false) Map<String, Integer> body) {
        int keep = body == null || body.get("keep") == null ? 1 : body.get("keep");
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> game.dismantleAll(s, keep));
    }

    @PostMapping("/{slot}/enhance")
    public Map<String, Object> enhance(HttpServletRequest req, @PathVariable int slot,
                                       @RequestBody IdxReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "enhance", s -> game.enhance(s, reqIdx(body.index(), "index")));
    }

    @PostMapping("/{slot}/temper")
    public Map<String, Object> temper(HttpServletRequest req, @PathVariable int slot,
                                      @RequestBody IdxReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "temper", s -> game.temper(s, reqIdx(body.index(), "index")));
    }

    /** 装备升星（第十批）：星核碎片 + 金币，底子 +3%/星。 */
    @PostMapping("/{slot}/star-up")
    public Map<String, Object> starUp(HttpServletRequest req, @PathVariable int slot,
                                      @RequestBody IdxReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "star-up", s -> game.starUp(s, reqIdx(body.index(), "index")));
    }

    /** 装备觉醒（第十一批）：满星后混沌核心 + 金币，底子 +5%/级。 */
    @PostMapping("/{slot}/awaken")
    public Map<String, Object> awaken(HttpServletRequest req, @PathVariable int slot,
                                      @RequestBody IdxReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "awaken", s -> game.awaken(s, reqIdx(body.index(), "index")));
    }

    public record InheritReq(Integer from, Integer to) {
    }

    /** 装备传承（第十三批）：from/to 均为背包下标，四维成长整表转移。 */
    @PostMapping("/{slot}/inherit")
    public Map<String, Object> inherit(HttpServletRequest req, @PathVariable int slot,
                                       @RequestBody InheritReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "inherit",
                s -> game.inherit(s, reqIdx(body.from(), "from"), reqIdx(body.to(), "to")));
    }

    public record SweepReq(Integer count) {
    }

    /** 区域扫荡（第十三批）：花金币买当前区域击杀口径（精通/猎杀任务），不出掉落。 */
    @PostMapping("/{slot}/sweep")
    public Map<String, Object> sweep(HttpServletRequest req, @PathVariable int slot,
                                     @RequestBody SweepReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "sweep",
                s -> game.sweep(s, reqIdx(body.count(), "count")));
    }

    public record SocketReq(Integer index, String gem, Integer gemIndex) {
    }

    /** 宝石镶嵌（第八批）：index=背包位，gem=宝石 code。 */
    @PostMapping("/{slot}/socket")
    public Map<String, Object> socket(HttpServletRequest req, @PathVariable int slot,
                                      @RequestBody SocketReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "socket",
                s -> game.socketGem(s, reqIdx(body.index(), "index"), reqStr(body.gem(), "gem")));
    }

    /** 宝石拆卸（第八批）：index=背包位，gemIndex=孔位。 */
    @PostMapping("/{slot}/unsocket")
    public Map<String, Object> unsocket(HttpServletRequest req, @PathVariable int slot,
                                        @RequestBody SocketReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "unsocket",
                s -> game.unsocketGem(s, reqIdx(body.index(), "index"), reqIdx(body.gemIndex(), "gemIndex")));
    }

    public record GemReq(String id) {
    }

    /** 宝石融合（第八批）：3 颗同种一阶 → 1 颗璀璨二阶。 */
    @PostMapping("/{slot}/fuse-gem")
    public Map<String, Object> fuseGem(HttpServletRequest req, @PathVariable int slot,
                                       @RequestBody GemReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "fuse-gem",
                s -> game.fuseGem(s, reqStr(body.id(), "id")));
    }

    public record PetReq(String id) {
    }

    /** 领养宠物（第九批）。 */
    @PostMapping("/{slot}/adopt-pet")
    public Map<String, Object> adoptPet(HttpServletRequest req, @PathVariable int slot,
                                        @RequestBody PetReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "adopt-pet",
                s -> game.adoptPet(s, reqStr(body.id(), "id")));
    }

    /** 宠物进化（第十一批）。 */
    @PostMapping("/{slot}/evolve-pet")
    public Map<String, Object> evolvePet(HttpServletRequest req, @PathVariable int slot,
                                         @RequestBody PetReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "evolve-pet",
                s -> game.evolvePet(s, reqStr(body.id(), "id")));
    }

    /** 切换出战宠物（第九批）。 */
    @PostMapping("/{slot}/switch-pet")
    public Map<String, Object> switchPet(HttpServletRequest req, @PathVariable int slot,
                                         @RequestBody PetReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "switch-pet",
                s -> game.switchPet(s, reqStr(body.id(), "id")));
    }

    public record PetRenameReq(String id, String name) {
    }

    /** 宠物改名（第九批）。 */
    @PostMapping("/{slot}/rename-pet")
    public Map<String, Object> renamePet(HttpServletRequest req, @PathVariable int slot,
                                         @RequestBody PetRenameReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "rename-pet",
                s -> game.renamePet(s, reqStr(body.id(), "id"), body.name()));
    }

    /** 喂食出战宠物（第九批）。 */
    @PostMapping("/{slot}/feed-pet")
    public Map<String, Object> feedPet(HttpServletRequest req, @PathVariable int slot) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "feed-pet",
                s -> game.feedPet(s));
    }

    /** 批量融合宝石（第九批）。 */
    @PostMapping("/{slot}/fuse-all-gems")
    public Map<String, Object> fuseAllGems(HttpServletRequest req, @PathVariable int slot) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "fuse-all-gems",
                s -> game.fuseAllGems(s));
    }

    /** 一键拆卸全部宝石（第九批）。 */
    @PostMapping("/{slot}/unsocket-all")
    public Map<String, Object> unsocketAll(HttpServletRequest req, @PathVariable int slot) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "unsocket-all",
                s -> game.unsocketAll(s));
    }

    @PostMapping("/{slot}/reroll")
    public Map<String, Object> reroll(HttpServletRequest req, @PathVariable int slot,
                                      @RequestBody RerollReq body) {
        // lockIndex/lockIndex2 可选(旧客户端/快捷洗炼不锁),缺省 -1 = 不锁定;lockIndex2 为双锁第二条
        Integer lock = body.lockIndex() == null ? -1 : body.lockIndex();
        Integer lock2 = body.lockIndex2() == null ? -1 : body.lockIndex2();
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "reroll", s -> game.reroll(s, reqIdx(body.index(), "index"), lock, lock2));
    }

    @PostMapping("/{slot}/autosell")
    public Map<String, Object> autoSell(HttpServletRequest req, @PathVariable int slot,
                                        @RequestBody(required = false) Map<String, Integer> body) {
        int keep = body == null || body.get("keep") == null ? 1 : body.get("keep");
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "autosell", s -> game.autoSell(s, keep));
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
                        body.autoBurn(), body.autoSkill(), body.autoRebirth(), body.autoSellKeep(), body.autoEquipBest(), body.autoEat(), body.autoSmeltKeep(), body.autoAdvance(), body.autoBoss()));
    }

    /** 无限属性点天赋：自由属性点加点（statlord 专属）。 */
    @PostMapping("/{slot}/statpoint")
    public Map<String, Object> statPoint(HttpServletRequest req, @PathVariable int slot,
                                         @RequestBody StatPointReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req),
                s -> game.allocateStatPoint(s, reqStr(body.stat(), "stat"), reqCount(body.count(), "count")));
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
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "quest-deliver", s -> quest.deliver(s, reqStr(body.code(), "code")));
    }

    @PostMapping("/{slot}/quest/abandon")
    public Map<String, Object> questAbandon(HttpServletRequest req, @PathVariable int slot,
                                            @RequestBody QuestReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), s -> quest.abandon(s, reqStr(body.code(), "code")));
    }

    /** 图纸合成：合成板（配方 + 持有量 + 可合成标记）。 */
    @GetMapping("/{slot}/crafts")
    public Map<String, Object> craftBoard(HttpServletRequest req, @PathVariable int slot) {
        PlayerState s = saves.load(AuthInterceptor.userId(req), slot);
        return crafts.board(s);
    }

    @PostMapping("/{slot}/craft")
    public Map<String, Object> craftDo(HttpServletRequest req, @PathVariable int slot,
                                       @RequestBody QuestReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "craft",
                s -> crafts.craft(s, reqStr(body.code(), "code")));
    }

    public record MatExchangeReq(String from, String to) {
    }

    /** 材料兑换：3 份区域材料换 1 份其他区域材料。 */
    @PostMapping("/{slot}/mat-exchange")
    public Map<String, Object> matExchange(HttpServletRequest req, @PathVariable int slot,
                                           @RequestBody MatExchangeReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "mat-exchange",
                s -> crafts.exchange(s, reqStr(body.from(), "from"), reqStr(body.to(), "to")));
    }

    public record SellMatReq(String id, Integer count) {
    }

    /** 材料出售：区域材料按单价卖金币（count 缺省/小于等于 0 = 全部卖出）。 */
    @PostMapping("/{slot}/sell-material")
    public Map<String, Object> sellMaterial(HttpServletRequest req, @PathVariable int slot,
                                            @RequestBody SellMatReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "sell-material",
                s -> crafts.sell(s, reqStr(body.id(), "id"), body.count() == null ? 0 : body.count()));
    }

    /** 一键出售全部区域材料（第九批）。 */
    @PostMapping("/{slot}/sell-all-materials")
    public Map<String, Object> sellAllMaterials(HttpServletRequest req, @PathVariable int slot) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "sell-all-materials",
                s -> crafts.sellAllMaterials(s));
    }

    /** 连续喂食宠物 ×10（第九批）。 */
    @PostMapping("/{slot}/feed-pet-batch")
    public Map<String, Object> feedPetBatch(HttpServletRequest req, @PathVariable int slot) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "feed-pet-batch",
                s -> game.feedPetBatch(s, 10));
    }

    /** 喂食宠物到满级或金币耗尽（第十批）。 */
    @PostMapping("/{slot}/feed-pet-max")
    public Map<String, Object> feedPetMax(HttpServletRequest req, @PathVariable int slot) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "feed-pet-max",
                s -> game.feedPetMax(s));
    }

    /** 一键出售多余图纸（第九批，每种保留 1 张）。 */
    @PostMapping("/{slot}/sell-extra-blueprints")
    public Map<String, Object> sellExtraBlueprints(HttpServletRequest req, @PathVariable int slot) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "sell-extra-blueprints",
                s -> crafts.sellExtraBlueprints(s));
    }

    /** 一键兑换材料（第九批，3:1 尽可能多换）。 */
    @PostMapping("/{slot}/mat-exchange-all")
    public Map<String, Object> matExchangeAll(HttpServletRequest req, @PathVariable int slot,
                                              @RequestBody MatExchangeReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "mat-exchange-all",
                s -> crafts.exchangeAll(s, reqStr(body.from(), "from"), reqStr(body.to(), "to")));
    }

    /** 宝石直购（第八批）：一阶宝石按售价 ×4 买入；二阶只能融合。 */
    @PostMapping("/{slot}/buy-gem")
    public Map<String, Object> buyGem(HttpServletRequest req, @PathVariable int slot,
                                      @RequestBody GemReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "buy-gem",
                s -> crafts.buyGem(s, reqStr(body.id(), "id")));
    }

    /** 材料买入（第八批）：区域材料按售价 ×3 买入（急用调剂）。 */
    @PostMapping("/{slot}/buy-material")
    public Map<String, Object> buyMaterial(HttpServletRequest req, @PathVariable int slot,
                                           @RequestBody SellMatReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "buy-material",
                s -> crafts.buy(s, reqStr(body.id(), "id"), body.count() == null ? 1 : body.count()));
    }

    /** 一键穿套装（第八批）：按套装 id 把背包里的成员件逐件换上。 */
    @PostMapping("/{slot}/equip-set")
    public Map<String, Object> equipSet(HttpServletRequest req, @PathVariable int slot,
                                        @RequestBody AchReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "equip-set",
                s -> game.equipSet(s, reqStr(body.id(), "id")));
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
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "enchant",
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
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "learn", s -> game.learnPassive(s, reqStr(body.id(), "id")));
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

    public record AchReq(String id) {
    }

    /** 每日任务奖励领取（第七批）。 */
    @PostMapping("/{slot}/daily-claim")
    public Map<String, Object> dailyClaim(HttpServletRequest req, @PathVariable int slot,
                                          @RequestBody AchReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "daily-claim",
                s -> game.claimDaily(s, reqStr(body.id(), "id")));
    }

    /** 周常任务奖励领取（第八批）。 */
    @PostMapping("/{slot}/weekly-claim")
    public Map<String, Object> weeklyClaim(HttpServletRequest req, @PathVariable int slot,
                                           @RequestBody AchReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "weekly-claim",
                s -> game.claimWeekly(s, reqStr(body.id(), "id")));
    }

    /** 月常任务领取（第九批）：全清额外 +300,000 金与随机宝石 ×3。 */
    @PostMapping("/{slot}/monthly-claim")
    public Map<String, Object> monthlyClaim(HttpServletRequest req, @PathVariable int slot,
                                            @RequestBody AchReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "monthly-claim",
                s -> game.claimMonthly(s, reqStr(body.id(), "id")));
    }

    /** 每日黑市购买（第七批）：当日货单内打折药剂，限购校验在 BlackMarket。 */
    @PostMapping("/{slot}/market-buy")
    public Map<String, Object> marketBuy(HttpServletRequest req, @PathVariable int slot,
                                         @RequestBody AchReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "market-buy",
                s -> game.buyBlackMarket(s, reqStr(body.id(), "id")));
    }

    /** 成就奖励领取（已解锁未领取，金币入账）。 */
    @PostMapping("/{slot}/ach-claim")
    public Map<String, Object> achClaim(HttpServletRequest req, @PathVariable int slot,
                                        @RequestBody AchReq body) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "ach-claim",
                s -> game.claimAchievement(s, reqStr(body.id(), "id")));
    }

    /** 成就奖励一键全领。 */
    @PostMapping("/{slot}/ach-claim-all")
    public Map<String, Object> achClaimAll(HttpServletRequest req, @PathVariable int slot) {
        return run(AuthInterceptor.userId(req), slot, AuthInterceptor.username(req), "ach-claim-all",
                s -> game.claimAllAchievements(s));
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
            // 连签预测与原子领取的 CASE 同口径:并发下只有胜者的 UPDATE 影响行数,
            // 败者走 revert 全额扣回(含连签加成),DB 里的 streak 也只被胜者推进。
            var acct = accounts.require(uid);
            int streak = com.gongpu.account.AccountService.nextStreak(
                    acct.getLastClaimDay(), acct.getClaimStreak() == null ? 0 : acct.getClaimStreak(), today);
            long streakGold = com.gongpu.account.AccountService.streakBonusGold(streak);
            boolean weekly = com.gongpu.account.AccountService.streakWeeklyBonus(streak);
            java.math.BigDecimal gold = java.math.BigDecimal.valueOf(200L + s.gen * 100L + streakGold);
            s.gold = com.gongpu.common.Num.add(s.gold, gold);
            s.potions.merge("hp_s", 3, Integer::sum);
            s.potions.merge("mp", 2, Integer::sum);
            if (weekly) {
                s.potions.merge("hp_l", 2, Integer::sum);
            }
            if (s.gen >= 1) {
                s.potions.merge("growth", 1, Integer::sum);
            }
            if (s.gen >= 3) {
                s.potions.merge("ironpot", 1, Integer::sum);
            }
            saves.persist(uid, slot, s, null);
            if (!accounts.claimDailyAtomic(uid, today)) {
                revertDailyReward(s, s.gen, streakGold, weekly);
                saves.persist(uid, slot, s, null);
                throw com.gongpu.common.ApiException.badRequest("今天已经签到过了，明天再来");
            }

            Map<String, Object> reward = new LinkedHashMap<>();
            reward.put("gold", com.gongpu.common.Num.plain(gold));
            reward.put("hp_s", 3);
            reward.put("mp", 2);
            reward.put("streak", streak);
            reward.put("streakGold", streakGold);
            if (weekly) reward.put("hp_l", 2);
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
            board.submit(uid, username, s.life.maxHP, s.gen, s.gold, s.stats.totalDamage);
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
