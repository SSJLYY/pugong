-- ============================================================
-- 增量脚本：v2.0 存档快照（硬核死亡制）
-- 说明：state_json = 当前活着的滚动状态；snapshot_json = 存档点快照
-- ============================================================
USE `gongpu`;

ALTER TABLE `game_save`
  ADD COLUMN `snapshot_json` LONGTEXT NULL COMMENT '存档点快照（死亡回档读它）' AFTER `state_json`,
  ADD COLUMN `saved_at` DATETIME(6) NULL COMMENT '存档点时间' AFTER `snapshot_json`;
