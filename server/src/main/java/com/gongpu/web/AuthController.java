package com.gongpu.web;

import com.gongpu.account.AccountService;
import com.gongpu.security.AuthRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginReq(@NotBlank String username, @NotBlank String password) {
    }

    public record PwReq(@NotBlank String oldPassword, @NotBlank String newPassword) {
    }

    private final AccountService account;
    private final AuthRateLimiter limiter;

    public AuthController(AccountService account, AuthRateLimiter limiter) {
        this.account = account;
        this.limiter = limiter;
    }

    /** 真实客户端 IP:优先 X-Real-IP(nginx 覆写自 $remote_addr),XFF 兜底;
     *  两者都被边缘 nginx 覆写,客户端伪造的头到不了这里。 */
    static String clientIp(HttpServletRequest req) {
        String xri = req.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) {
            return xri.trim();
        }
        String xff = req.getHeader("X-Forwarded-For");
        return xff != null && !xff.isBlank()
                ? xff.split(",")[0].trim()
                : req.getRemoteAddr();
    }

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody LoginReq req, HttpServletRequest http) {
        String ip = clientIp(http);
        limiter.checkRegisterAllowed(ip);
        Map<String, Object> out = account.register(req.username(), req.password());
        limiter.onRegister(ip);
        return out;
    }

    /** 修改密码（需登录态，拦截器放行名单外）：成功后旧密码立即失效，当前会话不受影响。 */
    @PostMapping("/password")
    public Map<String, Object> changePassword(@Valid @RequestBody PwReq req, HttpServletRequest http) {
        account.changePassword(
                com.gongpu.security.AuthInterceptor.userId(http), req.oldPassword(), req.newPassword());
        return Map.of("ok", true);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginReq req, HttpServletRequest http) {
        String ip = clientIp(http);
        limiter.checkLoginAllowed(ip);
        try {
            Map<String, Object> out = account.login(req.username(), req.password());
            limiter.onLoginSuccess(ip);
            return out;
        } catch (RuntimeException e) {
            // 第二十批·轮9：只对 401 认证失败（用户名/密码错）记账。
            // 此前一切 RuntimeException 都记——DB 抖动等基础设施异常会把无辜 IP
            // 计到锁死线（10 次/10 分钟），把故障放大成"全员登不上"。
            // 429（已锁定）/500 等业务与基础设施异常不重复计数。
            if (e instanceof com.gongpu.common.ApiException ae && ae.getStatus() == 401) {
                limiter.onLoginFail(ip);
            }
            throw e;
        }
    }
}
