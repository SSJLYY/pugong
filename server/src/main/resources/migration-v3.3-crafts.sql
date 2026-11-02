-- ============================================================
-- v3.3 图纸合成种子（DESIGN.md §6/§9）
-- 材料：兽皮(plain)/狼牙(forest)/矿石(mine)/毒腺(swamp)/魔尘(snow)/火晶(volcano)
-- 图纸打怪掉落（Boss 30% / 普通怪 4%）；图纸+材料+金币 → 指定底子装备（词条随机）
-- ============================================================
USE `gongpu`;

INSERT INTO `cfg_craft`
  (`code`, `name`, `emoji`, `product_code`, `mats_json`, `gold_cost`, `need_blueprint`, `enabled`, `sort_no`)
VALUES
  -- 无图纸基础配方（新手材料去向）
  ('c_iron_sword',  '铁剑锻造',     '⚒️', 'iron_sword',    '{"pelt":2}',                      100,  0, 1, 1),
  ('c_hunting_bow', '猎弓制作',     '🏹', 'hunting_bow',   '{"pelt":4,"wolf_fang":2}',         200,  0, 1, 2),
  -- 图纸配方（毕业装）
  ('c_ench_bow',    '附魔弓图纸',   '📐', 'enchanted_bow', '{"ore":6,"wolf_fang":4}',          500,  1, 1, 10),
  ('c_long_spear',  '长枪图纸',     '📐', 'long_spear',    '{"ore":4,"wolf_fang":6}',          400,  1, 1, 11),
  ('c_magic_staff', '法杖图纸',     '📐', 'magic_staff',   '{"toad_gland":5,"wolf_fang":3}',   600,  1, 1, 12),
  ('c_gatling',     '加特林图纸',   '📐', 'gatling',       '{"ore":10,"fire_crystal":2}',      2000, 1, 1, 20),
  ('c_arc_rifle',   '奥术步枪图纸', '📐', 'arc_rifle',     '{"mana_dust":6,"ore":5}',          3000, 1, 1, 21),
  ('c_mana_gatling','魔弹加特林图纸','📐','mana_gatling',  '{"mana_dust":8,"fire_crystal":4}', 5000, 1, 1, 22),
  ('c_scale_mail',  '龙鳞甲图纸',   '📐', 'dragon_scale_mail', '{"fire_crystal":3,"pelt":10}', 4000, 1, 1, 23),
  ('c_thunder_ring','雷鸣指环图纸', '📐', 'thunder_ring',  '{"mana_dust":6,"ore":8}',          3000, 1, 1, 24),
  ('c_holy_neck',   '圣光项链图纸', '📐', 'holy_necklace', '{"mana_dust":5,"wolf_fang":5}',    2500, 1, 1, 25)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `product_code`=VALUES(`product_code`),
  `mats_json`=VALUES(`mats_json`), `gold_cost`=VALUES(`gold_cost`),
  `need_blueprint`=VALUES(`need_blueprint`), `enabled`=1, `sort_no`=VALUES(`sort_no`);
