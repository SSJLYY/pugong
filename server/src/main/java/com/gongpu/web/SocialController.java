package com.gongpu.web;

import com.gongpu.security.AuthInterceptor;
import com.gongpu.stats.SocialService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 社交玩法：世界墓园 / 香火祭奠 / 香火复活 / 切磋。 */
@RestController
@RequestMapping("/api/social")
public class SocialController {

    public record TributeReq(long graveId, int slot) {
    }

    public record DuelReq(String target, int slot) {
    }

    public record ReviveReq(int slot) {
    }

    private final SocialService social;
    private final com.gongpu.security.JwtService jwt;
    private final com.gongpu.config.StateCache cache;

    public SocialController(SocialService social, com.gongpu.security.JwtService jwt,
                            com.gongpu.config.StateCache cache) {
        this.social = social;
        this.jwt = jwt;
        this.cache = cache;
    }

    /** 写端点统一加锁：与 GameController.run() 同一把锁，防止读-算-写交错。 */
    /** 锁模板已收口到 StateCache.withLock，此处仅保留一行转发（语义：短等重试，不再瞬时拒绝）。 */
    private <T> T locked(long uid, int slot, java.util.function.Supplier<T> body) {
        return cache.withLock(uid, slot, body);
    }

    /** 世界墓园（公开，未登录也能看）。 */
    @GetMapping("/graveyard")
    public Map<String, Object> worldGraveyard(HttpServletRequest req) {
        Long viewerId = null;
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            try {
                viewerId = jwt.verifyAndGetUserId(h.substring(7).trim());
            } catch (RuntimeException ignored) {
                // 无效 token 当作未登录
            }
        }
        return social.worldGraveyard(viewerId);
    }

    /** 我的战绩卡。 */
    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest req) {
        return social.pvpCard(AuthInterceptor.userId(req));
    }

    /** 祭奠墓碑（给墓主送香火）。 */
    @PostMapping("/tribute")
    public Map<String, Object> tribute(HttpServletRequest req, @RequestBody TributeReq body) {
        long uid = AuthInterceptor.userId(req);
        return locked(uid, body.slot(), () -> social.tribute(uid, body.graveId(), body.slot()));
    }

    /** 香火复活。 */
    @PostMapping("/revive")
    public Map<String, Object> revive(HttpServletRequest req, @RequestBody ReviveReq body) {
        long uid = AuthInterceptor.userId(req);
        return locked(uid, body.slot(), () -> social.reviveByIncense(uid, body.slot()));
    }

    /** 切磋。 */
    @PostMapping("/duel")
    public Map<String, Object> duel(HttpServletRequest req, @RequestBody DuelReq body) {
        long uid = AuthInterceptor.userId(req);
        return locked(uid, body.slot(), () -> social.duel(uid, body.slot(), body.target()));
    }

    /** 闲话一句（点缀用，公开）。 */
    @GetMapping("/flavor")
    public Map<String, Object> flavor() {
        return Map.of("ok", true, "text", SocialService.flavor());
    }

    /** 世界公告（史诗掉落播报，公开）。 */
    @GetMapping("/bulletin")
    public Map<String, Object> bulletin() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("rows", social.bulletin());
        return m;
    }
}
