package com.gongpu.web;

import com.gongpu.common.Num;
import com.gongpu.game.GameConst;
import com.gongpu.config.StateCache;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 健康检查 + 前端所需的静态元数据（区域/怪物/技能/药剂/装备底子）。 */
@RestController
@RequestMapping("/api")
public class MetaController {

    private final StateCache cache;
    private final DataSource dataSource;

    public MetaController(StateCache cache, DataSource dataSource) {
        this.cache = cache;
        this.dataSource = dataSource;
    }

    /** 探活结果短缓存：/health 未鉴权且常被监控高频轮询，2 秒内复用同一次探测，免每请求打 Redis+DB。 */
    private volatile Map<String, Object> healthCache;
    private volatile long healthCacheAt;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> cached = healthCache;
        if (cached != null && System.currentTimeMillis() - healthCacheAt < 2000) {
            return cached;
        }
        synchronized (this) {
            if (healthCache != null && System.currentTimeMillis() - healthCacheAt < 2000) {
                return healthCache;
            }
            Map<String, Object> fresh = probeHealth();
            healthCache = fresh;
            healthCacheAt = System.currentTimeMillis();
            return fresh;
        }
    }

    private Map<String, Object> probeHealth() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("redis", cache.ping() ? "up" : "down");
        String db = "down";
        try (Connection c = dataSource.getConnection()) {
            db = c.isValid(2) ? "up" : "down";
        } catch (Exception ignored) {
            // 探活失败即 down
        }
        m.put("mysql", db);
        addDiskWarn(m);
        // 内容版本指纹：部署后核对新内容是否生效（数字与库内 cfg 行数一致才算部署到位）；
        // 探测失败不加字段，绝不拖垮健康检查
        try {
            com.gongpu.cfg.GameData.Snapshot snap = com.gongpu.cfg.GameData.instance().snapshot();
            m.put("content", "items=" + snap.items().size()
                    + ",affixes=" + snap.affixes().size()
                    + ",equipment=" + snap.equipment().size()
                    + ",skills=" + snap.skills().size()
                    + ",crafts=" + snap.crafts().size());
        } catch (RuntimeException ignored) {
            // GameData 未就绪时省略
        }
        return m;
    }

    /**
     * 磁盘水位软告警：工作目录所在盘（136 上 WorkingDirectory=/data/gongpu，与 logs/ 同一分区）
     * 可用空间 <2GB 时追加 "disk":"warn" 与 "usableMB"，充足时不加任何字段（保持既有响应形状）。
     * ok 恒为 true——磁盘紧只提示不判死；探测本身出异常也不能拖垮健康检查。
     */
    private void addDiskWarn(Map<String, Object> m) {
        try {
            long usableMB = new File(".").getAbsoluteFile().getUsableSpace() / (1024 * 1024);
            if (usableMB < 2048) {
                m.put("disk", "warn");
                m.put("usableMB", usableMB);
            }
        } catch (Exception ignored) {
            // 探测失败按无告警处理，别让运维字段影响服务可用性判断
        }
    }

    /**
     * 元数据缓存：内容来自常量表 + GameData 配置快照，按快照对象身份缓存——
     * 运维端 reload() 换快照后自动重建，未换则直接复用（此前每个请求全量重建整棵 Map 树）。
     */
    private volatile Object metaSnapKey;
    private volatile Map<String, Object> metaCache;

    /** 前端启动时拉一次，用于渲染区域/怪物/技能/药剂表。 */
    @GetMapping("/config/meta")
    public Map<String, Object> meta() {
        Object key = com.gongpu.cfg.GameData.instance().snapshot();
        Map<String, Object> cached = metaCache;
        if (cached != null && key == metaSnapKey) {
            return cached;
        }
        synchronized (this) {
            if (metaCache == null || key != metaSnapKey) {
                metaCache = buildMeta();
                metaSnapKey = key;
            }
            return metaCache;
        }
    }

    private Map<String, Object> buildMeta() {
        Map<String, Object> m = new LinkedHashMap<>();

        List<Map<String, Object>> zones = new ArrayList<>();
        for (GameConst.ZoneDef z : GameConst.ZONES.values()) {
            Map<String, Object> zm = new LinkedHashMap<>();
            zm.put("id", z.id());
            zm.put("name", z.name());
            zm.put("emoji", z.emoji());
            zm.put("safe", z.safe());
            zm.put("desc", z.desc());
            zm.put("req", z.req());
            zm.put("hasBoss", z.boss() != null);
            List<Map<String, Object>> mobs = new ArrayList<>();
            for (GameConst.MobDef d : z.mobs()) {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("id", d.id());
                mm.put("name", d.name());
                mm.put("emoji", d.emoji());
                mm.put("maxHP", d.maxHP() == null ? "Infinity" : d.maxHP());
                mm.put("PATK", d.PATK());
                mm.put("MATK", d.MATK());
                mm.put("PDEF", d.PDEF());
                mm.put("MDEF", d.MDEF());
                mm.put("ASPD", d.ASPD());
                mm.put("RNG", d.RNG());
                mm.put("SPD", d.SPD());
                mm.put("gold", d.gold());
                mm.put("desc", d.desc());
                mm.put("tags", d.tags());
                mobs.add(mm);
            }
            zm.put("mobs", mobs);
            zones.add(zm);
        }
        m.put("zones", zones);

        // 怪物词条图鉴（前端图例弹窗用：id/名称/图标/说明，与引擎 MOB_TRAITS 同源）
        List<Map<String, Object>> traits = new ArrayList<>();
        for (GameConst.TraitDef t : GameConst.MOB_TRAITS.values()) {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("id", t.id());
            tm.put("name", t.name());
            tm.put("emoji", t.emoji());
            tm.put("desc", t.desc());
            traits.add(tm);
        }
        m.put("traits", traits);

        List<Map<String, Object>> skills = new ArrayList<>();
        for (GameConst.SkillDef d : com.gongpu.cfg.GameData.instance().skillList()) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("id", d.id());
            sm.put("name", d.name());
            sm.put("emoji", d.emoji());
            sm.put("mp", d.mp());
            sm.put("cd", d.cd());
            sm.put("dur", d.dur());
            sm.put("desc", d.desc());
            skills.add(sm);
        }
        m.put("skills", skills);

        List<Map<String, Object>> passives = new ArrayList<>();
        passives.addAll(com.gongpu.cfg.GameData.instance().passiveList());
        m.put("passives", passives);

        List<Map<String, Object>> potions = new ArrayList<>();
        for (GameConst.PotionDef d : com.gongpu.cfg.GameData.instance().potions()) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("id", d.id());
            pm.put("name", d.name());
            pm.put("emoji", d.emoji());
            pm.put("desc", d.desc());
            pm.put("price", d.price());
            // 商店分组（前端按组渲染）：回复（回血/回蓝）/ 增益（带 buff）/ 功能（解毒等）
            pm.put("group", (d.healHP() > 0 || d.healMP() > 0) ? "heal" : (d.buff() != null ? "buff" : "func"));
            potions.add(pm);
        }
        m.put("potions", potions);

        List<Map<String, Object>> bless = new ArrayList<>();
        for (String k : GameConst.BLESS_ORDER) {
            Map<String, Object> bm = new LinkedHashMap<>();
            bm.put("key", k);
            bm.put("name", GameConst.BLESS_NAME.get(k));
            bm.put("emoji", GameConst.BLESS_EMOJI.get(k));
            bm.put("step", Num.plain(GameConst.BLESS_STEP.get(k)));
            bm.put("cap", GameConst.BLESS_CAP.containsKey(k) ? Num.plain(GameConst.BLESS_CAP.get(k)) : null);
            bless.add(bm);
        }
        m.put("blessings", bless);
        m.put("clearAspd", Num.plain(GameConst.CLEAR_ASPD));
        m.put("bagMax", GameConst.BAG_MAX);
        m.put("offlineCapHours", GameConst.OFFLINE_CAP_HOURS);
        m.put("offlineEff", GameConst.OFFLINE_EFF);
        return m;
    }
}
