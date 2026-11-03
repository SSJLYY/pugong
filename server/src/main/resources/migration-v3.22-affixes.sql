-- ============================================================
-- 第四批第 5 轮：新词条——轻盈（闪避 EVA，此前只有药剂能加）+ 活力（生命上限 maxHP，此前无词条覆盖）
-- 与 GameConst.AFFIXES 镜像同步（seed-v3.sql 口径一致）；幂等：同 code 先删后插
-- ============================================================
USE `gongpu`;

DELETE FROM `cfg_affix` WHERE `code` IN ('lightness','vitality');
INSERT INTO `cfg_affix`
  (`code`, `name`, `stat_key`, `stat_type`, `min_val`, `max_val`, `rarity`, `side_key`, `side_val`, `enabled`, `sort_no`) VALUES
  ('lightness', '轻盈', 'EVA',   'flat', 0.02, 0.10, 'uncommon', NULL, NULL, 1, 15),
  ('vitality',  '活力', 'maxHP', 'flat', 50,   300,  'rare',     NULL, NULL, 1, 16);

-- 验证:
-- SELECT code,name,stat_key FROM cfg_affix WHERE code IN ('lightness','vitality');
