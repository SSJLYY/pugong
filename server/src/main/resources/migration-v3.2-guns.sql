-- ============================================================
-- v3.2 枪械系统：三线武器（物理弹 / 物理附魔弹 / 纯魔法弹）
-- 纯魔法弹：base_stats 带 BULLET_MP 标记，每发子弹耗蓝；无蓝哑火
-- 加特林：攻速流终极浪漫，一发子弹=一次普攻
-- ============================================================
USE `gongpu`;

INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`)
VALUES
  -- 物理弹线（纯 PATK，攻速递增，一发=一次普攻）
  ('flintlock',     '火铳',     '🔫', 'bow',   1, 'forest',  '{"PATK":"9","RNG":"6","ASPD":"1.2"}',                     10, 30, '物理弹·早期射速代表',     1, 40),
  ('rifle',         '步枪',     '🔫', 'bow',   2, 'mine',    '{"PATK":"16","RNG":"9","ASPD":"2.5"}',                    10, 40, '物理弹·中坚',             1, 41),
  ('gatling',       '加特林',   '🔫', 'bow',   3, 'snow',    '{"PATK":"12","RNG":"7","ASPD":"8"}',                      12, 80, '物理弹·射速的终极浪漫',    1, 42),
  -- 物理附魔弹线（PATK+MATK 双轨，攻速适中，不耗蓝）
  ('enchant_gun',   '附魔火铳', '🔫', 'bow',   2, 'mine',    '{"PATK":"10","MATK":"10","RNG":"8","ASPD":"1.5"}',        10, 45, '物理附魔弹·双修',           1, 43),
  ('arc_rifle',     '奥术步枪', '🔫', 'bow',   3, 'snow',    '{"PATK":"14","MATK":"14","RNG":"9","ASPD":"2"}',          12, 70, '物理附魔弹·魔像天敌',       1, 44),
  -- 纯魔法弹线（纯 MATK，攻速高，每发耗蓝 BULLET_MP）
  ('mana_pistol',   '魔弹手枪', '🔫', 'bow',   2, 'swamp',   '{"MATK":"18","RNG":"6","ASPD":"3","BULLET_MP":"1"}',       10, 45, '纯魔法弹·每发耗蓝1',        1, 45),
  ('mana_gatling',  '魔弹加特林','🔫', 'bow',  3, 'volcano', '{"MATK":"15","RNG":"7","ASPD":"10","BULLET_MP":"1"}',      12, 90, '纯魔法弹·每发耗蓝1·蓝海豪赌', 1, 46)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `slot`=VALUES(`slot`), `tier`=VALUES(`tier`),
  `min_zone`=VALUES(`min_zone`), `base_stats`=VALUES(`base_stats`), `enhance_max`=VALUES(`enhance_max`),
  `sell_base`=VALUES(`sell_base`), `remark`=VALUES(`remark`), `enabled`=1, `sort_no`=VALUES(`sort_no`);
