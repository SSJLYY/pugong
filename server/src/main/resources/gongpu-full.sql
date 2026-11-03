-- ============================================================
-- 《普攻永久增加生命上限》数据库全量脚本（唯一入口）
-- 由原 22 个文件整合：schema.sql + seed-v3.sql + 全部 migration-*.sql
-- 用法：mysql --default-character-set=utf8mb4 < gongpu-full.sql
-- 性质：全程幂等——新装一次到位、存量库升级、重复执行，三种场景同一文件。
--   · DDL 为 CREATE TABLE IF NOT EXISTS（现行结构，与原 schema.sql 同步）
--   · 历史裸 ALTER（v2.1/v2.2/v3.16）改为 information_schema 守卫式加列，
--     列已存在自动跳过；v3.16/v3.17 的一次性回填仅在本次真加列时执行，
--     避免对现行库重复回填把终身口径冲回当世值
--   · 种子与内容迁移均为 DELETE+INSERT / 定向 UPDATE，可重复执行
-- 结构：§1 表结构 → §2 结构守卫（历史加列） → §3 基线种子 → §4 内容迁移（按版本序）
-- ============================================================

-- ============================================================
-- §1 表结构（原 schema.sql）
-- ============================================================
-- ============================================================
-- 《普攻永久增加生命上限》v2.2 数据库结构（新装一次性到位）
-- MySQL 8.0 / utf8mb4
-- 包含 v2.1（存档快照）与 v2.2（社交玩法）全部列
-- ============================================================

CREATE DATABASE IF NOT EXISTS `gongpu`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `gongpu`;

-- ---------- 账号 ----------
CREATE TABLE IF NOT EXISTS `account_user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `username`      VARCHAR(32)  NOT NULL,
  `password_hash` VARCHAR(100) NOT NULL,
  `created_at`    DATETIME(6)  NOT NULL,
  `last_login_at` DATETIME(6)  DEFAULT NULL,
  `incense`       INT          NOT NULL DEFAULT 0 COMMENT '香火（复活货币）',
  `pvp_wins`      INT          NOT NULL DEFAULT 0,
  `pvp_losses`    INT          NOT NULL DEFAULT 0,
  `last_claim_day` DATE        DEFAULT NULL COMMENT '每日签到最近领取日',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='账号';

-- ---------- 存档槽 ----------
CREATE TABLE IF NOT EXISTS `game_save` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT       NOT NULL,
  `slot_no`       INT          NOT NULL,
  `slot_name`     VARCHAR(32)  DEFAULT NULL,
  `gen`           INT          NOT NULL DEFAULT 0,
  `zone`          VARCHAR(16)  DEFAULT 'village',
  `max_hp`        VARCHAR(80)  DEFAULT '100',
  `alive_seconds` BIGINT       NOT NULL DEFAULT 0,
  `punches_total` DECIMAL(65,0) DEFAULT 0 COMMENT '冗余累计出拳（运营统计 SUM 用）',
  `kills_total`   DECIMAL(65,0) DEFAULT 0 COMMENT '冗余累计击杀（运营统计 SUM 用）',
  `deaths_total`  BIGINT       NOT NULL DEFAULT 0 COMMENT '冗余累计死亡（运营统计 SUM 用）',
  `rebirths_total` BIGINT      NOT NULL DEFAULT 0 COMMENT '冗余累计转生（运营统计 SUM 用）',
  `state_json`    LONGTEXT     NOT NULL COMMENT '当前活着的滚动状态',
  `snapshot_json` LONGTEXT     NULL COMMENT '存档点快照（死亡回档读它）',
  `saved_at`      DATETIME(6)  NULL COMMENT '存档点时间',
  `created_at`    DATETIME(6)  NOT NULL,
  `updated_at`    DATETIME(6)  NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_save_user_slot` (`user_id`, `slot_no`),
  KEY `idx_save_updated` (`updated_at`),
  CONSTRAINT `fk_save_user` FOREIGN KEY (`user_id`) REFERENCES `account_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='存档槽';

-- ---------- 墓园（阵亡碑 / 飞升金碑） ----------
CREATE TABLE IF NOT EXISTS `game_grave` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL,
  `kind`        VARCHAR(16)  NOT NULL COMMENT 'death=黑碑，ascend=金碑',
  `gen`         INT          NOT NULL DEFAULT 0,
  `killer`      VARCHAR(64)  DEFAULT NULL,
  `final_hp`    VARCHAR(80)  DEFAULT '0',
  `lived_sec`   BIGINT       NOT NULL DEFAULT 0,
  `punches`     VARCHAR(80)  DEFAULT '0',
  `epitaph`     VARCHAR(120) DEFAULT NULL,
  `tributes`    INT          NOT NULL DEFAULT 0 COMMENT '被祭奠次数',
  `created_at`  DATETIME(6)  NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_grave_user` (`user_id`, `created_at`),
  KEY `idx_grave_tributes` (`tributes`),
  CONSTRAINT `fk_grave_user` FOREIGN KEY (`user_id`) REFERENCES `account_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='墓园';

-- ---------- 全局统计（运营看板用） ----------
CREATE TABLE IF NOT EXISTS `stat_daily` (
  `day`          DATE         NOT NULL,
  `punches`      VARCHAR(80)  NOT NULL DEFAULT '0',
  `kills`        VARCHAR(80)  NOT NULL DEFAULT '0',
  `deaths`       BIGINT       NOT NULL DEFAULT 0,
  `rebirths`     BIGINT       NOT NULL DEFAULT 0,
  `active_saves` BIGINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`day`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='按日汇总';

-- ============================================================
-- v3.0 配置主表（装备 / 词条 / 商品 / 技能 / 状态 / 爆率）
-- 运维端直接维护；GameData 启动加载 + 改后热刷新
-- ============================================================

-- ---------- 装备主表 ----------
CREATE TABLE IF NOT EXISTS `cfg_equipment_template` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `code`         VARCHAR(32)  NOT NULL COMMENT '唯一代码',
  `name`         VARCHAR(32)  NOT NULL,
  `emoji`        VARCHAR(8)   DEFAULT NULL,
  `slot`         VARCHAR(16)  NOT NULL COMMENT 'weapon/offhand/bow/head/chest/legs/boots/ring1/ring2/necklace',
  `tier`         INT          NOT NULL DEFAULT 0,
  `min_zone`     VARCHAR(16)  DEFAULT NULL COMMENT '最低出现区域',
  `base_stats`   TEXT         NOT NULL COMMENT '底子属性 JSON',
  `enhance_max`  INT          NOT NULL DEFAULT 10,
  `sell_base`    INT          NOT NULL DEFAULT 20,
  `remark`       VARCHAR(64)  DEFAULT NULL,
  `enabled`      TINYINT(1)   NOT NULL DEFAULT 1,
  `sort_no`      INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_equip_code` (`code`),
  KEY `idx_equip_slot_tier` (`slot`, `tier`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='装备模板';

-- ---------- 词条主表 ----------
CREATE TABLE IF NOT EXISTS `cfg_affix` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `code`       VARCHAR(32)  NOT NULL,
  `name`       VARCHAR(32)  NOT NULL,
  `stat_key`   VARCHAR(24)  NOT NULL,
  `stat_type`  VARCHAR(8)   NOT NULL DEFAULT 'flat' COMMENT 'flat/pct',
  `min_val`    DOUBLE       NOT NULL DEFAULT 0,
  `max_val`    DOUBLE       NOT NULL DEFAULT 0,
  `rarity`     VARCHAR(16)  DEFAULT 'common',
  `side_key`   VARCHAR(24)  DEFAULT NULL COMMENT '副作用键',
  `side_val`   DOUBLE       DEFAULT NULL,
  `enabled`    TINYINT(1)   NOT NULL DEFAULT 1,
  `sort_no`    INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_affix_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='随机词条';

-- ---------- 商品主表 ----------
CREATE TABLE IF NOT EXISTS `cfg_item` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `code`         VARCHAR(32)  NOT NULL,
  `name`         VARCHAR(32)  NOT NULL,
  `emoji`        VARCHAR(8)   DEFAULT NULL,
  `kind`         VARCHAR(16)  NOT NULL DEFAULT 'potion',
  `price`        INT          NOT NULL DEFAULT 0,
  `effect_json`  TEXT         NOT NULL,
  `desc`         VARCHAR(64)  DEFAULT NULL,
  `enabled`      TINYINT(1)   NOT NULL DEFAULT 1,
  `sort_no`      INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商店商品';

-- ---------- 技能主表 ----------
CREATE TABLE IF NOT EXISTS `cfg_skill` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `code`         VARCHAR(32)  NOT NULL,
  `name`         VARCHAR(32)  NOT NULL,
  `emoji`        VARCHAR(8)   DEFAULT NULL,
  `kind`         VARCHAR(8)   NOT NULL DEFAULT 'active' COMMENT 'active/passive',
  `price`        INT          NOT NULL DEFAULT 300,
  `mp`           INT          NOT NULL DEFAULT 0,
  `cd`           INT          NOT NULL DEFAULT 0,
  `dur`          INT          NOT NULL DEFAULT 0,
  `effect_json`  TEXT         NOT NULL,
  `desc`         VARCHAR(64)  DEFAULT NULL,
  `enabled`      TINYINT(1)   NOT NULL DEFAULT 1,
  `sort_no`      INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能（主动+被动）';

-- ---------- 状态主表 ----------
CREATE TABLE IF NOT EXISTS `cfg_buff` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `code`         VARCHAR(32)  NOT NULL,
  `name`         VARCHAR(32)  NOT NULL,
  `emoji`        VARCHAR(8)   DEFAULT NULL,
  `nature`       VARCHAR(8)   NOT NULL DEFAULT 'good' COMMENT 'good/bad',
  `group_type`   VARCHAR(8)   NOT NULL DEFAULT 'passive' COMMENT 'passive/status',
  `effect_json`  TEXT         NOT NULL,
  `desc`         VARCHAR(64)  DEFAULT NULL,
  `enabled`      TINYINT(1)   NOT NULL DEFAULT 1,
  `sort_no`      INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_buff_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='状态效果';

-- ---------- 爆率表 ----------
CREATE TABLE IF NOT EXISTS `cfg_drop_rate` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `zone`       VARCHAR(16)  NOT NULL COMMENT '区域或 boss',
  `rate`       DOUBLE       NOT NULL DEFAULT 0,
  `batch_cap`  INT          NOT NULL DEFAULT 3,
  `enabled`    TINYINT(1)   NOT NULL DEFAULT 1,
  `sort_no`    INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_drop_zone` (`zone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='区域爆率';

-- ---------- 委托主表（DESIGN.md §5，v3.3） ----------
CREATE TABLE IF NOT EXISTS `cfg_quest` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `code`             VARCHAR(32)  NOT NULL,
  `name`             VARCHAR(32)  NOT NULL,
  `emoji`            VARCHAR(8)   DEFAULT NULL,
  `desc`             VARCHAR(80)  DEFAULT NULL,
  `target_zone`      VARCHAR(16)  NOT NULL,
  `req_json`         TEXT         NOT NULL,
  `target_kills`     INT          NOT NULL DEFAULT 10,
  `reward_gold`      INT          NOT NULL DEFAULT 100,
  `reward_item`      VARCHAR(32)  DEFAULT NULL,
  `reward_item_count` INT         DEFAULT 0,
  `repeatable`       TINYINT(1)   NOT NULL DEFAULT 1,
  `enabled`          TINYINT(1)   NOT NULL DEFAULT 1,
  `sort_no`          INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quest_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='冒险公会委托';

-- ---------- 合成配方主表（DESIGN.md §6/§9 图纸合成，v3.3） ----------
CREATE TABLE IF NOT EXISTS `cfg_craft` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `code`           VARCHAR(32)  NOT NULL,
  `name`           VARCHAR(32)  NOT NULL,
  `emoji`          VARCHAR(8)   DEFAULT NULL,
  `product_code`   VARCHAR(32)  NOT NULL COMMENT '产出装备模板 code',
  `mats_json`      TEXT         NOT NULL COMMENT '材料需求 JSON',
  `gold_cost`      INT          NOT NULL DEFAULT 0,
  `need_blueprint` TINYINT(1)   NOT NULL DEFAULT 1,
  `enabled`        TINYINT(1)   NOT NULL DEFAULT 1,
  `sort_no`        INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_craft_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='图纸合成配方';

-- ---------- 运维审计日志 ----------
CREATE TABLE IF NOT EXISTS `admin_audit_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `action`      VARCHAR(64)  NOT NULL COMMENT '如 USER_RESET_PW / EQUIP_CREATE',
  `detail`      VARCHAR(500) DEFAULT NULL,
  `created_at`  DATETIME(6)  NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运维操作日志';

-- ============================================================
-- 账号（root 执行一次；密码请自行替换强密码并妥善保存）
-- ============================================================
-- CREATE USER IF NOT EXISTS 'gongpu_app'@'127.0.0.1' IDENTIFIED BY '<强密码>';
-- GRANT ALL PRIVILEGES ON gongpu.* TO 'gongpu_app'@'127.0.0.1';
-- FLUSH PRIVILEGES;

-- ============================================================
-- §2 结构守卫（原 migration-v2.1 / v2.2 / v3.16 / v3.17 的加列与回填）
-- 守卫写法：information_schema 查列 → 缺失才拼 ALTER 执行；回填绑定「本次加列」标志
-- ============================================================

-- ---- v2.1 存档快照列（game_save.snapshot_json / saved_at） ----
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = 'gongpu' AND table_name = 'game_save' AND column_name = 'snapshot_json');
SET @sql := IF(@c = 0, 'ALTER TABLE `game_save` ADD COLUMN `snapshot_json` LONGTEXT NULL COMMENT ''存档点快照（死亡回档读它）'' AFTER `state_json`', 'SELECT 1');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = 'gongpu' AND table_name = 'game_save' AND column_name = 'saved_at');
SET @sql := IF(@c = 0, 'ALTER TABLE `game_save` ADD COLUMN `saved_at` DATETIME(6) NULL COMMENT ''存档点时间'' AFTER `snapshot_json`', 'SELECT 1');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- ---- v2.2 社交列（account_user.incense / pvp_wins / pvp_losses、game_grave.tributes） ----
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = 'gongpu' AND table_name = 'account_user' AND column_name = 'incense');
SET @sql := IF(@c = 0, 'ALTER TABLE `account_user` ADD COLUMN `incense` INT NOT NULL DEFAULT 0 COMMENT ''香火（复活货币）'' AFTER `last_login_at`', 'SELECT 1');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = 'gongpu' AND table_name = 'account_user' AND column_name = 'pvp_wins');
SET @sql := IF(@c = 0, 'ALTER TABLE `account_user` ADD COLUMN `pvp_wins` INT NOT NULL DEFAULT 0 AFTER `incense`', 'SELECT 1');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = 'gongpu' AND table_name = 'account_user' AND column_name = 'pvp_losses');
SET @sql := IF(@c = 0, 'ALTER TABLE `account_user` ADD COLUMN `pvp_losses` INT NOT NULL DEFAULT 0 AFTER `pvp_wins`', 'SELECT 1');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = 'gongpu' AND table_name = 'game_grave' AND column_name = 'tributes');
SET @sql := IF(@c = 0, 'ALTER TABLE `game_grave` ADD COLUMN `tributes` INT NOT NULL DEFAULT 0 COMMENT ''被祭奠次数'' AFTER `epitaph`', 'SELECT 1');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
-- 每日签到列（原由 deploy.sh migrate() 内联补，本文件一并收口）
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = 'gongpu' AND table_name = 'account_user' AND column_name = 'last_claim_day');
SET @sql := IF(@c = 0, 'ALTER TABLE `account_user` ADD COLUMN `last_claim_day` DATE NULL COMMENT ''每日签到最近领取日'' AFTER `pvp_losses`', 'SELECT 1');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- ---- v3.16 运营统计冗余列 + v3.17 终身口径回填（仅本次真加列时回填） ----
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = 'gongpu' AND table_name = 'game_save' AND column_name = 'punches_total');
SET @added_stats := IF(@c = 0, 1, 0);
SET @sql := IF(@c = 0, 'ALTER TABLE `game_save` ADD COLUMN `punches_total` DECIMAL(65,0) DEFAULT 0 COMMENT ''冗余累计出拳（运营统计 SUM 用）'' AFTER `alive_seconds`', 'SELECT 1');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = 'gongpu' AND table_name = 'game_save' AND column_name = 'kills_total');
SET @sql := IF(@c = 0, 'ALTER TABLE `game_save` ADD COLUMN `kills_total` DECIMAL(65,0) DEFAULT 0 COMMENT ''冗余累计击杀（运营统计 SUM 用）'' AFTER `punches_total`', 'SELECT 1');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = 'gongpu' AND table_name = 'game_save' AND column_name = 'deaths_total');
SET @sql := IF(@c = 0, 'ALTER TABLE `game_save` ADD COLUMN `deaths_total` BIGINT NOT NULL DEFAULT 0 COMMENT ''冗余累计死亡（运营统计 SUM 用）'' AFTER `kills_total`', 'SELECT 1');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = 'gongpu' AND table_name = 'game_save' AND column_name = 'rebirths_total');
SET @sql := IF(@c = 0, 'ALTER TABLE `game_save` ADD COLUMN `rebirths_total` BIGINT NOT NULL DEFAULT 0 COMMENT ''冗余累计转生（运营统计 SUM 用）'' AFTER `deaths_total`', 'SELECT 1');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
-- v3.16 回填（当世口径）→ v3.17 改终身口径种子：两步仅在本次新建冗余列时执行
SET @sql := IF(@added_stats = 1,
  'UPDATE `game_save` SET `punches_total` = COALESCE(CAST(JSON_EXTRACT(`state_json`, ''$.stats.punches'') AS DECIMAL(65,0)), 0), `kills_total` = COALESCE(CAST(JSON_EXTRACT(`state_json`, ''$.stats.kills'') AS DECIMAL(65,0)), 0), `deaths_total` = COALESCE(CAST(JSON_EXTRACT(`state_json`, ''$.stats.deaths'') AS SIGNED), 0), `rebirths_total` = COALESCE(CAST(JSON_EXTRACT(`state_json`, ''$.stats.rebirths'') AS SIGNED), 0)',
  'SELECT 1');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @sql := IF(@added_stats = 1,
  'UPDATE `game_save` SET `punches_total` = COALESCE(CAST(JSON_EXTRACT(`state_json`, ''$.stats.lifetimePunches'') AS DECIMAL(65,0)), 0), `kills_total` = COALESCE(CAST(JSON_EXTRACT(`state_json`, ''$.stats.kills'') AS DECIMAL(65,0)), 0)',
  'SELECT 1');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- ============================================================
-- §3 基线种子（原 seed-v3.sql：cfg_* 全表 DELETE+INSERT）
-- ============================================================
-- ============================================================
-- 《普攻永久增加生命上限》v3.0 配置数据种子
-- 数据来源：GameConst.java / EquipFactory.java（迁移硬编码数值）
-- 幂等：可重复执行（先 DELETE 再 INSERT）
-- MySQL 8.0 / utf8mb4
-- ============================================================

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
-- 4. 技能主表 cfg_skill（15 行 = 6 主动 + 9 被动）
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
  ('element',  '元素亲和', '⚡', 'passive', 300, 0,  0,   0,   '{"effect":"mul","key":"AFFIX_MATK","mul":1.25}',          '附魔魔伤 +25%',                  1, 14),
  ('boss_hunter', '巨人杀手', '🗡️', 'passive', 300, 0,  0,   0,   '{"effect":"boss_hunter","mul":1.2}',                     '对 Boss 伤害 +20%',              1, 15);

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

-- ============================================================
-- §4 内容迁移（按版本序，全部幂等；每节保留原文件名标注）
-- ============================================================

-- ---------- 原 migration-v3.1-content.sql ----------
-- v3.1 玩法扩充：2 种新药剂 + 3 件新装备 + 2 个新状态（幂等）
-- ---------- 新药剂 ----------
INSERT INTO `cfg_item` (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`)
VALUES
  ('growth', '修行药剂', '🍵', 'potion', 600, '{"buff":"growth_pot","buffVal":2,"buffDur":120}', '120s 内普攻成长 ×2（打桩冲级利器）', 1, 7),
  ('ironpot', '铁壁药剂', '🛡️', 'potion', 400, '{"buff":"iron_pot","buffVal":1.5,"buffDur":60}', '60s 内物防魔防 ×1.5（Boss 前磕一瓶）', 1, 8)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `price`=VALUES(`price`), `effect_json`=VALUES(`effect_json`), `desc`=VALUES(`desc`), `enabled`=1, `sort_no`=VALUES(`sort_no`);

-- ---------- 新状态（前端效果区显示用） ----------
INSERT INTO `cfg_buff` (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`)
VALUES
  ('growth_pot', '修行药剂', '🍵', 'good', 'passive', '{"growthMul":2}', '普攻成长 ×2', 1, 7),
  ('iron_pot', '铁壁药剂', '🛡️', 'good', 'passive', '{"defMul":1.5}', '物防魔防 ×1.5', 1, 8)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `nature`=VALUES(`nature`), `group_type`=VALUES(`group_type`), `effect_json`=VALUES(`effect_json`), `desc`=VALUES(`desc`), `enabled`=1, `sort_no`=VALUES(`sort_no`);

-- ---------- 新装备（3 件毕业向底子） ----------
INSERT INTO `cfg_equipment_template` (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`)
VALUES
  ('dragon_scale_mail', '龙鳞甲', '🐲', 'chest',   3, 'volcano', '{"PDEF":"18","MDEF":"12","REGEN":"3"}',  12, 60, '毕业胸甲：双防+回血', 1, 30),
  ('thunder_ring',      '雷鸣指环', '⚡', 'ring1',  3, 'volcano', '{"ASPD":"0.2","PATK":"3"}',               12, 60, '攻速流毕业戒指',     1, 31),
  ('holy_necklace',     '圣光项链', '✨', 'necklace', 3, 'snow',   '{"MDEF":"10","MREGEN":"4","MATK":"6"}',  12, 60, '法系毕业项链',       1, 32)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `slot`=VALUES(`slot`), `tier`=VALUES(`tier`), `min_zone`=VALUES(`min_zone`), `base_stats`=VALUES(`base_stats`), `enhance_max`=VALUES(`enhance_max`), `sell_base`=VALUES(`sell_base`), `remark`=VALUES(`remark`), `enabled`=1, `sort_no`=VALUES(`sort_no`);

-- 核对
-- SELECT code,name,price FROM cfg_item WHERE code IN ('growth','ironpot');
-- SELECT code,name,min_zone FROM cfg_equipment_template WHERE code IN ('dragon_scale_mail','thunder_ring','holy_necklace');
-- SELECT code,name FROM cfg_buff WHERE code IN ('growth_pot','iron_pot');

-- ---------- 原 migration-v3.2-guns.sql ----------
-- ============================================================
-- v3.2 枪械系统：三线武器（物理弹 / 物理附魔弹 / 纯魔法弹）
-- 纯魔法弹：base_stats 带 BULLET_MP 标记，每发子弹耗蓝；无蓝哑火
-- 加特林：攻速流终极浪漫，一发子弹=一次普攻
-- ============================================================
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

-- ---------- 原 migration-v3.3-belt.sql ----------
-- ============================================================
-- v3.3 P1 补充：负重腰带（DESIGN.md §6.4 经典取舍款）
-- 背包 +20 格 / -3 移速
-- ============================================================
INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`)
VALUES
  ('load_belt', '负重腰带', '🎽', 'legs', 1, 'forest',
   '{"BAG_CAP":"20","SPD":"-3"}', 10, 25, '背包 +20 格 · 移速 -3（后勤流取舍款）', 1, 50)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `slot`=VALUES(`slot`), `tier`=VALUES(`tier`),
  `min_zone`=VALUES(`min_zone`), `base_stats`=VALUES(`base_stats`), `enhance_max`=VALUES(`enhance_max`),
  `sell_base`=VALUES(`sell_base`), `remark`=VALUES(`remark`), `enabled`=1, `sort_no`=VALUES(`sort_no`);

-- ---------- 原 migration-v3.3-cooking.sql ----------
-- ============================================================
-- v3.3 P1 烹饪（DESIGN.md §8：食材 → 料理）
-- 产出走 item: 前缀（药剂通道）：兔肉料理/狼肉料理/烤野猪肉
-- 无需图纸（基础烹饪）
-- ============================================================
INSERT INTO `cfg_craft`
  (`code`, `name`, `emoji`, `product_code`, `mats_json`, `gold_cost`, `need_blueprint`, `enabled`, `sort_no`)
VALUES
  ('cook_rabbit', '烤兔肉',   '🍖', 'item:food',     '{"pelt":3}',               10,  0, 1, 3),
  ('cook_boar',   '炖野猪肉', '🍲', 'item:hp_s',     '{"pelt":5}',               50,  0, 1, 4),
  ('cook_wolf',   '狼肉大餐', '🥘', 'item:hp_l',     '{"pelt":6,"wolf_fang":2}', 120, 0, 1, 5)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `product_code`=VALUES(`product_code`),
  `mats_json`=VALUES(`mats_json`), `gold_cost`=VALUES(`gold_cost`),
  `need_blueprint`=VALUES(`need_blueprint`), `enabled`=1, `sort_no`=VALUES(`sort_no`);

-- ---------- 原 migration-v3.3-crafts.sql ----------
-- ============================================================
-- v3.3 图纸合成种子（DESIGN.md §6/§9）
-- 材料：兽皮(plain)/狼牙(forest)/矿石(mine)/毒腺(swamp)/魔尘(snow)/火晶(volcano)
-- 图纸打怪掉落（Boss 30% / 普通怪 4%）；图纸+材料+金币 → 指定底子装备（词条随机）
-- ============================================================
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

-- ---------- 原 migration-v3.3-quests.sql ----------
-- ============================================================
-- v3.3 委托系统种子（DESIGN.md §5 冒险公会）
-- 属性门槛接取，击杀交付；奖励金币+药剂（速度药剂走委托产出，对齐设计 §8）
-- ============================================================
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
  ('mine_deep',    '深入矿脉',   '💎', '矿脉深处有异响，老矿工不敢下去了',     'mine',    '{"maxHP":30000,"PDEF":20}',         15, 700,  'far',     1, 1, 1, 9),
  ('swamp_venom',  '毒雾深处',   '☠️', '瘴气最浓处盘踞着老毒物，公会出重赏',   'swamp',   '{"maxHP":80000,"MDEF":20}',         18, 950,  'luck',    1, 1, 1, 10),
  ('snow_alpha',   '雪原狼王',   '🐺', '雪原狼群有了头狼，商路又断了',         'snow',    '{"maxHP":200000,"PDEF":40}',        18, 1500, 'elixir',  1, 1, 1, 11),
  ('guild_init',   '入会试炼',   '🎖️', '新人的入会考核：证明你的拳头够硬',     'plain',   '{"maxHP":500}',                    3,  300,  'hp_s',    5, 0, 1, 0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `desc`=VALUES(`desc`), `target_zone`=VALUES(`target_zone`),
  `req_json`=VALUES(`req_json`), `target_kills`=VALUES(`target_kills`), `reward_gold`=VALUES(`reward_gold`),
  `reward_item`=VALUES(`reward_item`), `reward_item_count`=VALUES(`reward_item_count`),
  `repeatable`=VALUES(`repeatable`), `enabled`=1, `sort_no`=VALUES(`sort_no`);

-- ---------- 原 migration-v3.9-affix-flat.sql ----------
-- ============================================================
-- v3.9:词条池百分比改直接数值(玩家要求:回血/物攻等词条直接加面板,前期可感)
-- 幂等:UPDATE 重复执行结果一致。旧档已生成的 pct 词条按原 type 兼容生效。
-- 对应 GameConst.AFFIXES 与 seed-v3.sql 同步修改。
-- ============================================================
UPDATE `cfg_affix` SET `stat_type`='flat', `min_val`=4,  `max_val`=16 WHERE `code`='sharp'         AND `stat_type`='pct';
UPDATE `cfg_affix` SET `stat_type`='flat', `min_val`=6,  `max_val`=24 WHERE `code`='magic_power'    AND `stat_type`='pct';
UPDATE `cfg_affix` SET `stat_type`='flat', `min_val`=1,  `max_val`=4  WHERE `code`='regen_vigorous' AND `stat_type`='pct';
UPDATE `cfg_affix` SET `stat_type`='flat', `min_val`=10, `max_val`=30 WHERE `code`='nimble'         AND `stat_type`='pct';
UPDATE `cfg_affix` SET `stat_type`='flat', `min_val`=6,  `max_val`=20 WHERE `code`='heavy_armor'    AND `stat_type`='pct';
UPDATE `cfg_affix` SET `stat_type`='flat', `min_val`=6,  `max_val`=18 WHERE `code`='magic_weave'    AND `stat_type`='pct';
UPDATE `cfg_affix` SET `stat_type`='flat', `min_val`=1,  `max_val`=3  WHERE `code`='blue_spring'    AND `stat_type`='pct';

-- ---------- 原 migration-v3.13-affix-mp.sql ----------
-- ============================================================
-- v3.13:新增词条「法力洪流」——蓝上限(MP)+100~500
-- 背景:蓝上限固定 80 无成长线,纯魔法弹(BULLET_MP)流派无配套成长;
--       魔弹枪玩家靠本词条堆蓝上限,每帧可打发数 = 蓝上限 ÷ 每发耗蓝
-- 幂等:同 code 先删后插,可重复执行(与 seed 同口径)
-- ============================================================
DELETE FROM `cfg_affix` WHERE `code`='mana_tide';
INSERT INTO `cfg_affix`
  (`code`, `name`, `stat_key`, `stat_type`, `min_val`, `max_val`, `rarity`, `side_key`, `side_val`, `enabled`, `sort_no`) VALUES
  ('mana_tide',    '法力洪流', 'MP',        'flat', 100,   500,   'rare',     NULL,   NULL,   1, 14);

-- ---------- 原 migration-v3.14-potions.sql ----------
-- ============================================================
-- v3.14:商店新增 10 种药剂 + 配套增益 buff
-- 设计口径:全属性乘区/加区均受沉默(Silence)压制,与既有药剂一致;
--         护盾为吸收型新机制(先于血量扣除,GameEngine.mobAttack 接线)
-- 幂等:同 code 先删后插,可重复执行(与 seed 同口径)
-- ============================================================
-- ---- 商品主表 cfg_item:10 种新药剂 ----
DELETE FROM `cfg_item` WHERE `code` IN
  ('crit','critd','lifesteal','atk','def','aspd','acc','eva','mregen','shield');
INSERT INTO `cfg_item`
  (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('crit',      '锐眼药剂', '👁️', 'potion', 500, '{"buff":"crit_pot","buffVal":0.10,"buffDur":90}',  '90s 内暴击率 +10%',   1, 9),
  ('critd',     '残暴药剂', '🩸', 'potion', 550, '{"buff":"critd_pot","buffVal":0.50,"buffDur":90}',  '90s 内暴击伤害 +50%', 1, 10),
  ('lifesteal', '嗜血药剂', '🦇', 'potion', 450, '{"buff":"lifesteal_pot","buffVal":0.10,"buffDur":90}','90s 内吸血 +10%',    1, 11),
  ('atk',       '狂暴药剂', '⚔️', 'potion', 800, '{"buff":"atk_pot","buffVal":1.5,"buffDur":60}',     '60s 内攻击 ×1.5',     1, 12),
  ('def',       '磐石药剂', '🪨', 'potion', 700, '{"buff":"def_pot","buffVal":1.5,"buffDur":60}',     '60s 内物防魔防 ×1.5', 1, 13),
  ('aspd',      '疾风药剂', '🌪️', 'potion', 900, '{"buff":"aspd_pot","buffVal":1.5,"buffDur":60}',    '60s 内攻速 ×1.5',     1, 14),
  ('acc',       '明察药剂', '🎯', 'potion', 300, '{"buff":"acc_pot","buffVal":0.10,"buffDur":120}',   '120s 内命中 +10%',    1, 15),
  ('eva',       '幻影药剂', '🌫️', 'potion', 350, '{"buff":"eva_pot","buffVal":0.10,"buffDur":120}',   '120s 内闪避 +10%',    1, 16),
  ('mregen',    '凝神药剂', '🧿', 'potion', 250, '{"buff":"mregen_pot","buffVal":10,"buffDur":120}',  '120s 内回蓝 +10/秒',  1, 17),
  ('shield',    '护盾药剂', '🛡', 'potion', 900, '{"buff":"shield_pot","buffVal":0.30,"buffDur":30}', '30s 内获得 30% 生命上限的护盾', 1, 18);

-- ---- 状态主表 cfg_buff:10 个配套增益 ----
DELETE FROM `cfg_buff` WHERE `code` IN
  ('crit_pot','critd_pot','lifesteal_pot','atk_pot','def_pot','aspd_pot','acc_pot','eva_pot','mregen_pot','shield_pot');
INSERT INTO `cfg_buff`
  (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('crit_pot',      '锐眼', '👁️', 'good', 'passive', '{"critAdd":0.10}',     '90s 内暴击率 +10%',    1, 10),
  ('critd_pot',     '残暴', '🩸', 'good', 'passive', '{"critdAdd":0.50}',    '90s 内暴击伤害 +50%',  1, 11),
  ('lifesteal_pot', '嗜血', '🦇', 'good', 'passive', '{"lifestealAdd":0.10}','90s 内吸血 +10%',      1, 12),
  ('atk_pot',       '狂暴', '⚔️', 'good', 'passive', '{"atkMul":1.5}',       '60s 内攻击 ×1.5',      1, 13),
  ('def_pot',       '磐石', '🪨', 'good', 'passive', '{"defMul":1.5}',       '60s 内物防魔防 ×1.5',  1, 14),
  ('aspd_pot',      '疾风', '🌪️', 'good', 'passive', '{"aspdMul":1.5}',     '60s 内攻速 ×1.5',      1, 15),
  ('acc_pot',       '明察', '🎯', 'good', 'passive', '{"accAdd":0.10}',      '120s 内命中 +10%',     1, 16),
  ('eva_pot',       '幻影', '🌫️', 'good', 'passive', '{"evaAdd":0.10}',      '120s 内闪避 +10%',     1, 17),
  ('mregen_pot',    '凝神', '🧿', 'good', 'passive', '{"mregenAdd":10}',     '120s 内回蓝 +10/秒',   1, 18),
  ('shield_pot',    '护盾', '🛡', 'good', 'passive', '{"shieldPct":0.30}',   '30s 内获得 30% 生命上限的护盾', 1, 19);

-- 验证:
-- SELECT code,name,price FROM cfg_item WHERE code IN ('crit','critd','lifesteal','atk','def','aspd','acc','eva','mregen','shield');
-- SELECT code,name FROM cfg_buff WHERE code LIKE '%_pot';

-- ---------- 原 migration-v3.18-potions2.sql ----------
-- ============================================================
-- 第四批第 1 轮：药剂回复系扩充（特大血药 / 大蓝药 / 万灵药剂双回）
-- 幂等：同 code 先删后插，可重复执行（与 v3.14 同口径）
-- ============================================================
DELETE FROM `cfg_item` WHERE `code` IN ('hp_xl','mp_l','elixir');
INSERT INTO `cfg_item`
  (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('hp_xl',  '特大血药', '❤️', 'potion', 600, '{"healHP":1.0}',                 '瞬回 100% 生命上限',      1, 19),
  ('mp_l',   '大蓝药',   '💙', 'potion', 150, '{"healMP":1.0}',                 '瞬回 100% 蓝量',          1, 20),
  ('elixir', '万灵药剂', '🌈', 'potion', 800, '{"healHP":0.8,"healMP":0.8}',    '瞬回 80% 生命与 80% 蓝量', 1, 21);

-- 验证:
-- SELECT code,name,price FROM cfg_item WHERE code IN ('hp_xl','mp_l','elixir');

-- ---------- 原 migration-v3.19-potions3.sql ----------
-- ============================================================
-- 第四批第 2 轮：药剂新维度增益（持续回复 / 射程 / 移速）
-- 面板接线在 PanelCalculator（regen_pot/rng_pot/fleet_pot，同受沉默压制）
-- 幂等：同 code 先删后插，可重复执行（与 v3.14 同口径）
-- ============================================================
DELETE FROM `cfg_item` WHERE `code` IN ('regen','far','fleet');
INSERT INTO `cfg_item`
  (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('regen', '生生药剂', '🌿', 'potion', 400, '{"buff":"regen_pot","buffVal":25,"buffDur":120}', '120s 内回血 +25/秒', 1, 22),
  ('far',   '远眺药剂', '🔭', 'potion', 350, '{"buff":"rng_pot","buffVal":1,"buffDur":120}',     '120s 内射程 +1',     1, 23),
  ('fleet', '疾行药剂', '👟', 'potion', 300, '{"buff":"fleet_pot","buffVal":40,"buffDur":120}',  '120s 内移速 +40',    1, 24);

DELETE FROM `cfg_buff` WHERE `code` IN ('regen_pot','rng_pot','fleet_pot');
INSERT INTO `cfg_buff`
  (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('regen_pot', '生生', '🌿', 'good', 'passive', '{"regenAdd":25}', '120s 内回血 +25/秒', 1, 20),
  ('rng_pot',   '远眺', '🔭', 'good', 'passive', '{"rngAdd":1}',    '120s 内射程 +1',     1, 21),
  ('fleet_pot', '疾行', '👟', 'good', 'passive', '{"spdAdd":40}',   '120s 内移速 +40',    1, 22);

-- 验证:
-- SELECT code,name,price FROM cfg_item WHERE code IN ('regen','far','fleet');

-- ---------- 原 migration-v3.20-potions4.sql ----------
-- ============================================================
-- 第四批第 3 轮：鸿运药剂（收益维度——装备/图纸掉落率 ×1.5，300 秒）
-- 结算接线在 GameEngine.dropLuckMul（逐杀掉落/溢出期望/图纸三处同口径；Boss 必掉不受影响）
-- 幂等：同 code 先删后插，可重复执行
-- ============================================================
DELETE FROM `cfg_item` WHERE `code` IN ('luck');
INSERT INTO `cfg_item`
  (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('luck', '鸿运药剂', '🍀', 'potion', 500, '{"buff":"luck_pot","buffVal":1.5,"buffDur":300}', '300s 内装备与图纸掉落率 ×1.5', 1, 25);

DELETE FROM `cfg_buff` WHERE `code` IN ('luck_pot');
INSERT INTO `cfg_buff`
  (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('luck_pot', '鸿运', '🍀', 'good', 'passive', '{"dropMul":1.5}', '300s 内装备与图纸掉落率 ×1.5', 1, 23);

-- 验证:
-- SELECT code,name,price FROM cfg_item WHERE code = 'luck';

-- ---------- 原 migration-v3.21-cure.sql ----------
-- ============================================================
-- 第四批第 4 轮：解毒剂增强——兼清沉默（中毒/灼烧/沉默三清，逻辑在 GameService.usePotion）
-- 本迁移仅同步商品描述（幂等 UPDATE）
-- ============================================================
UPDATE `cfg_item` SET `desc` = '清除中毒/灼烧/沉默' WHERE `code` = 'cure';

-- ---------- 原 migration-v3.22-affixes.sql ----------
-- ============================================================
-- 第四批第 5 轮：新词条——轻盈（闪避 EVA，此前只有药剂能加）+ 活力（生命上限 maxHP，此前无词条覆盖）
-- 与 GameConst.AFFIXES 镜像同步（seed-v3.sql 口径一致）；幂等：同 code 先删后插
-- ============================================================
DELETE FROM `cfg_affix` WHERE `code` IN ('lightness','vitality');
INSERT INTO `cfg_affix`
  (`code`, `name`, `stat_key`, `stat_type`, `min_val`, `max_val`, `rarity`, `side_key`, `side_val`, `enabled`, `sort_no`) VALUES
  ('lightness', '轻盈', 'EVA',   'flat', 0.02, 0.10, 'uncommon', NULL, NULL, 1, 15),
  ('vitality',  '活力', 'maxHP', 'flat', 50,   300,  'rare',     NULL, NULL, 1, 16);

-- 验证:
-- SELECT code,name,stat_key FROM cfg_affix WHERE code IN ('lightness','vitality');

-- ---------- 原 migration-v3.23-equipment2.sql ----------
-- ============================================================
-- 第四批第 6 轮：装备区域补缺 I——沼泽（全游戏仅 1 件）与火山（无近战武器/头盔）
-- 数值口径对齐同 tier 既有装备（t2 武器主属性 18~20、t3 30 上下）；幂等：同 code 先删后插
-- ============================================================
DELETE FROM `cfg_equipment_template` WHERE `code` IN
  ('marsh_fang','marsh_robe','marsh_charm','lava_blade','lava_helm');
INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('marsh_fang',  '毒牙匕首', '🗡️', 'weapon',   2, 'swamp',   '{"PATK":"14","MATK":"8","ASPD":"0.1"}',        10, 45, '沼泽物法双修', 1, 37),
  ('marsh_robe',  '沼泽法袍', '🥻', 'chest',    2, 'swamp',   '{"MDEF":"12","REGEN":"2"}',                     10, 45, '瘴气不侵',     1, 38),
  ('marsh_charm', '瘴气护符', '🧿', 'necklace', 2, 'swamp',   '{"MATK":"6","MREGEN":"2"}',                       10, 45, '法力瘴泉',     1, 39),
  ('lava_blade',  '熔岩巨剑', '🌋', 'weapon',   3, 'volcano', '{"PATK":"34","ASPD":"-0.2","RNG":"2"}',          10, 60, '火山重剑',     1, 40),
  ('lava_helm',   '炎魔之盔', '⛑️', 'head',     3, 'volcano', '{"PDEF":"10","MDEF":"8","REGEN":"1"}',            10, 60, '炎魔覆面',     1, 41);

-- 验证:
-- SELECT code,name,slot,min_zone FROM cfg_equipment_template WHERE min_zone IN ('swamp','volcano');

-- ---------- 原 migration-v3.24-equipment3.sql ----------
-- ============================================================
-- 第四批第 7 轮：装备区域补缺 II——雪原无腿/靴、火山无副手、沼泽无靴
-- 幂等：同 code 先删后插（与第 6 轮同口径）
-- ============================================================
DELETE FROM `cfg_equipment_template` WHERE `code` IN
  ('frost_greaves','snow_boots','lava_aegis','marsh_boots');
INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('frost_greaves', '冰霜护腿', '🦵', 'legs',    3, 'snow',    '{"PDEF":"12","SPD":"5"}',                    10, 60, '踏雪无痕', 1, 42),
  ('snow_boots',    '雪原长靴', '🥾', 'boots',   3, 'snow',    '{"SPD":"30","MDEF":"4"}',                      10, 60, '雪原疾行', 1, 43),
  ('lava_aegis',    '熔火壁垒', '🛡️', 'offhand', 3, 'volcano', '{"PDEF":"16","MDEF":"6","SPD":"-5"}',          10, 60, '熔火之壁', 1, 44),
  ('marsh_boots',   '瘴气软靴', '👢', 'boots',   2, 'swamp',   '{"SPD":"20","REGEN":"1"}',                     10, 45, '泥沼轻身', 1, 45);

-- 验证:
-- SELECT min_zone, COUNT(*) FROM cfg_equipment_template WHERE enabled=1 GROUP BY min_zone;

-- ============================================================
-- §4.1 第六批内容（v3.25 起，逐轮追加；全部 DELETE+INSERT 幂等）
-- ============================================================
-- 第 1 轮：生命药剂（生命上限 ×1.3）
DELETE FROM `cfg_item` WHERE `code` IN ('vital');
INSERT INTO `cfg_item` (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('vital', '生命药剂', '💗', 'potion', 600, '{"buff":"vital_pot","buffVal":1.3,"buffDur":120}', '120s 内生命上限 ×1.3', 1, 41);
DELETE FROM `cfg_buff` WHERE `code` IN ('vital_pot');
INSERT INTO `cfg_buff` (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('vital_pot', '生命', '💗', 'good', 'passive', '{"maxHpMul":1.3}', '120s 内生命上限 ×1.3', 1, 41);
-- 第 2 轮：金钟药剂（物防魔防 +15）
DELETE FROM `cfg_item` WHERE `code` IN ('guard');
INSERT INTO `cfg_item` (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('guard', '金钟药剂', '🔔', 'potion', 500, '{"buff":"guard_pot","buffVal":15,"buffDur":120}', '120s 内物防魔防 +15', 1, 42);
DELETE FROM `cfg_buff` WHERE `code` IN ('guard_pot');
INSERT INTO `cfg_buff` (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('guard_pot', '金钟', '🔔', 'good', 'passive', '{"defFlat":15}', '120s 内物防魔防 +15', 1, 42);
-- 第 3 轮：大力药剂（物攻魔攻 +20）
DELETE FROM `cfg_item` WHERE `code` IN ('might');
INSERT INTO `cfg_item` (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('might', '大力药剂', '🏋️', 'potion', 500, '{"buff":"might_pot","buffVal":20,"buffDur":120}', '120s 内物攻魔攻 +20', 1, 43);
DELETE FROM `cfg_buff` WHERE `code` IN ('might_pot');
INSERT INTO `cfg_buff` (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('might_pot', '大力', '🏋️', 'good', 'passive', '{"atkFlat":20}', '120s 内物攻魔攻 +20', 1, 43);
-- 第 4 轮：石肤药剂（承伤 -10%）
DELETE FROM `cfg_item` WHERE `code` IN ('stone');
INSERT INTO `cfg_item` (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('stone', '石肤药剂', '🗿', 'potion', 550, '{"buff":"stone_pot","buffVal":0.9,"buffDur":120}', '120s 内受到的伤害 -10%', 1, 44);
DELETE FROM `cfg_buff` WHERE `code` IN ('stone_pot');
INSERT INTO `cfg_buff` (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('stone_pot', '石肤', '🗿', 'good', 'passive', '{"dmgTakenMul":0.9}', '120s 内受到的伤害 -10%', 1, 44);
-- 第 5 轮：荆棘药剂（反弹所受伤害 20%）
DELETE FROM `cfg_item` WHERE `code` IN ('thorn');
INSERT INTO `cfg_item` (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('thorn', '荆棘药剂', '🌵', 'potion', 650, '{"buff":"thorn_pot","buffVal":0.2,"buffDur":120}', '120s 内反弹所受伤害的 20%', 1, 45);
DELETE FROM `cfg_buff` WHERE `code` IN ('thorn_pot');
INSERT INTO `cfg_buff` (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('thorn_pot', '荆棘', '🌵', 'good', 'passive', '{"reflectMul":0.2}', '120s 内反弹所受伤害的 20%', 1, 45);
-- 第 6 轮：巨人药剂（对 Boss 伤害 +15%）
DELETE FROM `cfg_item` WHERE `code` IN ('giant');
INSERT INTO `cfg_item` (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('giant', '巨人药剂', '🦍', 'potion', 700, '{"buff":"giant_pot","buffVal":1.15,"buffDur":180}', '180s 内对 Boss 伤害 +15%', 1, 46);
DELETE FROM `cfg_buff` WHERE `code` IN ('giant_pot');
INSERT INTO `cfg_buff` (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('giant_pot', '巨人', '🦍', 'good', 'passive', '{"bossDmgMul":1.15}', '180s 内对 Boss 伤害 +15%', 1, 46);
-- 第 7 轮：聚灵药剂（蓝量上限 +300）
DELETE FROM `cfg_item` WHERE `code` IN ('spirit');
INSERT INTO `cfg_item` (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('spirit', '聚灵药剂', '🔮', 'potion', 450, '{"buff":"spirit_pot","buffVal":300,"buffDur":120}', '120s 内蓝量上限 +300', 1, 47);
DELETE FROM `cfg_buff` WHERE `code` IN ('spirit_pot');
INSERT INTO `cfg_buff` (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('spirit_pot', '聚灵', '🔮', 'good', 'passive', '{"mpFlat":300}', '120s 内蓝量上限 +300', 1, 47);

-- ============================================================
-- 整合完毕。验收参考（2026-11-03 口径）：
--   cfg_equipment_template 45 / cfg_item 药剂 25 / cfg_affix 16
--   cfg_craft 14 / cfg_skill、cfg_buff、cfg_quest、cfg_drop_rate 见库内计数
-- ============================================================
