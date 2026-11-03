-- v3.17 运营统计改终身口径：punches/kills 冗余列此前存「当世值」，每世重置导致
-- 全服 SUM 非单调、日增量被钳零失真。改存终身计数（lifetimePunches/lifetimeKills）：
-- punches 从 JSON 终身字段回填；kills 此前无终身字段，以当前当世击杀为种子起点
-- （运行时 StateCodec 解码也会做同口径种子兜底，此后终身单调累加）。
UPDATE `game_save` SET
  `punches_total` = COALESCE(CAST(JSON_EXTRACT(`state_json`, '$.stats.lifetimePunches') AS DECIMAL(65,0)), 0),
  `kills_total`   = COALESCE(CAST(JSON_EXTRACT(`state_json`, '$.stats.kills') AS DECIMAL(65,0)), 0);
