-- ============================================================
-- v3.3 委托系统种子（DESIGN.md §5 冒险公会）
-- 属性门槛接取，击杀交付；奖励金币+药剂（速度药剂走委托产出，对齐设计 §8）
-- ============================================================
USE `gongpu`;

INSERT INTO `cfg_quest`
  (`code`, `name`, `emoji`, `desc`, `target_zone`, `req_json`, `target_kills`, `reward_gold`, `reward_item`, `reward_item_count`, `repeatable`, `enabled`, `sort_no`)
VALUES
  ('clear_rabbit', '清剿野兔',   '📜', '平原的野兔泛滥了，替公会清理一批',     'plain',   '{"maxHP":500}',                    5,  80,   'hp_s',    2, 1, 1, 1),
  ('boar_hunt',    '狩猎野猪',   '🐗', '野猪拱了村口的菜地，讨个说法',         'plain',   '{"maxHP":1000,"PDEF":3}',         8,  150,  'hp_s',    3, 1, 1, 2),
  ('wolf_pelt',    '收集狼皮',   '🐺', '狼群夜袭羊圈，公会高价收购狼皮',       'forest',  '{"maxHP":3000,"PDEF":5}',         10, 260,  'mp',      3, 1, 1, 3),
  ('bandit_raid',  '剿灭山贼',   '🗡️', '山贼劫了商队，会里悬赏缉凶',           'forest',  '{"maxHP":5000,"PDEF":8}',         12, 380,  'cure',    2, 1, 1, 4),
  ('mine_guard',   '矿洞护卫',   '⛏️', '护送矿工下矿，清一路石魔像',           'mine',    '{"maxHP":12000}',                 12, 500,  'spd',     1, 1, 1, 5),
  ('swamp_clean',  '沼泽除害',   '🐸', '毒蟾的毒液污染了水源',                 'swamp',   '{"maxHP":40000,"REGEN":50}',      15, 750,  'growth',  1, 1, 1, 6),
  ('snow_hunt',    '雪山狩猎',   '🏔️', '雪人皮毛是上等护甲材料',               'snow',    '{"maxHP":100000,"MDEF":30}',      15, 1100, 'ironpot', 1, 1, 1, 7),
  ('volcano_final','火山斩魔',   '🌋', '炎魔不除，大陆永无宁日',               'volcano', '{"maxHP":300000,"PDEF":80,"MDEF":80}', 20, 2000, 'growth', 2, 1, 1, 8),
  ('guild_init',   '入会试炼',   '🎖️', '新人的入会考核：证明你的拳头够硬',     'plain',   '{"maxHP":500}',                    3,  300,  'hp_s',    5, 0, 1, 0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `desc`=VALUES(`desc`), `target_zone`=VALUES(`target_zone`),
  `req_json`=VALUES(`req_json`), `target_kills`=VALUES(`target_kills`), `reward_gold`=VALUES(`reward_gold`),
  `reward_item`=VALUES(`reward_item`), `reward_item_count`=VALUES(`reward_item_count`),
  `repeatable`=VALUES(`repeatable`), `enabled`=1, `sort_no`=VALUES(`sort_no`);
