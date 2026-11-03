package com.gongpu.web;

import com.gongpu.common.Num;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * 经济动作审计（第七批）：一切经 GameController.run 的「带标签」动作，
 * 只要金币发生变动就记一行结构化日志（独立 logger 名 economy-audit，可单独切文件/检索）。
 * 用途：金币异常增减时能按 uid+动作回溯，不必猜是刷的还是打的。
 * 纯轮询帧（无动作）不记——挂机击杀金币走量太大且有排行榜/统计兜底。
 */
public final class EconomyAudit {

    private static final Logger log = LoggerFactory.getLogger("economy-audit");

    private EconomyAudit() {
    }

    /** 审计行格式（单测锁形状）：uid=.. slot=.. action=.. delta=+N gold=余额。 */
    static String line(long uid, int slot, String action, BigDecimal before, BigDecimal after) {
        BigDecimal delta = after.subtract(before);
        String signed = (delta.signum() >= 0 ? "+" : "") + Num.plain(delta);
        return "uid=" + uid + " slot=" + slot + " action=" + action
                + " delta=" + signed + " gold=" + Num.plain(after);
    }

    /** 金币无变动不记（失败动作/零价操作不刷屏）。 */
    public static void record(long uid, int slot, String action, BigDecimal before, BigDecimal after) {
        if (before == null || after == null || before.compareTo(after) == 0) {
            return;
        }
        log.info("{}", line(uid, slot, action, before, after));
    }
}
