package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 枪械三线(物理弹/物理附魔弹/纯魔法弹)行为锁(设计 §7,v3.2 枪械系统):
 * <ul>
 *   <li>BULLET_MP 从武器 base_stats 进面板({@link PanelCalculator.Panel#BULLETMP()});</li>
 *   <li>纯魔法弹(BULLET_MP&gt;0)每发子弹耗蓝,无蓝整体哑火(本帧普攻全不结算),蓝回复后自动恢复;</li>
 *   <li>物理弹(BULLET_MP=0)不吃 MP,0 蓝照常射击;</li>
 *   <li>加特林 ASPD 8 / 魔弹加特林 ASPD 10(模板落在 migration-v3.2-guns.sql,测试直读该种子)。</li>
 * </ul>
 * <p>战斗一律经 {@link GameEngine#advance} 驱动(与生产同路径),村口木桩(无限血)便于只观察发数。</p>
 */
class GunplayTest {

    @BeforeAll
    static void initGameData() {
        // null 仓库 → load() 失败回落空快照 → GameConst 兜底(与 EquipFactoryTest 同法)
        new GameData(null, null, null, null, null, null, null);
    }

    /** 按模板 base_stats JSON 造一把枪(与生产 GameData.equipmentPool 的 base 同构)。 */
    private PlayerState.Item gun(String baseJson) throws Exception {
        PlayerState.Item it = new PlayerState.Item();
        it.uid = 1;
        it.name = "测试枪";
        it.emoji = "🔫";
        it.slot = "bow";
        Map<String, Object> stats = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                baseJson, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() { });
        for (Map.Entry<String, Object> e : stats.entrySet()) {
            it.base.put(e.getKey(), Num.parse(String.valueOf(e.getValue()), Num.ZERO));
        }
        return it;
    }

    private PlayerState villagerWith(String baseJson) throws Exception {
        PlayerState s = NewState.create();   // 村口木桩:无限血,只观察发数与成长
        s.equipped.put("bow", gun(baseJson));
        return s;
    }

    private static final String MANA_GUN = "{\"MATK\":\"18\",\"RNG\":\"6\",\"ASPD\":\"3\",\"BULLET_MP\":\"1\"}";
    private static final String PHYSICAL_GUN = "{\"PATK\":\"12\",\"RNG\":\"7\",\"ASPD\":\"8\"}";

    /** BULLET_MP 必须经武器 base_stats 进面板;下枪后回 0。 */
    @Test
    void bulletMpEntersPanelFromWeaponBaseStats() throws Exception {
        PlayerState s = villagerWith(MANA_GUN);
        assertEquals(0, PanelCalculator.of(s).BULLETMP().compareTo(Num.of(1)),
                "武器 base_stats 的 BULLET_MP 必须进面板(纯魔法弹每发耗蓝的依据)");

        s.equipped.remove("bow");
        assertEquals(0, PanelCalculator.of(s).BULLETMP().compareTo(Num.ZERO),
                "不持枪/物理弹武器时面板 BULLET_MP 应为 0");
    }

    /** 纯魔法弹 0 蓝哑火:本帧发数清零,普攻成长/出拳数一并不结算。 */
    @Test
    void magicBulletOutOfManaMutesWholeFrame() throws Exception {
        PlayerState s = villagerWith(MANA_GUN);
        s.life.curMP = Num.ZERO;
        BigDecimal hpBefore = s.life.maxHP;
        BigDecimal punchesBefore = s.stats.punches;

        long t = System.currentTimeMillis();
        s.lastTickAt = t - 5000;
        GameEngine.AdvanceResult r = GameEngine.advance(s, t, 1.0, false);

        assertEquals("0", r.punches(), "纯魔法弹无蓝应整体哑火(本帧 0 发,一发都打不起)");
        assertEquals(0, s.stats.punches.compareTo(punchesBefore), "哑火帧不得累积出拳数");
        assertEquals(0, s.life.maxHP.compareTo(hpBefore), "哑火帧不得成长(普攻成长挂在本帧发数上)");
    }

    /** 哑火帧蓝回复照常:下一帧有蓝即自动恢复射击(每发耗蓝 BULLET_MP×发数)。 */
    @Test
    void manaRegenRestoresFireNextFrame() throws Exception {        PlayerState s = villagerWith(MANA_GUN);
        s.life.curMP = Num.ZERO;

        // 第一帧:5 秒,哑火;帧内 regenTick 回蓝 MREGEN(2/s)×5 = 10
        long t1 = System.currentTimeMillis();
        s.lastTickAt = t1 - 5000;
        GameEngine.AdvanceResult r1 = GameEngine.advance(s, t1, 1.0, false);
        assertEquals("0", r1.punches(), "第一帧无蓝应哑火");
        assertEquals(0, s.life.curMP.compareTo(Num.of(10)),
                "哑火帧蓝回复照常(2/s×5s=10),下一帧才有弹药");

        // 第二帧:3 秒,面板 ASPD=2+3=5 → 应打 15 发,蓝只够 10 发 → 打 10 发耗尽
        long t2 = System.currentTimeMillis();
        s.lastTickAt = t2 - 3000;
        BigDecimal hpBefore = s.life.maxHP;
        GameEngine.AdvanceResult r2 = GameEngine.advance(s, t2, 1.0, false);

        assertEquals("10", r2.punches(), "蓝回复后应自动恢复射击,发数被可负担弹药钳到 10");
        assertEquals(0, s.life.curMP.compareTo(Num.of(6)),
                "10 发耗尽全部蓝(10-10=0),帧末 regenTick 再回 2×3=6");
        assertTrue(Num.gt(s.life.maxHP, hpBefore), "恢复射击后普攻成长随之恢复");
    }

    /** 哑火帧的零头蓝(0<蓝<一发耗蓝)必须保留——清零等于把玩家白攒的蓝蒸发。 */
    @Test
    void jamFrameKeepsDustMana() throws Exception {
        PlayerState s = villagerWith(MANA_GUN);   // BULLET_MP=1
        s.life.curMP = new BigDecimal("0.5");     // 不足一发
        long t = System.currentTimeMillis();
        s.lastTickAt = t - 5000;
        GameEngine.AdvanceResult r = GameEngine.advance(s, t, 1.0, false);
        assertEquals("0", r.punches(), "不足一发应哑火");
        // 帧内顺序:弹药判定先于 regenTick → 判定时 0.5 不足一发哑火,帧末回蓝 2/s×5s=10 → 10.5
        assertEquals(0, s.life.curMP.compareTo(new BigDecimal("10.5")),
                "哑火帧零头蓝不得被清零(0.5 保留 + 回蓝 10 = 10.5)");
    }

    /** 物理弹(BULLET_MP=0)不吃 MP:0 蓝照常全额射击。 */
    @Test
    void physicalBulletIgnoresMana() throws Exception {
        PlayerState s = villagerWith(PHYSICAL_GUN);
        s.life.curMP = Num.ZERO;

        long t = System.currentTimeMillis();
        s.lastTickAt = t - 1000;
        GameEngine.AdvanceResult r = GameEngine.advance(s, t, 1.0, false);

        assertEquals("10", r.punches(), "物理弹 0 蓝照常射击(面板 ASPD 2+8=10,1 秒 10 发)");
        // 物理弹不耗蓝:帧末蓝量只有 regenTick 的自然回复(MREGEN 2/s × 1s = 2),没有弹药扣减
        assertEquals(0, s.life.curMP.compareTo(Num.of(2)), "物理弹不耗蓝,蓝量只来自自然回复");
    }

    /**
     * 模板锁:加特林 ASPD 8(物理弹线)/魔弹加特林 ASPD 10(纯魔法弹线)。
     * 模板只存在于 migration-v3.2-guns.sql(cfg 表种子),测试直读该种子逐行解析 base_stats。
     */
    @Test
    void gatlingTemplateAspdAndBulletMpLocked() throws Exception {
        Map<String, BigDecimal> gatling = templateBaseStats("gatling");
        assertEquals(0, gatling.get("ASPD").compareTo(Num.of(8)), "加特林 ASPD 必须 = 8");
        assertFalse(gatling.containsKey("BULLET_MP"), "物理加特林不耗蓝(物理弹线)");

        Map<String, BigDecimal> manaGatling = templateBaseStats("mana_gatling");
        assertEquals(0, manaGatling.get("ASPD").compareTo(Num.of(10)), "魔弹加特林 ASPD 必须 = 10");
        assertEquals(0, manaGatling.get("BULLET_MP").compareTo(Num.of(1)), "魔弹加特林必须带 BULLET_MP=1");

        Map<String, BigDecimal> manaPistol = templateBaseStats("mana_pistol");
        assertEquals(0, manaPistol.get("BULLET_MP").compareTo(Num.of(1)), "魔弹手枪必须带 BULLET_MP=1");
        Map<String, BigDecimal> enchantGun = templateBaseStats("enchant_gun");
        assertFalse(enchantGun.containsKey("BULLET_MP"), "物理附魔弹线不耗蓝(双轨 PATK+MATK)");
        assertTrue(enchantGun.containsKey("MATK") && enchantGun.containsKey("PATK"),
                "物理附魔弹线必须双修(PATK+MATK 底子)");
    }

    /** 从 migration-v3.2-guns.sql 里取指定 code 枪械模板的 base_stats JSON 并解析。 */
    private Map<String, BigDecimal> templateBaseStats(String code) throws Exception {
        String sql;
        try (InputStream in = GunplayTest.class.getResourceAsStream("/migration-v3.2-guns.sql")) {
            assertNotNull(in, "种子 migration-v3.2-guns.sql 必须在 classpath 上(枪械模板唯一来源)");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String line : sql.split("\r?\n")) {
            if (line.contains("('" + code + "',")) {
                Matcher m = Pattern.compile("\\{[^}]*\\}").matcher(line);
                assertTrue(m.find(), "模板行缺 base_stats JSON:" + line);
                Map<String, Object> stats = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        m.group(), new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() { });
                Map<String, BigDecimal> out = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : stats.entrySet()) {
                    out.put(e.getKey(), new BigDecimal(String.valueOf(e.getValue())));
                }
                return out;
            }
        }
        throw new AssertionError("种子中找不到枪械模板:" + code);
    }
}
