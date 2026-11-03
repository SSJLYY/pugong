-- ============================================================
-- 第四批第 3 轮：鸿运药剂（收益维度——装备/图纸掉落率 ×1.5，300 秒）
-- 结算接线在 GameEngine.dropLuckMul（逐杀掉落/溢出期望/图纸三处同口径；Boss 必掉不受影响）
-- 幂等：同 code 先删后插，可重复执行
-- ============================================================
USE `gongpu`;

DELETE FROM `cfg_item` WHERE `code` IN ('luck');
INSERT INTO `cfg_item`
  (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('luck', '鸿运药剂', '🍀', 'potion', 500, '{"buff":"luck_pot","buffVal":1.5,"buffDur":300}', '300s 内装备与图纸掉落率 ×1.5', 1, 25);

DELETE FROM `cfg_buff` WHERE `code` IN ('luck_pot');
INSERT INTO `cfg_buff`
  (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('luck_pot', '鸿运', '🍀', 'good', 'passive', '{"dropMul":1.5}', '300s 内装备与图纸掉落率 ×1.5', 1, 23);

-- 验证:
-- SELECT code,name,price FROM cfg_item WHERE code = 'luck';
