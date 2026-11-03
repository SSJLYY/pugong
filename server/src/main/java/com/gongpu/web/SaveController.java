package com.gongpu.web;

import com.gongpu.game.GameService;
import com.gongpu.security.AuthInterceptor;
import com.gongpu.state.PlayerState;
import com.gongpu.state.SaveService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 存档槽接口。
 * <p>所有写操作结束都统一 persist，保证 Redis 与 MySQL 一致。</p>
 */
@RestController
@RequestMapping("/api/saves")
public class SaveController {

    public record CreateReq(String slotName) {
    }


    private final SaveService saves;
    private final GameService game;
    private final com.gongpu.config.StateCache cache;
    private final com.gongpu.account.AccountService accounts;

    public SaveController(SaveService saves, GameService game,
                          com.gongpu.config.StateCache cache,
                          com.gongpu.account.AccountService accounts) {
        this.saves = saves;
        this.game = game;
        this.cache = cache;
        this.accounts = accounts;
    }

    /** 写端点统一加锁：与 GameController.run() 同一把锁，防止读-算-写交错。 */
    /** 锁模板已收口到 StateCache.withLock，此处仅保留一行转发（语义：短等重试，不再瞬时拒绝）。 */
    private <T> T locked(long uid, int slot, java.util.function.Supplier<T> body) {
        return cache.withLock(uid, slot, body);
    }

    /** void 版本（Runnable 在 java.lang，无需 import）。 */
    private void locked(long uid, int slot, Runnable body) {
        cache.withLock(uid, slot, body);
    }

    @GetMapping
    public List<Map<String, Object>> list(HttpServletRequest req) {
        return saves.list(AuthInterceptor.userId(req));
    }

    /**
     * 导出存档 JSON（玩家自备备份/申诉举证）。
     * 只出不进：导入会绕过全部服务端数值校验与成就/锁语义，明确不开放。
     */
    @GetMapping("/{slot}/export")
    public org.springframework.http.ResponseEntity<String> export(HttpServletRequest req,
                                                                  @PathVariable int slot) {
        PlayerState s = saves.load(AuthInterceptor.userId(req), slot);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"gongpu-save-slot" + slot + ".json\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(com.gongpu.state.StateCodec.encode(s));
    }

    @PostMapping("/{slot}")
    public Map<String, Object> create(HttpServletRequest req, @PathVariable int slot,
                                      @RequestBody(required = false) CreateReq body) {
        long uid = AuthInterceptor.userId(req);
        String name = body == null ? null : body.slotName();
        PlayerState s = locked(uid, slot, () -> saves.create(uid, slot, name));
        return game.fullView(s);
    }

    @GetMapping("/{slot}")
    public Map<String, Object> load(HttpServletRequest req, @PathVariable int slot) {
        long uid = AuthInterceptor.userId(req);
        return locked(uid, slot, () -> {
            PlayerState s = saves.load(uid, slot);
            s.bulletinName = accounts.require(uid).getUsername();
            saves.advanceNow(uid, slot, s);
            saves.persist(uid, slot, s, null);
            return game.fullView(s);
        });
    }

    @PostMapping("/{slot}/save")
    public Map<String, Object> save(HttpServletRequest req, @PathVariable int slot) {
        long uid = AuthInterceptor.userId(req);
        return locked(uid, slot, () -> {
            PlayerState s = saves.load(uid, slot);
            if (s.dead) {
                throw com.gongpu.common.ApiException.badRequest("已经阵亡，无法存档");
            }
            if (!"village".equals(s.zone)) {
                throw com.gongpu.common.ApiException.badRequest("战斗中不可存档：请先回村口");
            }
            saves.createSnapshot(uid, slot, s);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ok", true);
            m.put("savedAt", s.lastSaveAt);
            return m;
        });
    }

    @DeleteMapping("/{slot}")
    public Map<String, Object> delete(HttpServletRequest req, @PathVariable int slot) {
        long uid = AuthInterceptor.userId(req);
        locked(uid, slot, () -> saves.delete(uid, slot));
        return Map.of("ok", true);
    }
}
