-- v3.16 运营统计冗余列：StatDailyAggregator 改走 SUM 聚合，不再全表解析 state_json。
-- 四列由 SaveService.persist 与 JSON 内 stats 同步维护；本迁移给存量档从 JSON 回填一次。
ALTER TABLE `game_save`
  ADD COLUMN `punches_total`  DECIMAL(65,0) DEFAULT 0 COMMENT '冗余累计出拳（运营统计 SUM 用）' AFTER `alive_seconds`,
  ADD COLUMN `kills_total`    DECIMAL(65,0) DEFAULT 0 COMMENT '冗余累计击杀（运营统计 SUM 用）' AFTER `punches_total`,
  ADD COLUMN `deaths_total`   BIGINT NOT NULL DEFAULT 0 COMMENT '冗余累计死亡（运营统计 SUM 用）' AFTER `kills_total`,
  ADD COLUMN `rebirths_total` BIGINT NOT NULL DEFAULT 0 COMMENT '冗余累计转生（运营统计 SUM 用）' AFTER `deaths_total`;

UPDATE `game_save` SET
  `punches_total`  = COALESCE(CAST(JSON_EXTRACT(`state_json`, '$.stats.punches') AS DECIMAL(65,0)), 0),
  `kills_total`    = COALESCE(CAST(JSON_EXTRACT(`state_json`, '$.stats.kills') AS DECIMAL(65,0)), 0),
  `deaths_total`   = COALESCE(CAST(JSON_EXTRACT(`state_json`, '$.stats.deaths') AS SIGNED), 0),
  `rebirths_total` = COALESCE(CAST(JSON_EXTRACT(`state_json`, '$.stats.rebirths') AS SIGNED), 0);
