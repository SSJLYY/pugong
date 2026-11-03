package com.gongpu.security;

import com.gongpu.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器：校验 Authorization: Bearer &lt;token&gt;，把 userId 放进 request attribute。
 * <p>刻意不用 Spring Security 的过滤器链——本项目的鉴权只有"登录态"一件小事，
 * 引入完整安全链会带来不必要的默认行为与配置负担。</p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER_ID = "gongpu.userId";
    public static final String ATTR_USERNAME = "gongpu.username";

    private final JwtService jwt;

    public AuthInterceptor(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            return true;
        }
        String h = req.getHeader("Authorization");
        if (h == null || !h.startsWith("Bearer ")) {
            throw ApiException.unauthorized("请先登录");
        }
        String token = h.substring(7).trim();
        JwtService.Verified v = jwt.verify(token);
        req.setAttribute(ATTR_USER_ID, v.userId());
        if (v.username() != null) {
            req.setAttribute(ATTR_USERNAME, v.username());
        }
        return true;
    }

    public static long userId(HttpServletRequest req) {
        Object v = req.getAttribute(ATTR_USER_ID);
        if (v == null) {
            throw ApiException.unauthorized("请先登录");
        }
        return (Long) v;
    }

    /** JWT 内的用户名（免查库）；老令牌无此 claim 时返回 null，调用方自行回源。 */
    public static String username(HttpServletRequest req) {
        Object v = req.getAttribute(ATTR_USERNAME);
        return v == null ? null : (String) v;
    }
}
