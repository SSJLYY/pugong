-- ============================================================
-- v3.3 P1 烹饪（DESIGN.md §8：食材 → 料理）
-- 产出走 item: 前缀（药剂通道）：兔肉料理/狼肉料理/烤野猪肉
-- 无需图纸（基础烹饪）
-- ============================================================
USE `gongpu`;

INSERT INTO `cfg_craft`
  (`code`, `name`, `emoji`, `product_code`, `mats_json`, `gold_cost`, `need_blueprint`, `enabled`, `sort_no`)
VALUES
  ('cook_rabbit', '烤兔肉',   '🍖', 'item:food',     '{"pelt":3}',               10,  0, 1, 3),
  ('cook_boar',   '炖野猪肉', '🍲', 'item:hp_s',     '{"pelt":5}',               50,  0, 1, 4),
  ('cook_wolf',   '狼肉大餐', '🥘', 'item:hp_l',     '{"pelt":6,"wolf_fang":2}', 120, 0, 1, 5)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `product_code`=VALUES(`product_code`),
  `mats_json`=VALUES(`mats_json`), `gold_cost`=VALUES(`gold_cost`),
  `need_blueprint`=VALUES(`need_blueprint`), `enabled`=1, `sort_no`=VALUES(`sort_no`);
