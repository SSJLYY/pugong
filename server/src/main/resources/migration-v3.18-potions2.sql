-- ============================================================
-- 第四批第 1 轮：药剂回复系扩充（特大血药 / 大蓝药 / 万灵药剂双回）
-- 幂等：同 code 先删后插，可重复执行（与 v3.14 同口径）
-- ============================================================
USE `gongpu`;

DELETE FROM `cfg_item` WHERE `code` IN ('hp_xl','mp_l','elixir');
INSERT INTO `cfg_item`
  (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('hp_xl',  '特大血药', '❤️', 'potion', 600, '{"healHP":1.0}',                 '瞬回 100% 生命上限',      1, 19),
  ('mp_l',   '大蓝药',   '💙', 'potion', 150, '{"healMP":1.0}',                 '瞬回 100% 蓝量',          1, 20),
  ('elixir', '万灵药剂', '🌈', 'potion', 800, '{"healHP":0.8,"healMP":0.8}',    '瞬回 80% 生命与 80% 蓝量', 1, 21);

-- 验证:
-- SELECT code,name,price FROM cfg_item WHERE code IN ('hp_xl','mp_l','elixir');
