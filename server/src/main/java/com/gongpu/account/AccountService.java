package com.gongpu.account;

import com.gongpu.common.ApiException;
import com.gongpu.security.JwtService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** 账号服务：注册 / 登录 / 当前用户。 */
@Service
public class AccountService {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_\\u4e00-\\u9fa5]{2,16}$");
    private static final int MIN_PASSWORD = 6;

    private final UserRepository users;
    private final JwtService jwt;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AccountService(UserRepository users, JwtService jwt) {
        this.users = users;
        this.jwt = jwt;
    }

    @Transactional
    public Map<String, Object> register(String username, String password) {
        String u = username == null ? "" : username.trim();
        if (!USERNAME.matcher(u).matches()) {
            throw ApiException.badRequest("用户名需 2~16 位，仅限中英文、数字、下划线");
        }
        if (password == null || password.length() < MIN_PASSWORD) {
            throw ApiException.badRequest("密码至少 " + MIN_PASSWORD + " 位");
        }
        if (users.existsByUsername(u)) {
            throw ApiException.conflict("该用户名已被占用");
        }
        User user = new User();
        user.setUsername(u);
        user.setPasswordHash(encoder.encode(password));
        user.setCreatedAt(Instant.now());
        user.setLastLoginAt(Instant.now());
        try {
            users.save(user);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 并发注册同一用户名：唯一约束兜底，转成友好 409
            throw ApiException.conflict("该用户名已被占用");
        }
        return tokenPayload(user);
    }

    @Transactional
    public Map<String, Object> login(String username, String password) {
        User user = users.findByUsername(username == null ? "" : username.trim())
                .orElseThrow(() -> ApiException.unauthorized("用户名或密码错误"));
        if (!encoder.matches(password == null ? "" : password, user.getPasswordHash())) {
            throw ApiException.unauthorized("用户名或密码错误");
        }
        user.setLastLoginAt(Instant.now());
        users.save(user);
        return tokenPayload(user);
    }

    public User require(long userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.unauthorized("账号不存在"));
    }

    /**
     * 修改密码（已登录）：验原密码后换新哈希。原密码错走 401（前端不会误清会话，
     * 因 /api/auth/* 的 401 不触发过期广播）；新密码规则与注册一致（至少 6 位）且不能与原密码相同。
     */
    @Transactional
    public void changePassword(long userId, String oldPassword, String newPassword) {
        User user = require(userId);
        if (!encoder.matches(oldPassword == null ? "" : oldPassword, user.getPasswordHash())) {
            throw ApiException.unauthorized("原密码不正确");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw ApiException.badRequest("新密码至少 6 位");
        }
        if (encoder.matches(newPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("新密码不能与原密码相同");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
    }

    /**
     * 原子每日签到：DB 条件更新按影响行数判定，防同账号多槽并发重复领奖。
     * 返回 false = 今天已领过。
     */
    @Transactional
    public boolean claimDailyAtomic(long userId, java.time.LocalDate today) {
        return users.claimDaily(userId, today, today.minusDays(1)) > 0;
    }

    /** 连签预测：与原子领取的 CASE 同口径——昨日签过则 +1，否则从 1 起（今天已签由调用方预检拦截）。 */
    public static int nextStreak(java.time.LocalDate lastClaimDay, int curStreak, java.time.LocalDate today) {
        return today.minusDays(1).equals(lastClaimDay) ? curStreak + 1 : 1;
    }

    /** 连签金币加成：每天 +50，封顶 7 天（350）。 */
    public static long streakBonusGold(int streak) {
        return Math.min(Math.max(streak, 0), 7) * 50L;
    }

    /** 连签周奖：每逢 7 的倍数天额外发大血药。 */
    public static boolean streakWeeklyBonus(int streak) {
        return streak > 0 && streak % 7 == 0;
    }

    private Map<String, Object> tokenPayload(User user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", jwt.issue(user.getId(), user.getUsername()));
        m.put("expiresIn", jwt.ttlSeconds());
        m.put("userId", user.getId());
        m.put("username", user.getUsername());
        return m;
    }
}
