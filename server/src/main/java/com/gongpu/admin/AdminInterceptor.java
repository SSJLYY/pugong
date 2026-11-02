package com.gongpu.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 管理端拦截器：/api/admin/**（login 除外，放行名单在 WebConfig 配置）。 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    private final AdminGuard guard;

    public AdminInterceptor(AdminGuard guard) {
        this.guard = guard;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            return true;
        }
        guard.requireAdmin(req);
        return true;
    }
}
