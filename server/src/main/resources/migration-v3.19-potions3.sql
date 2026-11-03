-- ============================================================
-- 第四批第 2 轮：药剂新维度增益（持续回复 / 射程 / 移速）
-- 面板接线在 PanelCalculator（regen_pot/rng_pot/fleet_pot，同受沉默压制）
-- 幂等：同 code 先删后插，可重复执行（与 v3.14 同口径）
-- ============================================================
USE `gongpu`;

DELETE FROM `cfg_item` WHERE `code` IN ('regen','far','fleet');
INSERT INTO `cfg_item`
  (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('regen', '生生药剂', '🌿', 'potion', 400, '{"buff":"regen_pot","buffVal":25,"buffDur":120}', '120s 内回血 +25/秒', 1, 22),
  ('far',   '远眺药剂', '🔭', 'potion', 350, '{"buff":"rng_pot","buffVal":1,"buffDur":120}',     '120s 内射程 +1',     1, 23),
  ('fleet', '疾行药剂', '👟', 'potion', 300, '{"buff":"fleet_pot","buffVal":40,"buffDur":120}',  '120s 内移速 +40',    1, 24);

DELETE FROM `cfg_buff` WHERE `code` IN ('regen_pot','rng_pot','fleet_pot');
INSERT INTO `cfg_buff`
  (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('regen_pot', '生生', '🌿', 'good', 'passive', '{"regenAdd":25}', '120s 内回血 +25/秒', 1, 20),
  ('rng_pot',   '远眺', '🔭', 'good', 'passive', '{"rngAdd":1}',    '120s 内射程 +1',     1, 21),
  ('fleet_pot', '疾行', '👟', 'good', 'passive', '{"spdAdd":40}',   '120s 内移速 +40',    1, 22);

-- 验证:
-- SELECT code,name,price FROM cfg_item WHERE code IN ('regen','far','fleet');
