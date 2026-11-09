package com.gongpu.config;

import com.gongpu.state.PlayerState;
import com.gongpu.state.StateCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 在线状态缓存（Redis）。
 * <p>定位：<b>加速层，非权威源</b>。MySQL 才是真相，Redis 只缓存
 * 「正在被反复推进的活跃档」，减少每次 tick 的 DB 读写。</p>
 * <p>锁设计：</p>
 * <ul>
 *   <li><b>fail-closed</b>：Redis 故障时拒绝推进而不是放行，防止并发双结算
 *       （离线收益重复入账）。此时调用方提示"稍后重试"即可。</li>
 *   <li><b>属主 token</b>：加锁写入唯一 token，解锁用 Lua 比对后删除，
 *       防止持锁过期后误删他人的锁。</li>
 * </ul>
 */
@Component
public class StateCache {

    private final StringRedisTemplate redis;
    private final Duration ttl;

    private static final String LOCK_PREFIX = "gongpu:lock:";

    /** 本次线程持有的锁 token（lock 成功后写入，unlock 读取并清除）。 */
    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    /** 释放锁的 Lua：仅当持锁 token 匹配才删除，防止误删他人锁。 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    public StateCache(StringRedisTemplate redis,
                      @Value("${gongpu.game.state-cache-ttl-min:30}") long ttlMinutes) {
        this.redis = redis;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    private String key(long userId, int slot) {
        return "gongpu:state:" + userId + ":" + slot;
    }

    // 缓存命中观测（第八批）：进程级计数，health 输出命中率便于判断缓存是否真在工作
    private static final java.util.concurrent.atomic.AtomicLong HITS = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong MISSES = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong PUTS = new java.util.concurrent.atomic.AtomicLong();

    public static java.util.Map<String, Object> stats() {
        long h = HITS.get();
        long mi = MISSES.get();
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("hits", h);
        m.put("misses", mi);
        m.put("puts", PUTS.get());
        m.put("hitRate", (h + mi) == 0 ? 0 : Math.round(1000.0 * h / (h + mi)) / 10.0);
        return m;
    }

    // ---- 第二十批·轮8：Redis 异常可观测 + 读路径熔断 ----
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StateCache.class);
    /** 告警节流：Redis 宕机时全 catch 每秒触发，30s 一条合并防刷屏。 */
    private static final long WARN_INTERVAL_MS = 30_000L;
    private static final java.util.concurrent.atomic.AtomicLong LAST_WARN =
            new java.util.concurrent.atomic.AtomicLong();

    private static void warnThrottled(String op, long userId, int slot, RuntimeException e) {
        long now = System.currentTimeMillis();
        long last = LAST_WARN.get();
        if (now - last >= WARN_INTERVAL_MS && LAST_WARN.compareAndSet(last, now)) {
            log.warn("Redis {} 失败({}:{}): {}（30s 内同类异常合并）", op, userId, slot, e.toString());
        }
    }

    /** get 熔断：连续 3 次 Redis 异常后 10s 内直接回源 MySQL——
     *  否则故障期间每秒轮询×每玩家都等满超时再回源，DB 连接池被拖垮。 */
    private static final int CB_FAILS = 3;
    private static final long CB_OPEN_MS = 10_000L;
    private final java.util.concurrent.atomic.AtomicInteger consecutiveGetFails =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicLong circuitOpenUntil =
            new java.util.concurrent.atomic.AtomicLong();

    public PlayerState get(long userId, int slot) {
        if (System.currentTimeMillis() < circuitOpenUntil.get()) {
            MISSES.incrementAndGet();
            return null;
        }
        String json;
        try {
            json = redis.opsForValue().get(key(userId, slot));
            consecutiveGetFails.set(0);
        } catch (RuntimeException e) {
            // Redis 故障不应阻断读取，直接回源 DB；连续异常触发熔断
            warnThrottled("get", userId, slot, e);
            if (consecutiveGetFails.incrementAndGet() >= CB_FAILS) {
                circuitOpenUntil.set(System.currentTimeMillis() + CB_OPEN_MS);
                consecutiveGetFails.set(0);
            }
            return null;
        }
        if (json == null) {
            MISSES.incrementAndGet();
            return null;
        }
        HITS.incrementAndGet();
        try {
            return StateCodec.decode(json);
        } catch (RuntimeException e) {
            // 坏缓存 JSON：回源 DB（decodeOrRecover 有备份环自愈），不计入 Redis 熔断
            warnThrottled("decode", userId, slot, e);
            return null;
        }
    }

    public void put(long userId, int slot, PlayerState state) {
        putJson(userId, slot, StateCodec.encode(state));
    }

    /** 第二十批·轮2：调用方已编码时直接写入，免去同一帧的第二次全量 Jackson encode。 */
    public void putJson(long userId, int slot, String json) {
        try {
            redis.opsForValue().set(key(userId, slot), json, ttl);
            PUTS.incrementAndGet();
        } catch (RuntimeException e) {
            // 缓存写失败可接受（MySQL 是权威源）；留节流 warn 便于发现 Redis 故障
            warnThrottled("put", userId, slot, e);
        }
    }

    public void evict(long userId, int slot) {
        try {
            redis.delete(key(userId, slot));
        } catch (RuntimeException e) {
            warnThrottled("evict", userId, slot, e);
        }
    }

    /**
     * 分布式锁：同一档同一时刻只允许一个推进请求。
     * <p>Redis 故障时 fail-closed（返回 false），调用方提示稍后重试。
     * 锁带 TTL 兜底：持锁线程崩溃时锁自然过期，不会死锁。</p>
     */
    public boolean lock(long userId, int slot, long seconds) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(
                    LOCK_PREFIX + userId + ":" + slot, token, seconds, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(ok)) {
                TOKEN.set(token);
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            // fail-closed：Redis 不可用时拒绝推进，防止并发双结算；留日志（此前零痕迹，与真实高频操作不可区分）
            warnThrottled("lock", userId, slot, e);
            return false;
        }
    }

    /** 释放锁（带属主校验）。lock() 成功后必须配对调用。 */
    public void unlock(long userId, int slot) {
        String token = TOKEN.get();
        if (token == null) {
            return;
        }
        TOKEN.remove();
        try {
            // 注意：execute 的签名是 (script, keys, Object... args)，
            // token 必须作为单个 varargs 传入，不能包成 List（否则被序列化成"[token]"字符串，比对永假）
            redis.execute(UNLOCK_SCRIPT,
                    Collections.singletonList(LOCK_PREFIX + userId + ":" + slot),
                    token);
        } catch (RuntimeException ignored) {
            // 忽略：锁有 TTL，异常时等待自然过期
        }
    }

    /**
     * 统一锁模板（全项目写端点唯一实现）：短等重试拿锁——正常争锁（轮询持锁约百毫秒、
     * 双开页签）等对方让完即可，不直接 409；真并发双结算耗尽重试仍拒。
     * 拿到锁后执行 body，finally 释放。新写端点一律用它，不要再手写 lock/try/finally。
     */
    /** 拿锁（含短等重试）：成功返回 true；耗尽重试返回 false。withLock 与旧 try 块共用这一份实现。 */
    public boolean lockRetry(long userId, int slot) {
        boolean locked = lock(userId, slot, 10);
        int tries = 0;
        while (!locked && tries++ < 3) {
            try {
                Thread.sleep(120);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            locked = lock(userId, slot, 10);
        }
        return locked;
    }

    public <T> T withLock(long userId, int slot, java.util.function.Supplier<T> body) {
        if (!lockRetry(userId, slot)) {
            throw com.gongpu.common.ApiException.conflict("操作太快了，稍等一下");
        }
        try {
            return body.get();
        } finally {
            unlock(userId, slot);
        }
    }

    /** void 版（与 Supplier 版同语义）。 */
    public void withLock(long userId, int slot, Runnable body) {
        withLock(userId, slot, () -> {
            body.run();
            return null;
        });
    }

    public boolean ping() {
        try {
            String pong = redis.execute((org.springframework.data.redis.core.RedisCallback<String>)
                    conn -> conn.ping());
            return "PONG".equalsIgnoreCase(pong);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
