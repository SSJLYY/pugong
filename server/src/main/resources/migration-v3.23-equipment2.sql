-- ============================================================
-- 第四批第 6 轮：装备区域补缺 I——沼泽（全游戏仅 1 件）与火山（无近战武器/头盔）
-- 数值口径对齐同 tier 既有装备（t2 武器主属性 18~20、t3 30 上下）；幂等：同 code 先删后插
-- ============================================================
USE `gongpu`;

DELETE FROM `cfg_equipment_template` WHERE `code` IN
  ('marsh_fang','marsh_robe','marsh_charm','lava_blade','lava_helm');
INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('marsh_fang',  '毒牙匕首', '🗡️', 'weapon',   2, 'swamp',   '{"PATK":"14","MATK":"8","ASPD":"0.1"}',        10, 45, '沼泽物法双修', 1, 37),
  ('marsh_robe',  '沼泽法袍', '🥻', 'chest',    2, 'swamp',   '{"MDEF":"12","REGEN":"2"}',                     10, 45, '瘴气不侵',     1, 38),
  ('marsh_charm', '瘴气护符', '🧿', 'necklace', 2, 'swamp',   '{"MATK":"6","MREGEN":"2"}',                       10, 45, '法力瘴泉',     1, 39),
  ('lava_blade',  '熔岩巨剑', '🌋', 'weapon',   3, 'volcano', '{"PATK":"34","ASPD":"-0.2","RNG":"2"}',          10, 60, '火山重剑',     1, 40),
  ('lava_helm',   '炎魔之盔', '⛑️', 'head',     3, 'volcano', '{"PDEF":"10","MDEF":"8","REGEN":"1"}',            10, 60, '炎魔覆面',     1, 41);

-- 验证:
-- SELECT code,name,slot,min_zone FROM cfg_equipment_template WHERE min_zone IN ('swamp','volcano');
