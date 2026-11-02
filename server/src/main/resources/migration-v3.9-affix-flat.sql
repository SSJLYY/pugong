-- ============================================================
-- v3.9:词条池百分比改直接数值(玩家要求:回血/物攻等词条直接加面板,前期可感)
-- 幂等:UPDATE 重复执行结果一致。旧档已生成的 pct 词条按原 type 兼容生效。
-- 对应 GameConst.AFFIXES 与 seed-v3.sql 同步修改。
-- ============================================================
USE `gongpu`;

UPDATE `cfg_affix` SET `stat_type`='flat', `min_val`=4,  `max_val`=16 WHERE `code`='sharp'         AND `stat_type`='pct';
UPDATE `cfg_affix` SET `stat_type`='flat', `min_val`=6,  `max_val`=24 WHERE `code`='magic_power'    AND `stat_type`='pct';
UPDATE `cfg_affix` SET `stat_type`='flat', `min_val`=1,  `max_val`=4  WHERE `code`='regen_vigorous' AND `stat_type`='pct';
UPDATE `cfg_affix` SET `stat_type`='flat', `min_val`=10, `max_val`=30 WHERE `code`='nimble'         AND `stat_type`='pct';
UPDATE `cfg_affix` SET `stat_type`='flat', `min_val`=6,  `max_val`=20 WHERE `code`='heavy_armor'    AND `stat_type`='pct';
UPDATE `cfg_affix` SET `stat_type`='flat', `min_val`=6,  `max_val`=18 WHERE `code`='magic_weave'    AND `stat_type`='pct';
UPDATE `cfg_affix` SET `stat_type`='flat', `min_val`=1,  `max_val`=3  WHERE `code`='blue_spring'    AND `stat_type`='pct';
