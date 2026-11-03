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

    // 进程级请求指标（第七批）：/api/health 下发，运维不必翻日志就知道慢请求占比
    private static final java.util.concurrent.atomic.AtomicLong REQ_TOTAL =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong REQ_SLOW =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong REQ_MAX_MS =
            new java.util.concurrent.atomic.AtomicLong();

    /** 记录一次请求耗时；返回是否为慢请求。静态便于单测与 health 读取。 */
    static boolean record(long costMs, long slowMs) {
        REQ_TOTAL.incrementAndGet();
        REQ_MAX_MS.accumulateAndGet(costMs, Math::max);
        boolean slow = costMs >= slowMs;
        if (slow) {
            REQ_SLOW.incrementAndGet();
        }
        return slow;
    }

    /** 指标快照（health 用）。 */
    public static java.util.Map<String, Object> stats(long slowMs) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("requests", REQ_TOTAL.get());
        m.put("slow", REQ_SLOW.get());
        m.put("slowMs", slowMs);
        m.put("maxMs", REQ_MAX_MS.get());
        return m;
    }

    public TimingInterceptor(@org.springframework.beans.factory.annotation.Value("${gongpu.slow-ms:500}") long slowMs) {
        this.slowMs = slowMs;
    }

    /** 实例侧快照（health 注入拦截器 bean 读取，阈值取实例配置）。 */
    public java.util.Map<String, Object> stats() {
        return stats(slowMs);
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
        if (record(cost, slowMs)) {
            log.warn("慢端点: {} {} {}ms status={} uid={}",
                    req.getMethod(), req.getRequestURI(), cost, res.getStatus(),
                    req.getHeader("Authorization") == null ? "-" : "有");
        }
    }
}
