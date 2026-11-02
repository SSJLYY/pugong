-- ============================================================
-- 《普攻永久增加生命上限》v3.0 配置数据种子
-- 数据来源：GameConst.java / EquipFactory.java（迁移硬编码数值）
-- 幂等：可重复执行（先 DELETE 再 INSERT）
-- MySQL 8.0 / utf8mb4
-- ============================================================

USE `gongpu`;

-- ============================================================
-- 1. 装备主表 cfg_equipment_template（25 行 = 11 武器/弓 + 14 防具）
-- 来源：EquipFactory.WEAPONS + EquipFactory.ARMOR
-- ============================================================
DELETE FROM `cfg_equipment_template`;
INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('wood_stick',        '新手木棍',  '🥢', 'weapon',    0, 'plain',  '{"PATK":"2","RNG":"1"}',                              10, 20, '白给',             1, 1),
  ('rabbit_bone_dagger','兔骨匕首',  '🦴', 'weapon',    0, 'plain',  '{"PATK":"2","ASPD":"0.15","RNG":"1"}',                10, 20, '攻速启蒙',         1, 2),
  ('iron_sword',        '铁剑',      '🗡️', 'weapon',    1, 'forest', '{"PATK":"8","RNG":"1"}',                              10, 20, '标准',             1, 3),
  ('dagger',            '匕首',      '🔪', 'weapon',    1, 'forest', '{"PATK":"4","ASPD":"0.3","RNG":"1"}',                 10, 20, '攻速流',           1, 4),
  ('light_crossbow',    '轻弩',      '🎯', 'bow',       1, 'forest', '{"PATK":"4","ASPD":"-0.05","RNG":"4"}',               10, 20, '新手远程',         1, 5),
  ('long_spear',        '长枪',      '🔱', 'weapon',    2, 'mine',   '{"PATK":"12","RNG":"3"}',                             10, 20, '近战手长者',       1, 6),
  ('magic_staff',       '法杖',      '🪄', 'weapon',    2, 'mine',   '{"MATK":"20","RNG":"6"}',                             10, 20, '纯魔路线',         1, 7),
  ('great_sword',       '双手大剑',  '⚔️', 'weapon',    3, 'snow',   '{"PATK":"30","ASPD":"-0.3","RNG":"2"}',               10, 20, '输出暴增成长变慢', 1, 8),
  ('hunting_bow',       '猎弓',      '🏹', 'bow',       1, 'forest', '{"PATK":"10","RNG":"5","ASPD":"-0.1"}',               10, 20, '远程入门',         1, 9),
  ('long_bow',          '长弓',      '🏹', 'bow',       2, 'mine',   '{"PATK":"18","RNG":"7","ASPD":"-0.2"}',               10, 20, '风筝核心',         1, 10),
  ('enchanted_bow',     '附魔弓',    '🏹', 'bow',       3, 'snow',   '{"PATK":"12","MATK":"12","RNG":"8","ASPD":"-0.2"}',   10, 20, '物魔双修',         1, 11),
  ('wood_shield',       '木盾',      '🛡️', 'offhand',   0, 'plain',  '{"PDEF":"4","SPD":"-5"}',                             10, 20, '皇上点名款',       1, 12),
  ('tower_shield',      '塔盾',      '🛡️', 'offhand',   2, 'mine',   '{"PDEF":"15","SPD":"-20"}',                            10, 20, '乌龟流',           1, 13),
  ('leather_cap',       '皮帽',      '🎩', 'head',      0, 'plain',  '{"PDEF":"2"}',                                         10, 20, NULL,              1, 14),
  ('iron_helmet',       '铁盔',      '⛑️', 'head',      1, 'forest', '{"PDEF":"5","MDEF":"2"}',                             10, 20, NULL,              1, 15),
  ('leather_armor',     '皮甲',      '🥋', 'chest',     0, 'plain',  '{"PDEF":"3","SPD":"3"}',                              10, 20, '风筝流',           1, 16),
  ('chain_mail',        '锁子甲',    '👕', 'chest',     2, 'mine',   '{"PDEF":"10","SPD":"-5"}',                             10, 20, NULL,              1, 17),
  ('leather_pants',     '皮裤',      '👖', 'legs',      0, 'plain',  '{"PDEF":"2","SPD":"1"}',                              10, 20, NULL,              1, 18),
  ('iron_greaves',      '铁腿甲',    '🦵', 'legs',      2, 'mine',   '{"PDEF":"6","SPD":"-3"}',                              10, 20, NULL,              1, 19),
  ('straw_sandals',     '草鞋',      '👟', 'boots',     0, 'plain',  '{"SPD":"5"}',                                          10, 20, NULL,              1, 20),
  ('swift_boots',       '疾风靴',    '🥾', 'boots',     1, 'forest', '{"SPD":"15"}',                                         10, 20, '跑图逃跑',         1, 21),
  ('copper_ring',       '铜戒',      '💍', 'ring1',     0, 'plain',  '{"PATK":"1"}',                                         10, 20, NULL,              1, 22),
  ('hummingbird_ring',  '蜂鸟戒指',  '💍', 'ring1',     3, 'snow',   '{"ASPD":"0.1"}',                                       10, 20, '最稀有掉落',       1, 23),
  ('bone_necklace',     '骨链',      '📿', 'necklace',  0, 'plain',  '{"PDEF":"1","MDEF":"1"}',                             10, 20, NULL,              1, 24),
  ('magic_necklace',    '魔力项链',  '📿', 'necklace',  1, 'forest', '{"MATK":"5","MREGEN":"2"}',                           10, 20, NULL,              1, 25);

-- ============================================================
-- 2. 词条主表 cfg_affix（14 行）
-- 来源：GameConst.AFFIXES（min/max/rarity/side_key/side_val/type 照抄）
-- ============================================================
DELETE FROM `cfg_affix`;
INSERT INTO `cfg_affix`
  (`code`, `name`, `stat_key`, `stat_type`, `min_val`, `max_val`, `rarity`, `side_key`, `side_val`, `enabled`, `sort_no`) VALUES
  ('sharp',        '锋利',     'PATK',      'flat', 4,      16,    'common',   NULL,   NULL,   1, 1),
  ('magic_power',  '咒能',     'MATK',      'flat', 6,      24,    'common',   NULL,   NULL,   1, 2),
  ('regen_vigorous','坚韧',    'REGEN',     'flat', 1,      4,     'common',   NULL,   NULL,   1, 3),
  ('nimble',       '灵巧',     'SPD',       'flat', 10,     30,    'common',   NULL,   NULL,   1, 4),
  ('heavy_armor',  '重甲',     'PDEF',      'flat', 6,      20,    'common',   'SPD',  -0.03,  1, 5),
  ('magic_weave',  '魔纹',     'MDEF',      'flat', 6,      18,    'common',   NULL,   NULL,   1, 6),
  ('eagle_eye',    '鹰眼',     'ACC',       'flat', 0.03,  0.08,  'uncommon', NULL,   NULL,   1, 7),
  ('blue_spring',  '蓝泉',     'MREGEN',    'flat', 1,      3,     'uncommon', NULL,   NULL,   1, 8),
  ('deadly',       '致命',     'CRIT',      'flat', 0.03,  0.15,  'rare',     NULL,   NULL,   1, 9),
  ('brutal',       '残忍',     'CRITD',     'flat', 0.10,  0.50,  'rare',     NULL,   NULL,   1, 10),
  ('lifesteal',    '吸血回春', 'LIFESTEAL', 'flat', 0.005, 0.03,  'rare',     NULL,   NULL,   1, 11),
  ('swift',        '迅捷',     'ASPD',      'flat', 0.02,  0.10,  'epic',     NULL,   NULL,   1, 12),
  ('mana_tide',    '法力洪流', 'MP',        'flat', 100,   500,   'rare',     NULL,   NULL,   1, 14),
  ('extend',       '延伸',     'RNG',       'flat', 1,     2,     'epic',     NULL,   NULL,   1, 15);

-- ============================================================
-- 3. 商品主表 cfg_item（6 行，kind='potion'）
-- 来源：GameConst.POTIONS（price 照抄，effect_json 见说明）
-- ============================================================
DELETE FROM `cfg_item`;
INSERT INTO `cfg_item`
  (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('hp_s', '小血药',   '🧪', 'potion', 50,  '{"healHP":0.2}',                                     '瞬回 20% 生命上限', 1, 1),
  ('hp_l', '大血药',   '🧪', 'potion', 200, '{"healHP":0.5}',                                     '瞬回 50% 生命上限', 1, 2),
  ('mp',   '蓝药',     '🔵', 'potion', 40,  '{"healMP":0.4}',                                     '瞬回 40% 蓝量',     1, 3),
  ('food', '兔肉',     '🍖', 'potion', 10,  '{"buff":"regen_food","buffVal":20,"buffDur":10}',    '10s 内回血 +20',    1, 4),
  ('cure', '解毒剂',   '💊', 'potion', 30,  '{"cure":true}',                                      '清除中毒/灼烧',     1, 5),
  ('spd',  '速度药剂', '💨', 'potion', 100, '{"buff":"spd_pot","buffVal":0.2,"buffDur":30}',      '30s 内攻速 +20%',   1, 6);

-- ============================================================
-- 4. 技能主表 cfg_skill（14 行 = 6 主动 + 8 被动）
-- 来源：GameConst.ACTIVE_SKILLS / GameConst.PASSIVE_SKILLS
-- 主动：mp/cd/dur 照抄；被动：price=300，effect_json 存 GameConst 对应参数
-- ============================================================
DELETE FROM `cfg_skill`;
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('heavy',    '蓄力重击', '💥', 'active',  300, 20, 30,  0,   '{"id":"heavy","dmgMul":5}',                              '下一击 ×5 伤害',                 1, 1),
  ('block',    '格挡反击', '🛡️', 'active',  300, 25, 20,  3,   '{"id":"block","dmgTaken":0.5,"reflect":0.5}',            '3s 内受击减半并反弹 50%',        1, 2),
  ('roar',     '怒吼',     '😤', 'active',  300, 15, 60,  0,   '{"id":"roar","scare":true}',                             '吓退当前怪物（移速判定）',       1, 3),
  ('rapid',    '急速射击', '⚡', 'active',  300, 40, 90,  5,   '{"id":"rapid","aspdMul":3}',                             '5s 内攻速 ×3',                   1, 4),
  ('burn',     '燃烧生命', '🔥', 'active',  300, 0,  300, 300, '{"id":"burn","hpCost":0.25,"atkMul":2}',                 '烧当前血 25%，300s 内攻击 ×2',   1, 5),
  ('surge',    '魔力涌动', '✨', 'active',  300, 50, 180, 10,  '{"id":"surge","freeCast":true}',                         '10s 内技能不耗蓝',               1, 6),
  ('combo',    '连击',     '👊', 'passive', 300, 0,  0,   0,   '{"effect":"combo","chance":0.15}',                        '15% 概率一拳变两拳',             1, 7),
  ('vitality', '健体',     '💪', 'passive', 300, 0,  0,   0,   '{"effect":"vitality","interval":100,"bonus":50}',         '每 100 次普攻额外 +50 生命上限', 1, 8),
  ('iron',     '铁布衫',   '🛡️', 'passive', 300, 0,  0,   0,   '{"effect":"mul","key":"PDEF","mul":1.2}',                 '物防 ×1.2',                      1, 9),
  ('resist',   '魔抗皮肤', '🔮', 'passive', 300, 0,  0,   0,   '{"effect":"mul","key":"MDEF","mul":1.2}',                 '魔防 ×1.2',                      1, 10),
  ('swift',    '疾风步',   '💨', 'passive', 300, 0,  0,   0,   '{"effect":"mul","key":"SPD","mul":1.2}',                  '+20% 移速',                       1, 11),
  ('hawkeye',  '鹰眼',     '🦅', 'passive', 300, 0,  0,   0,   '{"effect":"add","key":"ACC","val":0.05}',                 '+5% 命中',                       1, 12),
  ('brute',    '蛮力',     '💢', 'passive', 300, 0,  0,   0,   '{"effect":"mul","key":"PATK","mul":1.15,"sideKey":"ACC","sideVal":-0.03}', '物攻 ×1.15，-3% 命中', 1, 13),
  ('element',  '元素亲和', '⚡', 'passive', 300, 0,  0,   0,   '{"effect":"mul","key":"AFFIX_MATK","mul":1.25}',          '附魔魔伤 +25%',                  1, 14);

-- ============================================================
-- 5. 状态主表 cfg_buff（9 行 = 6 增益 passive + 3 负面 status）
-- 来源：GameConst 顶部 BUFF_META 语义 + GameEngine.dotTick 数值
-- poison dotPct=0.02、burn_dot dotPct=0.015
-- ============================================================
DELETE FROM `cfg_buff`;
INSERT INTO `cfg_buff`
  (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('burn',       '燃烧生命', '🔥', 'good', 'passive', '{"atkMul":2}',                   '300s 内攻击 ×2（烧当前血 25%）', 1, 1),
  ('block',      '格挡',     '🛡️', 'good', 'passive', '{"dmgTaken":0.5,"reflect":0.5}', '3s 内受击减半并反弹 50%',        1, 2),
  ('rapid',      '急速',     '⚡', 'good', 'passive', '{"aspdMul":3}',                   '5s 内攻速 ×3',                   1, 3),
  ('surge',      '涌动',     '✨', 'good', 'passive', '{"freeCast":true}',               '10s 内技能不耗蓝',               1, 4),
  ('regen_food', '饱食回血', '🍖', 'good', 'passive', '{"regenAdd":20}',                 '10s 内回血 +20',                 1, 5),
  ('spd_pot',    '迅捷药剂', '💨', 'good', 'passive', '{"aspdPct":0.2}',                 '30s 内攻速 +20%',                1, 6),
  ('silence',    '沉默',     '🤐', 'bad',  'status',  '{"silence":true}',                '增益被动失效（Boss 沉默领域）',  1, 7),
  ('poison',     '中毒',     '☠️', 'bad',  'status',  '{"dotPct":0.02}',                 '每秒掉 2% 当前生命',             1, 8),
  ('burn_dot',   '灼烧',     '🔥', 'bad',  'status',  '{"dotPct":0.015}',                '每秒掉 1.5% 当前生命',           1, 9);

-- ============================================================
-- 6. 爆率表 cfg_drop_rate（8 行 = 7 区域 + boss）
-- 来源：GameConst.DROP_RATE + boss 必掉（rate=1.0，单件上限）
-- ============================================================
DELETE FROM `cfg_drop_rate`;
INSERT INTO `cfg_drop_rate`
  (`zone`, `rate`, `batch_cap`, `enabled`, `sort_no`) VALUES
  ('village', 0.0,  3, 1, 1),
  ('plain',   0.30, 3, 1, 2),
  ('forest',  0.35, 3, 1, 3),
  ('mine',    0.45, 3, 1, 4),
  ('swamp',   0.55, 3, 1, 5),
  ('snow',    0.65, 3, 1, 6),
  ('volcano', 0.75, 3, 1, 7),
  ('boss',    1.0,  1, 1, 8);

-- ============================================================
-- 核对语句（执行后人工确认行数）
-- 期望：装备 25 / 词条 13 / 商品 6 / 技能 14 / 状态 9 / 爆率 8
-- ============================================================
-- SELECT 'cfg_equipment_template' AS tbl, COUNT(*) AS cnt FROM `cfg_equipment_template`;
-- SELECT 'cfg_affix'              AS tbl, COUNT(*) AS cnt FROM `cfg_affix`;
-- SELECT 'cfg_item'               AS tbl, COUNT(*) AS cnt FROM `cfg_item`;
-- SELECT 'cfg_skill'              AS tbl, COUNT(*) AS cnt FROM `cfg_skill`;
-- SELECT 'cfg_buff'               AS tbl, COUNT(*) AS cnt FROM `cfg_buff`;
-- SELECT 'cfg_drop_rate'          AS tbl, COUNT(*) AS cnt FROM `cfg_drop_rate`;
