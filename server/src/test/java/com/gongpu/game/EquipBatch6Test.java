package com.gongpu.game;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第六批装备扩充（第 16~20 轮）：逐轮断言新装备行存在于整合 SQL
 * （行格式与 DataParityTest 的装备解析口径一致），且分区/部位符合补缺目标。
 */
class EquipBatch6Test {

    private static final Pattern ROW = Pattern.compile(
            "\\('([a-z0-9_]+)',\\s*'[^']*',\\s*'[^']*',\\s*'(weapon|offhand|bow|head|chest|legs|boots|ring1|ring2|necklace)',\\s*\\d+,\\s*'(plain|forest|mine|swamp|snow|volcano)',\\s*'(\\{[^']*\\})'");

    private String sql() throws Exception {
        Path p = Path.of("src/main/resources/gongpu-full.sql");
        if (!Files.exists(p)) {
            p = Path.of("server/src/main/resources/gongpu-full.sql");
        }
        return Files.readString(p);
    }

    private void assertEquip(String code, String slot, String zone) throws Exception {
        Matcher m = ROW.matcher(sql());
        while (m.find()) {
            if (m.group(1).equals(code)) {
                assertTrue(m.group(2).equals(slot) && m.group(3).equals(zone),
                        code + " 应为 " + zone + "/" + slot + "，实际 " + m.group(3) + "/" + m.group(2));
                return;
            }
        }
        throw new AssertionError(code + " 不在装备表中");
    }

    @Test
    void round16BambooBow() throws Exception {
        assertEquip("bamboo_bow", "bow", "plain");
    }

    @Test
    void round17VineArmor() throws Exception {
        assertEquip("vine_armor", "chest", "forest");
    }

    @Test
    void round18MinerHelm() throws Exception {
        assertEquip("miner_helm", "head", "mine");
    }

    @Test
    void round19MarshLeggings() throws Exception {
        assertEquip("marsh_leggings", "legs", "swamp");
    }
}
