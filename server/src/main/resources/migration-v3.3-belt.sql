-- ============================================================
-- v3.3 P1 补充：负重腰带（DESIGN.md §6.4 经典取舍款）
-- 背包 +20 格 / -3 移速
-- ============================================================
USE `gongpu`;

INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`)
VALUES
  ('load_belt', '负重腰带', '🎽', 'legs', 1, 'forest',
   '{"BAG_CAP":"20","SPD":"-3"}', 10, 25, '背包 +20 格 · 移速 -3（后勤流取舍款）', 1, 50)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `slot`=VALUES(`slot`), `tier`=VALUES(`tier`),
  `min_zone`=VALUES(`min_zone`), `base_stats`=VALUES(`base_stats`), `enhance_max`=VALUES(`enhance_max`),
  `sell_base`=VALUES(`sell_base`), `remark`=VALUES(`remark`), `enabled`=1, `sort_no`=VALUES(`sort_no`);
