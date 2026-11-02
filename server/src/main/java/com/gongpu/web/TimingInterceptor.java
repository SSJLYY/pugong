package com.gongpu.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 慢端点观测：超过阈值（默认 500ms）的 API 请求打 WARN 日志。
 * <p>玩家轮询正常 <100ms；慢日志是容量预警与性能回归的第一道哨兵。
 * 本地 profile 可把阈值调低（application-local.yml gongpu.slow-ms）。</p>
 */
@Component
public class TimingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TimingInterceptor.class);
    private static final String ATTR = "gongpu.timing.t0";

    private final long slowMs;

    public TimingInterceptor(@org.springframework.beans.factory.annotation.Value("${gongpu.slow-ms:500}") long slowMs) {
        this.slowMs = slowMs;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        req.setAttribute(ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res,
                                Object handler, Exception ex) {
        Object t0 = req.getAttribute(ATTR);
        if (t0 == null) {
            return;
        }
        long cost = System.currentTimeMillis() - (Long) t0;
        if (cost >= slowMs) {
            log.warn("慢端点: {} {} {}ms status={} uid={}",
                    req.getMethod(), req.getRequestURI(), cost, res.getStatus(),
                    req.getHeader("Authorization") == null ? "-" : "有");
        }
    }
}
