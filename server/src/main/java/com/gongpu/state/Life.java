package com.gongpu.state;

import com.gongpu.common.Num;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 玩家面板（可成长部分）。
 * <p>与旧版 localStorage 存档结构保持兼容，便于迁移老档。</p>
 */
public class Life {

    public BigDecimal maxHP = Num.of(100);
    public BigDecimal curHP = Num.of(100);
    public BigDecimal MP = Num.of(80);
    public BigDecimal curMP = Num.of(80);
    public BigDecimal PATK = Num.of(2);
    public BigDecimal MATK = Num.ZERO;
    public BigDecimal PDEF = Num.of(2);
    public BigDecimal MDEF = Num.ZERO;
    public BigDecimal REGEN = Num.of(2);
    public BigDecimal MREGEN = Num.of(2);

    public BigDecimal get(String key) {
        return switch (key) {
            case "maxHP" -> maxHP;
            case "curHP" -> curHP;
            case "MP" -> MP;
            case "curMP" -> curMP;
            case "PATK" -> PATK;
            case "MATK" -> MATK;
            case "PDEF" -> PDEF;
            case "MDEF" -> MDEF;
            case "REGEN" -> REGEN;
            case "MREGEN" -> MREGEN;
            default -> Num.ZERO;
        };
    }

    public void set(String key, BigDecimal v) {
        switch (key) {
            case "maxHP" -> maxHP = v;
            case "curHP" -> curHP = v;
            case "MP" -> MP = v;
            case "curMP" -> curMP = v;
            case "PATK" -> PATK = v;
            case "MATK" -> MATK = v;
            case "PDEF" -> PDEF = v;
            case "MDEF" -> MDEF = v;
            case "REGEN" -> REGEN = v;
            case "MREGEN" -> MREGEN = v;
            default -> { }
        }
    }

    public static Life fresh() {
        return new Life();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("maxHP", Num.plain(maxHP));
        m.put("curHP", Num.plain(curHP));
        m.put("MP", Num.plain(MP));
        m.put("curMP", Num.plain(curMP));
        m.put("PATK", Num.plain(PATK));
        m.put("MATK", Num.plain(MATK));
        m.put("PDEF", Num.plain(PDEF));
        m.put("MDEF", Num.plain(MDEF));
        m.put("REGEN", Num.plain(REGEN));
        m.put("MREGEN", Num.plain(MREGEN));
        return m;
    }
}
