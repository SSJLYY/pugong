package com.gongpu.common;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 大数运算封装。
 * <p>游戏后期数值可达 1e44（载）甚至更高，double 只有约 15~16 位有效数字，
 * 无法支撑「每秒 1 载次出拳」的血量累积，因此统一使用 BigDecimal 并固定精度。</p>
 * <p>精度策略：28 位有效数字（DECIMAL128 同级），足以让 1e44 量级的每秒 +1e44 成长
 * 在数小时内不丢有效位；序列化时统一走字符串，避免 JSON 双精度截断。</p>
 */
public final class Num {

    /** 统一精度：28 位有效数字。 */
    public static final MathContext MC = new MathContext(28, RoundingMode.HALF_UP);

    public static final BigDecimal ZERO = BigDecimal.ZERO;

    private Num() {
    }

    public static BigDecimal of(long v) {
        return BigDecimal.valueOf(v);
    }

    public static BigDecimal of(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            throw new IllegalArgumentException("非法数值: " + v);
        }
        return new BigDecimal(Double.toString(v), MC);
    }

    /** 宽松解析：空串/非法值回落到默认值，用于处理历史存档。 */
    public static BigDecimal parse(String s, BigDecimal fallback) {
        if (s == null || s.isBlank()) {
            return fallback;
        }
        try {
            return new BigDecimal(s.trim(), MC);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return a.add(b, MC);
    }

    public static BigDecimal sub(BigDecimal a, BigDecimal b) {
        return a.subtract(b, MC);
    }

    public static BigDecimal mul(BigDecimal a, BigDecimal b) {
        return a.multiply(b, MC);
    }

    public static BigDecimal mul(BigDecimal a, double b) {
        return a.multiply(of(b), MC);
    }

    public static BigDecimal div(BigDecimal a, BigDecimal b) {
        if (b.signum() == 0) {
            return ZERO;
        }
        return a.divide(b, MC);
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.max(b);
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.min(b);
    }

    /** 向下取整为整数大数（用于「出拳次数」这类必须是整数的量）。 */
    public static BigDecimal floor(BigDecimal a) {
        return a.setScale(0, RoundingMode.FLOOR);
    }

    /** 非负钳制。 */
    public static BigDecimal nonNegative(BigDecimal a) {
        return a.signum() < 0 ? ZERO : a;
    }

    public static boolean gt(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) > 0;
    }

    public static boolean gte(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0;
    }

    public static boolean lt(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) < 0;
    }

    public static boolean lte(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0;
    }

    /** 输出为普通十进制字符串（不用科学计数法），保证 JSON 与前端大数库均可解析。 */
    public static String plain(BigDecimal a) {
        if (a == null) {
            return "0";
        }
        BigDecimal s = a.stripTrailingZeros();
        if (s.scale() < 0) {
            s = s.setScale(0);
        }
        return s.toPlainString();
    }

    /**
     * 中文大数简写（万/亿/兆/京/垓/秭/穰/沟/涧/正/载）。
     * 仅用于日志与榜单展示；前端自身有更精细的格式化实现。
     */
    public static String cn(BigDecimal a) {
        if (a == null) {
            return "0";
        }
        String[] units = {"", "万", "亿", "兆", "京", "垓", "秭", "穰", "沟", "涧", "正", "载"};
        if (a.abs().compareTo(BigDecimal.valueOf(1e4)) < 0) {
            return a.setScale(0, RoundingMode.DOWN).toPlainString();
        }
        int idx = 0;
        BigDecimal v = a;
        while (v.abs().compareTo(BigDecimal.valueOf(1e4)) >= 0 && idx < units.length - 1) {
            v = v.divide(BigDecimal.valueOf(1e4), MC);
            idx++;
        }
        if (idx == units.length - 1 && v.abs().compareTo(BigDecimal.valueOf(1e4)) >= 0) {
            // 超出「载」后回退到科学计数，避免单位表耗尽
            return v.multiply(BigDecimal.valueOf(1e4)).round(new MathContext(4)).toString() + "载";
        }
        return v.setScale(2, RoundingMode.DOWN).stripTrailingZeros().toPlainString() + units[idx];
    }
}
