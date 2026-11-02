package com.gongpu.state;

import com.gongpu.common.Num;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 固定面板：攻速 / 射程 / 移速 / 暴击 / 命中 / 闪避。
 * <p>这些量在后期会突破 double 上限（攻速目标 1e44），但攻速本身只需要
 * 参与「每秒出拳次数」的乘法，因此用 BigDecimal 存储，避免精度与溢出。</p>
 */
public class Fixed {

    public java.math.BigDecimal ASPD = Num.of(2.0);
    public java.math.BigDecimal RNG = Num.of(1);
    public java.math.BigDecimal SPD = Num.of(100);
    public java.math.BigDecimal CRIT = Num.of(0.05);
    public java.math.BigDecimal CRITD = Num.of(1.5);
    public java.math.BigDecimal ACC = Num.of(0.95);
    public java.math.BigDecimal EVA = Num.of(0);

    public java.math.BigDecimal get(String key) {
        return switch (key) {
            case "ASPD" -> ASPD;
            case "RNG" -> RNG;
            case "SPD" -> SPD;
            case "CRIT" -> CRIT;
            case "CRITD" -> CRITD;
            case "ACC" -> ACC;
            case "EVA" -> EVA;
            default -> Num.ZERO;
        };
    }

    public void add(String key, java.math.BigDecimal v) {
        switch (key) {
            case "ASPD" -> ASPD = Num.add(ASPD, v);
            case "RNG" -> RNG = Num.add(RNG, v);
            case "SPD" -> SPD = Num.add(SPD, v);
            case "CRIT" -> CRIT = Num.min(Num.of(1), Num.add(CRIT, v));
            case "CRITD" -> CRITD = Num.add(CRITD, v);
            case "ACC" -> ACC = Num.min(Num.of(1), Num.add(ACC, v));
            case "EVA" -> EVA = Num.min(Num.of(0.95), Num.add(EVA, v));
            default -> { }
        }
    }

    public static Fixed fresh() {
        return new Fixed();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ASPD", Num.plain(ASPD));
        m.put("RNG", Num.plain(RNG));
        m.put("SPD", Num.plain(SPD));
        m.put("CRIT", Num.plain(CRIT));
        m.put("CRITD", Num.plain(CRITD));
        m.put("ACC", Num.plain(ACC));
        m.put("EVA", Num.plain(EVA));
        return m;
    }
}
