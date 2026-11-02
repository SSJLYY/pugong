package com.gongpu.admin;

import com.gongpu.cfg.AffixTemplate;
import com.gongpu.cfg.AffixTemplateRepository;
import com.gongpu.cfg.BuffTemplate;
import com.gongpu.cfg.BuffTemplateRepository;
import com.gongpu.cfg.DropRateRule;
import com.gongpu.cfg.DropRateRuleRepository;
import com.gongpu.cfg.EquipmentTemplate;
import com.gongpu.cfg.EquipmentTemplateRepository;
import com.gongpu.cfg.GameData;
import com.gongpu.cfg.ItemTemplate;
import com.gongpu.cfg.ItemTemplateRepository;
import com.gongpu.cfg.SkillTemplate;
import com.gongpu.cfg.SkillTemplateRepository;
import com.gongpu.common.ApiException;
import com.gongpu.config.StateCache;
import com.gongpu.state.GameSaveRepository;
import com.gongpu.state.GraveRepository;
import com.gongpu.account.User;
import com.gongpu.account.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 运维端接口：账号管理 + 配置主表 CRUD + 审计。
 * <p>除 /login 外全部由 AdminInterceptor 保护（放行名单在 WebConfig）。
 * 每次配置写操作后调用 GameData.reload() 热刷新。</p>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record LoginReq(String password) {
    }

    public record ResetPwReq(String password) {
    }

    private final AdminGuard guard;
    private final AdminAuditLogRepository audit;
    private final UserRepository users;
    private final GameSaveRepository saves;
    private final GraveRepository graves;
    private final StateCache cache;
    private final GameData gameData;
    private final EquipmentTemplateRepository equipmentRepo;
    private final AffixTemplateRepository affixRepo;
    private final ItemTemplateRepository itemRepo;
    private final SkillTemplateRepository skillRepo;
    private final BuffTemplateRepository buffRepo;
    private final DropRateRuleRepository dropRepo;
    private final com.gongpu.stats.StatDailyAggregator statAggregator;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private static final Pattern PW = Pattern.compile("^\\S{6,64}$");

    public AdminController(AdminGuard guard, AdminAuditLogRepository audit,
                           UserRepository users, GameSaveRepository saves,
                           GraveRepository graves, StateCache cache, GameData gameData,
                           EquipmentTemplateRepository equipmentRepo,
                           AffixTemplateRepository affixRepo,
                           ItemTemplateRepository itemRepo,
                           SkillTemplateRepository skillRepo,
                           BuffTemplateRepository buffRepo,
                           DropRateRuleRepository dropRepo,
                           com.gongpu.stats.StatDailyAggregator statAggregator) {
        this.guard = guard;
        this.statAggregator = statAggregator;
        this.audit = audit;
        this.users = users;
        this.saves = saves;
        this.graves = graves;
        this.cache = cache;
        this.gameData = gameData;
        this.equipmentRepo = equipmentRepo;
        this.affixRepo = affixRepo;
        this.itemRepo = itemRepo;
        this.skillRepo = skillRepo;
        this.buffRepo = buffRepo;
        this.dropRepo = dropRepo;
    }

    private void log(String action, String detail) {
        try {
            AdminAuditLog a = new AdminAuditLog();
            a.setAction(action);
            a.setDetail(detail == null || detail.length() <= 480 ? detail : detail.substring(0, 480));
            a.setCreatedAt(Instant.now());
            audit.save(a);
        } catch (RuntimeException ignored) {
            // 审计失败不阻断主流程
        }
    }

    // ==================== 登录 ====================

    @PostMapping("/login")
    public Map<String, Object> login(jakarta.servlet.http.HttpServletRequest httpReq,
                                     @RequestBody LoginReq req) {
        // 取真实客户端 IP:优先 X-Real-IP(nginx 覆写),XFF 兜底
        String xri = httpReq.getHeader("X-Real-IP");
        String xff = httpReq.getHeader("X-Forwarded-For");
        String ip = xri != null && !xri.isBlank() ? xri.trim()
                : xff != null && !xff.isBlank() ? xff.split(",")[0].trim()
                : httpReq.getRemoteAddr();
        String token = guard.login(req == null ? null : req.password(), ip);
        log("ADMIN_LOGIN", "ip=" + ip);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("token", token);
        m.put("expiresIn", guard.ttlSeconds());
        m.put("role", "admin");
        return m;
    }

    // ==================== 概览 ====================

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("users", users.count());
        m.put("saves", saves.count());
        m.put("graves", graves.count());
        // 今日运营行(当日增量:出拳/击杀/死亡/转生/活跃存档),空行=今天还没有结算
        com.gongpu.stats.StatDaily d = statAggregator.today();
        if (d != null) {
            m.put("today", Map.of(
                    "punches", d.getPunches(),
                    "kills", d.getKills(),
                    "deaths", d.getDeaths(),
                    "rebirths", d.getRebirths(),
                    "activeSaves", d.getActiveSaves()));
        }
        return m;
    }

    // ==================== 账号 ====================

    @GetMapping("/users")
    public Map<String, Object> users(@RequestParam(required = false) String kw) {
        // 关键字下推 DB(contains → LIKE),账号量增长后不再全表载内存过滤
        String key = kw == null ? "" : kw.trim();
        List<User> all = key.isEmpty()
                ? users.findTop500ByOrderByIdAsc()
                : users.findTop500ByUsernameContainingOrderByIdAsc(key);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User u : all) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("createdAt", u.getCreatedAt());
            m.put("lastLoginAt", u.getLastLoginAt());
            m.put("incense", u.getIncense() == null ? 0 : u.getIncense());
            m.put("pvpWins", u.getPvpWins() == null ? 0 : u.getPvpWins());
            m.put("pvpLosses", u.getPvpLosses() == null ? 0 : u.getPvpLosses());
            rows.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        return out;
    }

    @PostMapping("/users/{id}/reset-pw")
    @Transactional
    public Map<String, Object> resetPw(@PathVariable long id, @RequestBody ResetPwReq req) {
        String pw = req == null ? null : req.password();
        if (pw == null || !PW.matcher(pw).matches()) {
            throw ApiException.badRequest("密码需 6~64 位非空白字符");
        }
        User u = users.findById(id)
                .orElseThrow(() -> ApiException.notFound("账号不存在"));
        u.setPasswordHash(encoder.encode(pw));
        users.save(u);
        log("USER_RESET_PW", "id=" + id + " username=" + u.getUsername());
        return Map.of("ok", true);
    }

    @DeleteMapping("/users/{id}")
    @Transactional
    public Map<String, Object> deleteUser(@PathVariable long id) {
        User u = users.findById(id)
                .orElseThrow(() -> ApiException.notFound("账号不存在"));
        String name = u.getUsername();
        users.delete(u);
        // 清掉该用户全部缓存（外键 CASCADE 已删存档行）
        for (int slot = 1; slot <= 3; slot++) {
            cache.evict(id, slot);
        }
        log("USER_DELETE", "id=" + id + " username=" + name);
        return Map.of("ok", true);
    }

    // ==================== 配置 CRUD（六组统一模式） ====================

    @GetMapping("/equipment")
    public Map<String, Object> listEquipment() {
        return rows(equipmentRepo.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getSortNo(), b.getSortNo())).toList());
    }

    @PostMapping("/equipment")
    @Transactional
    public Map<String, Object> createEquipment(@RequestBody EquipmentTemplate t) {
        validateEquipment(t);
        if (t.getId() != null || equipmentRepo.existsByCode(t.getCode())) {
            throw ApiException.conflict("code 已存在：" + t.getCode());
        }
        normalize(t);
        equipmentRepo.save(t);
        log("EQUIP_UPSERT", t.getCode() + " " + t.getName());
        gameData.reload();
        return Map.of("ok", true, "id", t.getId());
    }

    @PutMapping("/equipment/{id}")
    @Transactional
    public Map<String, Object> updateEquipment(@PathVariable long id, @RequestBody EquipmentTemplate t) {
        EquipmentTemplate old = equipmentRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("装备不存在"));
        if (t.getCode() != null && !t.getCode().equals(old.getCode())
                && equipmentRepo.existsByCode(t.getCode())) {
            throw ApiException.conflict("code 已存在：" + t.getCode());
        }
        validateEquipment(t);
        apply(old, t);
        equipmentRepo.save(old);
        log("EQUIP_UPSERT", old.getCode() + " " + old.getName());
        gameData.reload();
        return Map.of("ok", true);
    }

    @DeleteMapping("/equipment/{id}")
    @Transactional
    public Map<String, Object> deleteEquipment(@PathVariable long id) {
        equipmentRepo.deleteById(id);
        log("EQUIP_DELETE", "id=" + id);
        gameData.reload();
        return Map.of("ok", true);
    }

    private static void validateEquipment(EquipmentTemplate t) {
        if (t == null || t.getCode() == null || t.getCode().isBlank()
                || t.getName() == null || t.getName().isBlank()
                || t.getSlot() == null || t.getSlot().isBlank()) {
            throw ApiException.badRequest("code/name/slot 必填");
        }
        if (t.getBaseStats() == null || t.getBaseStats().isBlank()) {
            t.setBaseStats("{}");
        }
    }

    private static void apply(EquipmentTemplate old, EquipmentTemplate t) {
        if (t.getCode() != null) old.setCode(t.getCode());
        if (t.getName() != null) old.setName(t.getName());
        old.setEmoji(t.getEmoji());
        if (t.getSlot() != null) old.setSlot(t.getSlot());
        if (t.getTier() != null) old.setTier(t.getTier());
        old.setMinZone(t.getMinZone());
        if (t.getBaseStats() != null) old.setBaseStats(t.getBaseStats());
        if (t.getEnhanceMax() != null) old.setEnhanceMax(t.getEnhanceMax());
        if (t.getSellBase() != null) old.setSellBase(t.getSellBase());
        old.setRemark(t.getRemark());
        if (t.getEnabled() != null) old.setEnabled(t.getEnabled());
        if (t.getSortNo() != null) old.setSortNo(t.getSortNo());
    }

    private static void normalize(EquipmentTemplate t) {
        if (t.getTier() == null) t.setTier(0);
        if (t.getEnhanceMax() == null) t.setEnhanceMax(10);
        if (t.getSellBase() == null) t.setSellBase(20);
        if (t.getEnabled() == null) t.setEnabled(true);
        if (t.getSortNo() == null) t.setSortNo(0);
    }

    // ---------- 词条 ----------

    @GetMapping("/affix")
    public Map<String, Object> listAffix() {
        return rows(affixRepo.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getSortNo(), b.getSortNo())).toList());
    }

    @PostMapping("/affix")
    @Transactional
    public Map<String, Object> createAffix(@RequestBody AffixTemplate t) {
        if (t == null || t.getCode() == null || t.getCode().isBlank()
                || t.getName() == null || t.getStatKey() == null) {
            throw ApiException.badRequest("code/name/statKey 必填");
        }
        if (affixRepo.existsByCode(t.getCode())) {
            throw ApiException.conflict("code 已存在：" + t.getCode());
        }
        if (t.getStatType() == null) t.setStatType("flat");
        if (t.getMinVal() == null) t.setMinVal(0.0);
        if (t.getMaxVal() == null) t.setMaxVal(0.0);
        if (t.getEnabled() == null) t.setEnabled(true);
        if (t.getSortNo() == null) t.setSortNo(0);
        affixRepo.save(t);
        log("AFFIX_UPSERT", t.getCode() + " " + t.getName());
        gameData.reload();
        return Map.of("ok", true, "id", t.getId());
    }

    @PutMapping("/affix/{id}")
    @Transactional
    public Map<String, Object> updateAffix(@PathVariable long id, @RequestBody AffixTemplate t) {
        AffixTemplate old = affixRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("词条不存在"));
        if (t.getCode() != null) old.setCode(t.getCode());
        if (t.getName() != null) old.setName(t.getName());
        if (t.getStatKey() != null) old.setStatKey(t.getStatKey());
        if (t.getStatType() != null) old.setStatType(t.getStatType());
        if (t.getMinVal() != null) old.setMinVal(t.getMinVal());
        if (t.getMaxVal() != null) old.setMaxVal(t.getMaxVal());
        old.setRarity(t.getRarity());
        old.setSideKey(t.getSideKey());
        old.setSideVal(t.getSideVal());
        if (t.getEnabled() != null) old.setEnabled(t.getEnabled());
        if (t.getSortNo() != null) old.setSortNo(t.getSortNo());
        affixRepo.save(old);
        log("AFFIX_UPSERT", old.getCode() + " " + old.getName());
        gameData.reload();
        return Map.of("ok", true);
    }

    @DeleteMapping("/affix/{id}")
    @Transactional
    public Map<String, Object> deleteAffix(@PathVariable long id) {
        affixRepo.deleteById(id);
        log("AFFIX_DELETE", "id=" + id);
        gameData.reload();
        return Map.of("ok", true);
    }

    // ---------- 商品 ----------

    @GetMapping("/item")
    public Map<String, Object> listItem() {
        return rows(itemRepo.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getSortNo(), b.getSortNo())).toList());
    }

    @PostMapping("/item")
    @Transactional
    public Map<String, Object> createItem(@RequestBody ItemTemplate t) {
        if (t == null || t.getCode() == null || t.getCode().isBlank()
                || t.getName() == null || t.getName().isBlank()) {
            throw ApiException.badRequest("code/name 必填");
        }
        if (itemRepo.existsByCode(t.getCode())) {
            throw ApiException.conflict("code 已存在：" + t.getCode());
        }
        if (t.getKind() == null) t.setKind("potion");
        if (t.getPrice() == null) t.setPrice(0);
        if (t.getEffectJson() == null || t.getEffectJson().isBlank()) t.setEffectJson("{}");
        if (t.getEnabled() == null) t.setEnabled(true);
        if (t.getSortNo() == null) t.setSortNo(0);
        itemRepo.save(t);
        log("ITEM_UPSERT", t.getCode() + " " + t.getName());
        gameData.reload();
        return Map.of("ok", true, "id", t.getId());
    }

    @PutMapping("/item/{id}")
    @Transactional
    public Map<String, Object> updateItem(@PathVariable long id, @RequestBody ItemTemplate t) {
        ItemTemplate old = itemRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("商品不存在"));
        if (t.getCode() != null) old.setCode(t.getCode());
        if (t.getName() != null) old.setName(t.getName());
        old.setEmoji(t.getEmoji());
        if (t.getKind() != null) old.setKind(t.getKind());
        if (t.getPrice() != null) old.setPrice(t.getPrice());
        if (t.getEffectJson() != null) old.setEffectJson(t.getEffectJson());
        old.setDesc(t.getDesc());
        if (t.getEnabled() != null) old.setEnabled(t.getEnabled());
        if (t.getSortNo() != null) old.setSortNo(t.getSortNo());
        itemRepo.save(old);
        log("ITEM_UPSERT", old.getCode() + " " + old.getName());
        gameData.reload();
        return Map.of("ok", true);
    }

    @DeleteMapping("/item/{id}")
    @Transactional
    public Map<String, Object> deleteItem(@PathVariable long id) {
        itemRepo.deleteById(id);
        log("ITEM_DELETE", "id=" + id);
        gameData.reload();
        return Map.of("ok", true);
    }

    // ---------- 技能 ----------

    @GetMapping("/skill")
    public Map<String, Object> listSkill() {
        return rows(skillRepo.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getSortNo(), b.getSortNo())).toList());
    }

    @PostMapping("/skill")
    @Transactional
    public Map<String, Object> createSkill(@RequestBody SkillTemplate t) {
        if (t == null || t.getCode() == null || t.getCode().isBlank()
                || t.getName() == null || t.getName().isBlank()) {
            throw ApiException.badRequest("code/name 必填");
        }
        if (skillRepo.existsByCode(t.getCode())) {
            throw ApiException.conflict("code 已存在：" + t.getCode());
        }
        if (t.getKind() == null) t.setKind("active");
        if (t.getPrice() == null) t.setPrice(0);
        if (t.getMp() == null) t.setMp(0);
        if (t.getCd() == null) t.setCd(0);
        if (t.getDur() == null) t.setDur(0);
        if (t.getEffectJson() == null || t.getEffectJson().isBlank()) t.setEffectJson("{}");
        if (t.getEnabled() == null) t.setEnabled(true);
        if (t.getSortNo() == null) t.setSortNo(0);
        skillRepo.save(t);
        log("SKILL_UPSERT", t.getCode() + " " + t.getName());
        gameData.reload();
        return Map.of("ok", true, "id", t.getId());
    }

    @PutMapping("/skill/{id}")
    @Transactional
    public Map<String, Object> updateSkill(@PathVariable long id, @RequestBody SkillTemplate t) {
        SkillTemplate old = skillRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("技能不存在"));
        if (t.getCode() != null) old.setCode(t.getCode());
        if (t.getName() != null) old.setName(t.getName());
        old.setEmoji(t.getEmoji());
        if (t.getKind() != null) old.setKind(t.getKind());
        if (t.getPrice() != null) old.setPrice(t.getPrice());
        if (t.getMp() != null) old.setMp(t.getMp());
        if (t.getCd() != null) old.setCd(t.getCd());
        if (t.getDur() != null) old.setDur(t.getDur());
        if (t.getEffectJson() != null) old.setEffectJson(t.getEffectJson());
        old.setDesc(t.getDesc());
        if (t.getEnabled() != null) old.setEnabled(t.getEnabled());
        if (t.getSortNo() != null) old.setSortNo(t.getSortNo());
        skillRepo.save(old);
        log("SKILL_UPSERT", old.getCode() + " " + old.getName());
        gameData.reload();
        return Map.of("ok", true);
    }

    @DeleteMapping("/skill/{id}")
    @Transactional
    public Map<String, Object> deleteSkill(@PathVariable long id) {
        skillRepo.deleteById(id);
        log("SKILL_DELETE", "id=" + id);
        gameData.reload();
        return Map.of("ok", true);
    }

    // ---------- 状态 ----------

    @GetMapping("/buff")
    public Map<String, Object> listBuff() {
        return rows(buffRepo.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getSortNo(), b.getSortNo())).toList());
    }

    @PostMapping("/buff")
    @Transactional
    public Map<String, Object> createBuff(@RequestBody BuffTemplate t) {
        if (t == null || t.getCode() == null || t.getCode().isBlank()
                || t.getName() == null || t.getName().isBlank()) {
            throw ApiException.badRequest("code/name 必填");
        }
        if (buffRepo.existsByCode(t.getCode())) {
            throw ApiException.conflict("code 已存在：" + t.getCode());
        }
        if (t.getNature() == null) t.setNature("good");
        if (t.getGroupType() == null) t.setGroupType("passive");
        if (t.getEffectJson() == null || t.getEffectJson().isBlank()) t.setEffectJson("{}");
        if (t.getEnabled() == null) t.setEnabled(true);
        if (t.getSortNo() == null) t.setSortNo(0);
        buffRepo.save(t);
        log("BUFF_UPSERT", t.getCode() + " " + t.getName());
        gameData.reload();
        return Map.of("ok", true, "id", t.getId());
    }

    @PutMapping("/buff/{id}")
    @Transactional
    public Map<String, Object> updateBuff(@PathVariable long id, @RequestBody BuffTemplate t) {
        BuffTemplate old = buffRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("状态不存在"));
        if (t.getCode() != null) old.setCode(t.getCode());
        if (t.getName() != null) old.setName(t.getName());
        old.setEmoji(t.getEmoji());
        if (t.getNature() != null) old.setNature(t.getNature());
        if (t.getGroupType() != null) old.setGroupType(t.getGroupType());
        if (t.getEffectJson() != null) old.setEffectJson(t.getEffectJson());
        old.setDesc(t.getDesc());
        if (t.getEnabled() != null) old.setEnabled(t.getEnabled());
        if (t.getSortNo() != null) old.setSortNo(t.getSortNo());
        buffRepo.save(old);
        log("BUFF_UPSERT", old.getCode() + " " + old.getName());
        gameData.reload();
        return Map.of("ok", true);
    }

    @DeleteMapping("/buff/{id}")
    @Transactional
    public Map<String, Object> deleteBuff(@PathVariable long id) {
        buffRepo.deleteById(id);
        log("BUFF_DELETE", "id=" + id);
        gameData.reload();
        return Map.of("ok", true);
    }

    // ---------- 爆率 ----------

    @GetMapping("/droprate")
    public Map<String, Object> listDropRate() {
        return rows(dropRepo.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getSortNo(), b.getSortNo())).toList());
    }

    @PostMapping("/droprate")
    @Transactional
    public Map<String, Object> createDropRate(@RequestBody DropRateRule t) {
        if (t == null || t.getZone() == null || t.getZone().isBlank()) {
            throw ApiException.badRequest("zone 必填");
        }
        if (dropRepo.findByZone(t.getZone()).isPresent()) {
            throw ApiException.conflict("zone 已存在：" + t.getZone());
        }
        if (t.getRate() == null) t.setRate(0.0);
        if (t.getBatchCap() == null) t.setBatchCap(3);
        if (t.getEnabled() == null) t.setEnabled(true);
        if (t.getSortNo() == null) t.setSortNo(0);
        dropRepo.save(t);
        log("DROPRATE_UPSERT", t.getZone() + " rate=" + t.getRate());
        gameData.reload();
        return Map.of("ok", true, "id", t.getId());
    }

    @PutMapping("/droprate/{id}")
    @Transactional
    public Map<String, Object> updateDropRate(@PathVariable long id, @RequestBody DropRateRule t) {
        DropRateRule old = dropRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("爆率规则不存在"));
        if (t.getZone() != null) old.setZone(t.getZone());
        if (t.getRate() != null) old.setRate(t.getRate());
        if (t.getBatchCap() != null) old.setBatchCap(t.getBatchCap());
        if (t.getEnabled() != null) old.setEnabled(t.getEnabled());
        if (t.getSortNo() != null) old.setSortNo(t.getSortNo());
        dropRepo.save(old);
        log("DROPRATE_UPSERT", old.getZone() + " rate=" + old.getRate());
        gameData.reload();
        return Map.of("ok", true);
    }

    @DeleteMapping("/droprate/{id}")
    @Transactional
    public Map<String, Object> deleteDropRate(@PathVariable long id) {
        dropRepo.deleteById(id);
        log("DROPRATE_DELETE", "id=" + id);
        gameData.reload();
        return Map.of("ok", true);
    }

    // ==================== 手动刷新 / 审计 ====================

    @PostMapping("/config/reload")
    public Map<String, Object> reloadConfig() {
        gameData.reload();
        log("CONFIG_RELOAD", null);
        return Map.of("ok", true);
    }

    /** 运营看板：最近 30 天每日统计。 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", statAggregator.dashboard());
        return out;
    }

    @GetMapping("/audit")
    public Map<String, Object> auditList() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AdminAuditLog a : audit.findTop100ByOrderByCreatedAtDesc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("action", a.getAction());
            m.put("detail", a.getDetail());
            m.put("createdAt", a.getCreatedAt());
            rows.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        return out;
    }

    private static Map<String, Object> rows(List<?> list) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", list);
        return out;
    }
}
