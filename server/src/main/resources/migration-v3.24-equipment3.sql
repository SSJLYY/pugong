-- ============================================================
-- 第四批第 7 轮：装备区域补缺 II——雪原无腿/靴、火山无副手、沼泽无靴
-- 幂等：同 code 先删后插（与第 6 轮同口径）
-- ============================================================
USE `gongpu`;

DELETE FROM `cfg_equipment_template` WHERE `code` IN
  ('frost_greaves','snow_boots','lava_aegis','marsh_boots');
INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('frost_greaves', '冰霜护腿', '🦵', 'legs',    3, 'snow',    '{"PDEF":"12","SPD":"5"}',                    10, 60, '踏雪无痕', 1, 42),
  ('snow_boots',    '雪原长靴', '🥾', 'boots',   3, 'snow',    '{"SPD":"30","MDEF":"4"}',                      10, 60, '雪原疾行', 1, 43),
  ('lava_aegis',    '熔火壁垒', '🛡️', 'offhand', 3, 'volcano', '{"PDEF":"16","MDEF":"6","SPD":"-5"}',          10, 60, '熔火之壁', 1, 44),
  ('marsh_boots',   '瘴气软靴', '👢', 'boots',   2, 'swamp',   '{"SPD":"20","REGEN":"1"}',                     10, 45, '泥沼轻身', 1, 45);

-- 验证:
-- SELECT min_zone, COUNT(*) FROM cfg_equipment_template WHERE enabled=1 GROUP BY min_zone;
