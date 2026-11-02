-- v3.1 玩法扩充：2 种新药剂 + 3 件新装备 + 2 个新状态（幂等）
USE `gongpu`;

-- ---------- 新药剂 ----------
INSERT INTO `cfg_item` (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`)
VALUES
  ('growth', '修行药剂', '🍵', 'potion', 600, '{"buff":"growth_pot","buffVal":2,"buffDur":120}', '120s 内普攻成长 ×2（打桩冲级利器）', 1, 7),
  ('ironpot', '铁壁药剂', '🛡️', 'potion', 400, '{"buff":"iron_pot","buffVal":1.5,"buffDur":60}', '60s 内物防魔防 ×1.5（Boss 前磕一瓶）', 1, 8)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `price`=VALUES(`price`), `effect_json`=VALUES(`effect_json`), `desc`=VALUES(`desc`), `enabled`=1, `sort_no`=VALUES(`sort_no`);

-- ---------- 新状态（前端效果区显示用） ----------
INSERT INTO `cfg_buff` (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`)
VALUES
  ('growth_pot', '修行药剂', '🍵', 'good', 'passive', '{"growthMul":2}', '普攻成长 ×2', 1, 7),
  ('iron_pot', '铁壁药剂', '🛡️', 'good', 'passive', '{"defMul":1.5}', '物防魔防 ×1.5', 1, 8)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `nature`=VALUES(`nature`), `group_type`=VALUES(`group_type`), `effect_json`=VALUES(`effect_json`), `desc`=VALUES(`desc`), `enabled`=1, `sort_no`=VALUES(`sort_no`);

-- ---------- 新装备（3 件毕业向底子） ----------
INSERT INTO `cfg_equipment_template` (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`)
VALUES
  ('dragon_scale_mail', '龙鳞甲', '🐲', 'chest',   3, 'volcano', '{"PDEF":"18","MDEF":"12","REGEN":"3"}',  12, 60, '毕业胸甲：双防+回血', 1, 30),
  ('thunder_ring',      '雷鸣指环', '⚡', 'ring1',  3, 'volcano', '{"ASPD":"0.2","PATK":"3"}',               12, 60, '攻速流毕业戒指',     1, 31),
  ('holy_necklace',     '圣光项链', '✨', 'necklace', 3, 'snow',   '{"MDEF":"10","MREGEN":"4","MATK":"6"}',  12, 60, '法系毕业项链',       1, 32)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `slot`=VALUES(`slot`), `tier`=VALUES(`tier`), `min_zone`=VALUES(`min_zone`), `base_stats`=VALUES(`base_stats`), `enhance_max`=VALUES(`enhance_max`), `sell_base`=VALUES(`sell_base`), `remark`=VALUES(`remark`), `enabled`=1, `sort_no`=VALUES(`sort_no`);

-- 核对
-- SELECT code,name,price FROM cfg_item WHERE code IN ('growth','ironpot');
-- SELECT code,name,min_zone FROM cfg_equipment_template WHERE code IN ('dragon_scale_mail','thunder_ring','holy_necklace');
-- SELECT code,name FROM cfg_buff WHERE code IN ('growth_pot','iron_pot');
