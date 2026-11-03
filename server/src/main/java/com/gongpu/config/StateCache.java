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

    public PlayerState get(long userId, int slot) {
        try {
            String json = redis.opsForValue().get(key(userId, slot));
            return json == null ? null : StateCodec.decode(json);
        } catch (RuntimeException e) {
            // Redis 故障不应阻断读取，直接回源 DB
            return null;
        }
    }

    public void put(long userId, int slot, PlayerState state) {
        try {
            redis.opsForValue().set(key(userId, slot), StateCodec.encode(state), ttl);
        } catch (RuntimeException ignored) {
            // 缓存写失败可接受
        }
    }

    public void evict(long userId, int slot) {
        try {
            redis.delete(key(userId, slot));
        } catch (RuntimeException ignored) {
            // 忽略
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
            // fail-closed：Redis 不可用时拒绝推进，防止并发双结算
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
