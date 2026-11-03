package com.gongpu.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "account_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** 香火：其他玩家祭奠你的墓碑获得，可用于满血原地复活。 */
    @Column(name = "incense", nullable = false)
    private Integer incense = 0;

    /** 切磋战绩。 */
    @Column(name = "pvp_wins", nullable = false)
    private Integer pvpWins = 0;

    @Column(name = "pvp_losses", nullable = false)
    private Integer pvpLosses = 0;

    /** 每日签到：最近领取日（null=从未签到）。 */
    @Column(name = "last_claim_day")
    private java.time.LocalDate lastClaimDay;

    /** 连续签到天数（断签由原子领取的 CASE 重置为 1）。 */
    @Column(name = "claim_streak", nullable = false)
    private Integer claimStreak = 0;

    /** 令牌版本：改密时自增，JWT 携带签发时版本，拦截器比对不一致即失效。 */
    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 0;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Integer getIncense() {
        return incense;
    }

    public void setIncense(Integer incense) {
        this.incense = incense;
    }

    public Integer getPvpWins() {
        return pvpWins;
    }

    public void setPvpWins(Integer pvpWins) {
        this.pvpWins = pvpWins;
    }

    public Integer getPvpLosses() {
        return pvpLosses;
    }

    public void setPvpLosses(Integer pvpLosses) {
        this.pvpLosses = pvpLosses;
    }

    public java.time.LocalDate getLastClaimDay() {
        return lastClaimDay;
    }

    public void setLastClaimDay(java.time.LocalDate lastClaimDay) {
        this.lastClaimDay = lastClaimDay;
    }

    public Integer getClaimStreak() {
        return claimStreak;
    }

    public void setClaimStreak(Integer claimStreak) {
        this.claimStreak = claimStreak;
    }

    public Integer getTokenVersion() {
        return tokenVersion == null ? 0 : tokenVersion;
    }

    public void setTokenVersion(Integer tokenVersion) {
        this.tokenVersion = tokenVersion;
    }
}
