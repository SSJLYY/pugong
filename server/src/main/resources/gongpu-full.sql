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
  `backup_ring`   LONGTEXT     NULL COMMENT '滚动备份环（JSON 数组，主档自愈）',
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

-- ---- v3.25 滚动备份环（game_save.backup_ring，第七批：主档损坏自愈用） ----
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = 'gongpu' AND table_name = 'game_save' AND column_name = 'backup_ring');
SET @sql := IF(@c = 0, 'ALTER TABLE `game_save` ADD COLUMN `backup_ring` LONGTEXT NULL COMMENT ''滚动备份环（JSON 数组，主档自愈）'' AFTER `snapshot_json`', 'SELECT 1');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

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
-- 连签计数列（第六批第 39 轮：连续签到天数，断签清零）
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = 'gongpu' AND table_name = 'account_user' AND column_name = 'claim_streak');
SET @sql := IF(@c = 0, 'ALTER TABLE `account_user` ADD COLUMN `claim_streak` INT NOT NULL DEFAULT 0 COMMENT ''连续签到天数'' AFTER `last_claim_day`', 'SELECT 1');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
-- 令牌版本列（第六批第 61 轮：改密自增，旧 JWT 立即失效）
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = 'gongpu' AND table_name = 'account_user' AND column_name = 'token_version');
SET @sql := IF(@c = 0, 'ALTER TABLE `account_user` ADD COLUMN `token_version` INT NOT NULL DEFAULT 0 COMMENT ''令牌版本(改密自增,旧令牌失效)'' AFTER `claim_streak`', 'SELECT 1');
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
-- 第 8 轮：回春药剂（回血 +100/秒）
DELETE FROM `cfg_item` WHERE `code` IN ('regen2');
INSERT INTO `cfg_item` (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('regen2', '回春药剂', '🌸', 'potion', 650, '{"buff":"regen_pot_l","buffVal":100,"buffDur":60}', '60s 内回血 +100/秒', 1, 48);
DELETE FROM `cfg_buff` WHERE `code` IN ('regen_pot_l');
INSERT INTO `cfg_buff` (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('regen_pot_l', '回春', '🌸', 'good', 'passive', '{"regenFlat":100}', '60s 内回血 +100/秒', 1, 48);
-- 第 11 轮：高阶词条「血契」（吸血上位版）
DELETE FROM `cfg_affix` WHERE `code` IN ('bloodpact');
INSERT INTO `cfg_affix`
  (`code`, `name`, `stat_key`, `stat_type`, `min_val`, `max_val`, `rarity`, `side_key`, `side_val`, `enabled`, `sort_no`) VALUES
  ('bloodpact', '血契', 'LIFESTEAL', 'flat', 0.03, 0.06, 'epic', NULL, NULL, 1, 17);
-- 第 12 轮：高阶词条「磐石」（物防上位版）
DELETE FROM `cfg_affix` WHERE `code` IN ('bedrock');
INSERT INTO `cfg_affix`
  (`code`, `name`, `stat_key`, `stat_type`, `min_val`, `max_val`, `rarity`, `side_key`, `side_val`, `enabled`, `sort_no`) VALUES
  ('bedrock', '磐石', 'PDEF', 'flat', 20, 45, 'epic', NULL, NULL, 1, 18);
-- 第 13 轮：高阶词条「猎风」（移速上位版）
DELETE FROM `cfg_affix` WHERE `code` IN ('windhunter');
INSERT INTO `cfg_affix`
  (`code`, `name`, `stat_key`, `stat_type`, `min_val`, `max_val`, `rarity`, `side_key`, `side_val`, `enabled`, `sort_no`) VALUES
  ('windhunter', '猎风', 'SPD', 'flat', 30, 60, 'epic', NULL, NULL, 1, 19);
-- 第 14 轮：高阶词条「生命之源」（生命上限上位版）
DELETE FROM `cfg_affix` WHERE `code` IN ('lifespring');
INSERT INTO `cfg_affix`
  (`code`, `name`, `stat_key`, `stat_type`, `min_val`, `max_val`, `rarity`, `side_key`, `side_val`, `enabled`, `sort_no`) VALUES
  ('lifespring', '生命之源', 'maxHP', 'flat', 300, 800, 'epic', NULL, NULL, 1, 20);
-- 第 15 轮：高阶词条「魔力源泉」（蓝量上限上位版）
DELETE FROM `cfg_affix` WHERE `code` IN ('manaspring');
INSERT INTO `cfg_affix`
  (`code`, `name`, `stat_key`, `stat_type`, `min_val`, `max_val`, `rarity`, `side_key`, `side_val`, `enabled`, `sort_no`) VALUES
  ('manaspring', '魔力源泉', 'MP', 'flat', 500, 1200, 'epic', NULL, NULL, 1, 21);
-- 第 16 轮：平原补弓「竹弓」（平原此前无远程武器）
DELETE FROM `cfg_equipment_template` WHERE `code` IN ('bamboo_bow');
INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('bamboo_bow', '竹弓', '🎋', 'bow', 0, 'plain', '{"PATK":"3","RNG":"3"}', 10, 20, '新手远程启蒙', 1, 46);
-- 第 17 轮：森林补胸甲「藤甲」（森林此前无胸甲位）
DELETE FROM `cfg_equipment_template` WHERE `code` IN ('vine_armor');
INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('vine_armor', '藤甲', '🌿', 'chest', 1, 'forest', '{"PDEF":"6","MDEF":"3","maxHP":"40"}', 10, 25, '藤蔓编织轻便耐用', 1, 47);
-- 第 18 轮：矿洞补头盔「矿工盔」（矿洞此前无头部装备）
DELETE FROM `cfg_equipment_template` WHERE `code` IN ('miner_helm');
INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('miner_helm', '矿工盔', '⛑️', 'head', 2, 'mine', '{"PDEF":"7","ACC":"0.03"}', 10, 30, '头灯照亮矿道', 1, 48);
-- 第 19 轮：沼泽补腿甲「沼泽绑腿」（沼泽此前无腿部位）
DELETE FROM `cfg_equipment_template` WHERE `code` IN ('marsh_leggings');
INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('marsh_leggings', '沼泽绑腿', '🦵', 'legs', 2, 'swamp', '{"PDEF":"9","MDEF":"8","REGEN":"1"}', 10, 40, '防水防毒', 1, 49);
-- 第 20 轮：雪原补胸甲「雪狼皮甲」（雪原此前无胸甲位）
DELETE FROM `cfg_equipment_template` WHERE `code` IN ('snow_wolf_armor');
INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('snow_wolf_armor', '雪狼皮甲', '🐺', 'chest', 3, 'snow', '{"PDEF":"16","MDEF":"10","maxHP":"120"}', 10, 60, '雪狼皮保暖坚韧', 1, 50);
-- 第 25 轮：新被动「金刚体」（双防 ×1.15）
DELETE FROM `cfg_skill` WHERE `code` IN ('diamond_body');
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('diamond_body', '金刚体', '💎', 'passive', 300, 0, 0, 0, '{"effect":"mul2","keys":["PDEF","MDEF"],"mul":1.15}', '物防与魔防 ×1.15', 1, 16);
-- 第 26 轮：新被动「幸运星」（掉落率 +10%）
DELETE FROM `cfg_skill` WHERE `code` IN ('lucky_star');
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('lucky_star', '幸运星', '⭐', 'passive', 300, 0, 0, 0, '{"effect":"drop_mul","mul":1.1}', '装备与图纸掉落率 +10%', 1, 17);
-- 第 27 轮：新被动「鹰骨」（射程 +1）
DELETE FROM `cfg_skill` WHERE `code` IN ('eagle_bone');
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('eagle_bone', '鹰骨', '🦴', 'passive', 300, 0, 0, 0, '{"effect":"add","key":"RNG","val":1}', '射程 +1', 1, 18);
-- 第 28 轮：新主动「治愈术」（即时回复 35% 生命上限）
DELETE FROM `cfg_skill` WHERE `code` IN ('heal');
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('heal', '治愈术', '💚', 'active', 300, 40, 60, 0, '{"id":"heal","healRatio":0.35}', '立即恢复 35% 生命上限', 1, 19);
-- 第 29 轮：新主动「嗜血术」（10s 吸血 +20%）
DELETE FROM `cfg_skill` WHERE `code` IN ('bloodlust');
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('bloodlust', '嗜血术', '🩸', 'active', 300, 30, 45, 10, '{"id":"bloodlust","lifesteal":0.2}', '10s 内吸血 +20%', 1, 20);
DELETE FROM `cfg_buff` WHERE `code` IN ('bloodlust');
INSERT INTO `cfg_buff` (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('bloodlust', '嗜血', '🩸', 'good', 'passive', '{"lifesteal":0.2}', '10s 内吸血 +20%', 1, 49);
-- 第 30 轮：新主动「铁壁」（8s 受击减半）
DELETE FROM `cfg_skill` WHERE `code` IN ('iron_wall');
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('iron_wall', '铁壁', '🧱', 'active', 300, 35, 40, 8, '{"id":"iron_wall","dmgTaken":0.5}', '8s 内受击减半', 1, 21);
DELETE FROM `cfg_buff` WHERE `code` IN ('iron_wall');
INSERT INTO `cfg_buff` (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('iron_wall', '铁壁', '🧱', 'good', 'passive', '{"dmgTaken":0.5}', '8s 内受击减半', 1, 50);

-- 第六批第 81 轮：雪山补头盔「雪狼兜帽」（雪山此前无头部位）
DELETE FROM `cfg_equipment_template` WHERE `code` IN ('snow_hood');
INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('snow_hood', '雪狼兜帽', '🎿', 'head', 3, 'snow', '{"PDEF":"10","MDEF":"14","maxHP":"80"}', 10, 55, '雪狼头皮缝制，御寒护头', 1, 51);
-- 第六批第 82 轮：火山补护腿「熔岩护腿」（火山此前无腿部位）
DELETE FROM `cfg_equipment_template` WHERE `code` IN ('lava_greaves');
INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('lava_greaves', '熔岩护腿', '🌋', 'legs', 4, 'volcano', '{"PDEF":"22","MDEF":"12","REGEN":"2"}', 10, 80, '熔岩冷却后的岩甲护腿', 1, 52);

-- 第七批第 23 轮：深渊裂隙装备 ×6（跨世毕业装，min_zone=abyss，tier 4）
-- 第九批第 95 轮订正：原 void_bow/void_ring 与第九批虚空界同名装备 code 冲突（后段 DELETE 覆盖），改名 abyss_bow/abyss_ring，两区各 6 件互不覆盖。
DELETE FROM `cfg_equipment_template` WHERE `code` IN ('abyss_blade','abyss_bow','rift_helm','chaos_plate','abyss_greaves','abyss_ring');
INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('abyss_blade', '渊噬之刃', '🗡️', 'weapon', 4, 'abyss', '{"PATK":"260","CRITD":"0.3"}', 10, 900, '裂隙深处凝成的噬光之刃', 1, 53),
  ('abyss_bow', '深渊长弓', '🏹', 'bow', 4, 'abyss', '{"PATK":"210","RNG":"2","ASPD":"0.15"}', 10, 900, '弦是凝固的深渊', 1, 54),
  ('rift_helm', '裂隙兜鍪', '⛑️', 'head', 4, 'abyss', '{"maxHP":"4000","PDEF":"35","MDEF":"60"}', 10, 700, '戴上它能听见裂隙的低语', 1, 55),
  ('chaos_plate', '混沌重铠', '🛡️', 'chest', 4, 'abyss', '{"maxHP":"6000","PDEF":"70","MDEF":"50"}', 10, 800, '混沌魔胎的甲壳所铸', 1, 56),
  ('abyss_greaves', '渊行胫甲', '🦿', 'legs', 4, 'abyss', '{"maxHP":"3500","PDEF":"45","SPD":"15"}', 10, 700, '行于渊上如履平地', 1, 57),
  ('abyss_ring', '深渊指环', '💍', 'ring1', 4, 'abyss', '{"MATK":"150","MREGEN":"5","EVA":"0.02"}', 10, 850, '指环内侧刻着无光君王的名字', 1, 58);

-- 第六批第 83 轮：新委托 ×2（火山终局长线 + 平原高计数挂机委托；奖励用第六批新药剂）
DELETE FROM `cfg_quest` WHERE `code` IN ('volcano_heart');
INSERT INTO `cfg_quest`
  (`code`, `name`, `emoji`, `desc`, `target_zone`, `req_json`, `target_kills`, `reward_gold`, `reward_item`, `reward_item_count`, `repeatable`, `enabled`, `sort_no`) VALUES
  ('volcano_heart', '熔火之心', '❤️‍🔥', '炎魔的心火未熄，公会要趁热打铁——在火山清剿到底', 'volcano', '{"maxHP":500000,"ATK":800}', 25, 3000, 'giant', 1, 1, 1, 12);
DELETE FROM `cfg_quest` WHERE `code` IN ('plain_veteran');
INSERT INTO `cfg_quest`
  (`code`, `name`, `emoji`, `desc`, `target_zone`, `req_json`, `target_kills`, `reward_gold`, `reward_item`, `reward_item_count`, `repeatable`, `enabled`, `sort_no`) VALUES
  ('plain_veteran', '平原老兵', '🎖️', '功成身退的老兵想看看后辈的拳头：平原连战三十场', 'plain', '{"maxHP":2000}', 30, 200, 'might', 1, 1, 1, 13);

-- 第七批第 25 轮：深渊委托 ×2（裂隙长线：初探 + 讨伐无光君王）
DELETE FROM `cfg_quest` WHERE `code` IN ('abyss_probe');
INSERT INTO `cfg_quest`
  (`code`, `name`, `emoji`, `desc`, `target_zone`, `req_json`, `target_kills`, `reward_gold`, `reward_item`, `reward_item_count`, `repeatable`, `enabled`, `sort_no`) VALUES
  ('abyss_probe', '裂隙初探', '🕳️', '裂隙刚开，公会要第一手情报：在深渊裂隙清剿虚空生物', 'abyss', '{"maxHP":2000000,"ATK":4000}', 15, 20000, 'spirit', 2, 1, 1, 14);
DELETE FROM `cfg_quest` WHERE `code` IN ('abyss_lord');
INSERT INTO `cfg_quest`
  (`code`, `name`, `emoji`, `desc`, `target_zone`, `req_json`, `target_kills`, `reward_gold`, `reward_item`, `reward_item_count`, `repeatable`, `enabled`, `sort_no`) VALUES
  ('abyss_lord', '无光君王', '👑', '渊底之主盘踞裂隙最深处，公会悬赏讨伐：深渊连战三十场', 'abyss', '{"maxHP":3000000,"ATK":6000}', 30, 50000, 'giant', 2, 1, 1, 15);

-- 第六批第 84 轮：新药剂「聚宝药剂」（300s 击杀金币 +50%，补齐金币维度）
DELETE FROM `cfg_item` WHERE `code` IN ('fortune');
INSERT INTO `cfg_item` (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('fortune', '聚宝药剂', '🧧', 'potion', 800, '{"buff":"fortune_pot","buffVal":1.5,"buffDur":300}', '300s 内击杀金币 +50%', 1, 51);
DELETE FROM `cfg_buff` WHERE `code` IN ('fortune_pot');
INSERT INTO `cfg_buff` (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('fortune_pot', '聚宝', '🧧', 'good', 'passive', '{"goldMul":1.5}', '300s 内击杀金币 +50%', 1, 51);

-- 第六批第 85 轮：新配方 ×2（给本批新装备雪狼兜帽/熔岩护腿配锻造路线）
DELETE FROM `cfg_craft` WHERE `code` IN ('c_snow_hood');
INSERT INTO `cfg_craft`
  (`code`, `name`, `emoji`, `product_code`, `mats_json`, `gold_cost`, `need_blueprint`, `enabled`, `sort_no`) VALUES
  ('c_snow_hood', '雪狼兜帽缝制', '🧵', 'snow_hood', '{"mana_dust":3,"pelt":6}', 800, 0, 1, 26);
DELETE FROM `cfg_craft` WHERE `code` IN ('c_lava_greaves');
INSERT INTO `cfg_craft`
  (`code`, `name`, `emoji`, `product_code`, `mats_json`, `gold_cost`, `need_blueprint`, `enabled`, `sort_no`) VALUES
  ('c_lava_greaves', '熔岩护腿铸造', '📐', 'lava_greaves', '{"fire_crystal":4,"ore":6}', 1500, 1, 1, 27);

-- 第七批第 26 轮：深渊配方 ×2（深渊结晶喂毕业装铸造，图纸掉落池自动纳入新配方）
DELETE FROM `cfg_craft` WHERE `code` IN ('c_abyss_blade');
INSERT INTO `cfg_craft`
  (`code`, `name`, `emoji`, `product_code`, `mats_json`, `gold_cost`, `need_blueprint`, `enabled`, `sort_no`) VALUES
  ('c_abyss_blade', '渊噬之刃铸造', '📐', 'abyss_blade', '{"abyss_crystal":6,"fire_crystal":4}', 5000, 1, 1, 28);
-- 第九批第 95 轮订正：原 c_void_ring 与第九批虚空版同名冲突（后段 DELETE 覆盖），改名 c_abyss_ring 产深渊指环。
DELETE FROM `cfg_craft` WHERE `code` IN ('c_abyss_ring');
INSERT INTO `cfg_craft`
  (`code`, `name`, `emoji`, `product_code`, `mats_json`, `gold_cost`, `need_blueprint`, `enabled`, `sort_no`) VALUES
  ('c_abyss_ring', '深渊指环铸造', '📐', 'abyss_ring', '{"abyss_crystal":4,"mana_dust":6}', 4000, 1, 1, 29);

-- 第六批第 87 轮：新怪物词条「时滞」的状态行（攻速 -20%，沼泽起入池，词条总数 12）
DELETE FROM `cfg_buff` WHERE `code` IN ('slow');
INSERT INTO `cfg_buff` (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('slow', '时滞', '🐌', 'bad', 'status', '{"aspdMul":0.8}', '出拳速度 -20%', 1, 51);

-- 第六批第 88 轮：新被动「材料嗅觉」（击杀材料掉率 50%→75%）
DELETE FROM `cfg_skill` WHERE `code` IN ('material_sense');
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('material_sense', '材料嗅觉', '👃', 'passive', 300, 0, 0, 0, '{"effect":"mat_chance","bonus":0.25}', '击杀材料掉率 +25 个百分点（50%→75%）', 1, 22);

-- 第六批第 89 轮：新主动「净化」（清除毒/灼烧/沉默/时滞，解毒剂的技能版）
DELETE FROM `cfg_skill` WHERE `code` IN ('cleanse');
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('cleanse', '净化', '🕊️', 'active', 300, 20, 30, 0, '{"id":"cleanse","cleanse":true}', '清除中毒/灼烧/沉默/时滞全部负面状态', 1, 23);

-- 第六批第 90 轮：高阶词条「疾雷」（攻速上位版，迅捷 0.02~0.10 → 0.10~0.20）
DELETE FROM `cfg_affix` WHERE `code` IN ('thunderstep');
INSERT INTO `cfg_affix`
  (`code`, `name`, `stat_key`, `stat_type`, `min_val`, `max_val`, `rarity`, `side_key`, `side_val`, `enabled`, `sort_no`) VALUES
  ('thunderstep', '疾雷', 'ASPD', 'flat', 0.10, 0.20, 'epic', NULL, NULL, 1, 22);

-- 第七批第 44 轮：新被动「血怒」（生命低于 30% 时双攻 +15%）
DELETE FROM `cfg_skill` WHERE `code` IN ('bloodrage');
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('bloodrage', '血怒', '🩸', 'passive', 300, 0, 0, 0, '{"effect":"bloodrage","mul":1.15}', '生命低于 30% 时物理与魔法攻击 +15%', 1, 24);

-- 第七批第 45 轮：新主动「冲锋」（下一击 ×3，一次性消费 buff）
DELETE FROM `cfg_skill` WHERE `code` IN ('charge');
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('charge', '冲锋', '🐎', 'active', 300, 15, 20, 0, '{"id":"charge","mul":3}', '下一击 ×3 伤害（30 秒内有效）', 1, 25);

-- 第七批第 81 轮：新药剂「狂怒药剂」（复合暴击：暴击率 +10% 且暴伤 +30%，90s）
DELETE FROM `cfg_item` WHERE `code` IN ('rage');
INSERT INTO `cfg_item` (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('rage', '狂怒药剂', '😤', 'potion', 750, '{"buff":"rage_pot","buffVal":0.10,"buffDur":90}', '90s 内暴击率 +10%、暴击伤害 +30%', 1, 52);
DELETE FROM `cfg_buff` WHERE `code` IN ('rage_pot');
INSERT INTO `cfg_buff` (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('rage_pot', '狂怒', '😤', 'good', 'passive', '{"critAdd":0.10,"critdAdd":0.30}', '90s 内暴击率 +10%、暴击伤害 +30%', 1, 52);

-- 第七批第 82 轮：新药剂「时之砂」（平直攻速 +0.2，120s，吃攻速乘区）
DELETE FROM `cfg_item` WHERE `code` IN ('timesand');
INSERT INTO `cfg_item` (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('timesand', '时之砂', '⌛', 'potion', 800, '{"buff":"sand_pot","buffVal":0.2,"buffDur":120}', '120s 内攻速 +0.2（平直加算）', 1, 53);
DELETE FROM `cfg_buff` WHERE `code` IN ('sand_pot');
INSERT INTO `cfg_buff` (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('sand_pot', '时之砂', '⌛', 'good', 'passive', '{"aspdAdd":0.2}', '120s 内攻速 +0.2', 1, 53);

-- 第七批第 83 轮：高阶词条「破军」（物攻上位版，锋利 4~16 → 16~40）
DELETE FROM `cfg_affix` WHERE `code` IN ('pojun');
INSERT INTO `cfg_affix`
  (`code`, `name`, `stat_key`, `stat_type`, `min_val`, `max_val`, `rarity`, `side_key`, `side_val`, `enabled`, `sort_no`) VALUES
  ('pojun', '破军', 'PATK', 'flat', 16, 40, 'epic', NULL, NULL, 1, 23);

-- 第七批第 84 轮：高阶词条「绝杀」（暴击率上位版，致命 0.03~0.15 → 0.15~0.25）
DELETE FROM `cfg_affix` WHERE `code` IN ('juesha');
INSERT INTO `cfg_affix`
  (`code`, `name`, `stat_key`, `stat_type`, `min_val`, `max_val`, `rarity`, `side_key`, `side_val`, `enabled`, `sort_no`) VALUES
  ('juesha', '绝杀', 'CRIT', 'flat', 0.15, 0.25, 'epic', NULL, NULL, 1, 24);

-- 第八批第 82 轮：高阶词条「无影/不息」（闪避/回复上位版，轻盈 0.02~0.10 → 0.10~0.18、坚韧 1~4 → 5~12）
DELETE FROM `cfg_affix` WHERE `code` IN ('wuying','buxi');
INSERT INTO `cfg_affix`
  (`code`, `name`, `stat_key`, `stat_type`, `min_val`, `max_val`, `rarity`, `side_key`, `side_val`, `enabled`, `sort_no`) VALUES
  ('wuying', '无影', 'EVA', 'flat', 0.10, 0.18, 'epic', NULL, NULL, 1, 25),
  ('buxi', '不息', 'REGEN', 'flat', 5, 12, 'epic', NULL, NULL, 1, 26);

-- 第九批第 82 轮：高阶词条「裂帛/灵泉」（魔攻/魔回上位版，魔力 4~16 → 16~40、回魔 1~4 → 5~12）
DELETE FROM `cfg_affix` WHERE `code` IN ('liebo','lingquan');
INSERT INTO `cfg_affix`
  (`code`, `name`, `stat_key`, `stat_type`, `min_val`, `max_val`, `rarity`, `side_key`, `side_val`, `enabled`, `sort_no`) VALUES
  ('liebo', '裂帛', 'MATK', 'flat', 16, 40, 'epic', NULL, NULL, 1, 27),
  ('lingquan', '灵泉', 'MREGEN', 'flat', 5, 12, 'epic', NULL, NULL, 1, 28);

-- 第七批第 85 轮：新被动「处决者」（Boss 生命低于 30% 时对其伤害 +25%）
DELETE FROM `cfg_skill` WHERE `code` IN ('executioner');
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('executioner', '处决者', '🪓', 'passive', 400, 0, 0, 0, '{"effect":"executioner","mul":1.25}', 'Boss 生命低于 30% 时对其伤害 +25%', 1, 26);

-- 第七批第 86 轮：新主动「迟缓咒」（当前怪物攻速 -30%，持续 8 秒）
DELETE FROM `cfg_skill` WHERE `code` IN ('hex_slow');
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('hex_slow', '迟缓咒', '🐌', 'active', 350, 20, 25, 8, '{"id":"hex_slow","aspdMul":0.7}', '当前怪物攻速 -30%，持续 8 秒', 1, 27);

-- 第七批第 89 轮：新委托 ×2（火山/雪原各补一张高阶单）
DELETE FROM `cfg_quest` WHERE `code` IN ('volcano_core');
INSERT INTO `cfg_quest`
  (`code`, `name`, `emoji`, `desc`, `target_zone`, `req_json`, `target_kills`, `reward_gold`, `reward_item`, `reward_item_count`, `repeatable`, `enabled`, `sort_no`) VALUES
  ('volcano_core', '熔火之心', '🌋', '熔火平原的怒焰需要平息：于火山猎杀四十只魔物', 'volcano', '{"maxHP":500000,"ATK":2500}', 40, 30000, 'guard', 2, 1, 1, 17);
DELETE FROM `cfg_quest` WHERE `code` IN ('snow_throne');
INSERT INTO `cfg_quest`
  (`code`, `name`, `emoji`, `desc`, `target_zone`, `req_json`, `target_kills`, `reward_gold`, `reward_item`, `reward_item_count`, `repeatable`, `enabled`, `sort_no`) VALUES
  ('snow_throne', '雪原王座', '👑', '雪狼王盘踞王座已久：于雪原猎杀五十只魔物，向公会证明你的实力', 'snow', '{"maxHP":200000,"ATK":1500}', 50, 22000, 'fortune', 1, 1, 1, 18);

-- 第八批第 23 轮：星界委托 ×2 + 星界配方 ×2
DELETE FROM `cfg_quest` WHERE `code` IN ('star_patrol');
INSERT INTO `cfg_quest`
  (`code`, `name`, `emoji`, `desc`, `target_zone`, `req_json`, `target_kills`, `reward_gold`, `reward_item`, `reward_item_count`, `repeatable`, `enabled`, `sort_no`) VALUES
  ('star_patrol', '回廊巡星', '🌌', '群星回廊的秩序需要维护：于星界猎杀四十只魔物', 'star', '{"maxHP":20000000,"ATK":12000}', 40, 80000, 'rage', 2, 1, 1, 19);
DELETE FROM `cfg_quest` WHERE `code` IN ('star_heart');
INSERT INTO `cfg_quest`
  (`code`, `name`, `emoji`, `desc`, `target_zone`, `req_json`, `target_kills`, `reward_gold`, `reward_item`, `reward_item_count`, `repeatable`, `enabled`, `sort_no`) VALUES
  ('star_heart', '噬光之心', '💜', '星核主宰的心跳震得回廊发颤：于星界猎杀六十只魔物，向公会证明群星为你让路', 'star', '{"maxHP":20000000,"ATK":12000}', 60, 120000, 'pierce', 2, 1, 1, 20);

-- 第八批第 84 轮：星界委托补强 ×2（奖励接新药剂鹰目/生机）
DELETE FROM `cfg_quest` WHERE `code` IN ('star_dust');
INSERT INTO `cfg_quest`
  (`code`, `name`, `emoji`, `desc`, `target_zone`, `req_json`, `target_kills`, `reward_gold`, `reward_item`, `reward_item_count`, `repeatable`, `enabled`, `sort_no`) VALUES
  ('star_dust', '星尘拾遗', '✨', '坠落的星尘撒满回廊：于星界猎杀三十只魔物，替公会把它们拾回来', 'star', '{"maxHP":20000000,"ATK":12000}', 30, 60000, 'focus', 2, 1, 1, 21);
DELETE FROM `cfg_quest` WHERE `code` IN ('void_feast');
INSERT INTO `cfg_quest`
  (`code`, `name`, `emoji`, `desc`, `target_zone`, `req_json`, `target_kills`, `reward_gold`, `reward_item`, `reward_item_count`, `repeatable`, `enabled`, `sort_no`) VALUES
  ('void_feast', '虚空盛宴', '🕳️', '虚空行者成群结队地赴宴：于星界猎杀八十只魔物，让这场盛宴散场', 'star', '{"maxHP":20000000,"ATK":12000}', 80, 160000, 'vigor', 2, 1, 1, 22);
DELETE FROM `cfg_craft` WHERE `code` IN ('c_star_blade');
INSERT INTO `cfg_craft`
  (`code`, `name`, `emoji`, `product_code`, `mats_json`, `gold_cost`, `need_blueprint`, `enabled`, `sort_no`) VALUES
  ('c_star_blade', '星陨之刃铸造', '📐', 'star_blade', '{"star_shard":6,"abyss_crystal":4}', 12000, 1, 1, 30);
DELETE FROM `cfg_craft` WHERE `code` IN ('c_quasar_ring');
INSERT INTO `cfg_craft`
  (`code`, `name`, `emoji`, `product_code`, `mats_json`, `gold_cost`, `need_blueprint`, `enabled`, `sort_no`) VALUES
  ('c_quasar_ring', '类星指环铸造', '📐', 'quasar_ring', '{"star_shard":4,"mana_dust":8}', 10000, 1, 1, 31);

-- 第八批第 85 轮：星界配方补强 ×2（兜鍪/重铠）
DELETE FROM `cfg_craft` WHERE `code` IN ('c_nebula_helm');
INSERT INTO `cfg_craft`
  (`code`, `name`, `emoji`, `product_code`, `mats_json`, `gold_cost`, `need_blueprint`, `enabled`, `sort_no`) VALUES
  ('c_nebula_helm', '星云兜鍪铸造', '📐', 'nebula_helm', '{"star_shard":5,"mana_dust":6}', 11000, 1, 1, 32);
DELETE FROM `cfg_craft` WHERE `code` IN ('c_galaxy_plate');
INSERT INTO `cfg_craft`
  (`code`, `name`, `emoji`, `product_code`, `mats_json`, `gold_cost`, `need_blueprint`, `enabled`, `sort_no`) VALUES
  ('c_galaxy_plate', '银河重铠铸造', '📐', 'galaxy_plate', '{"star_shard":7,"abyss_crystal":3}', 13000, 1, 1, 33);

-- 第八批第 22 轮：星界毕业装 ×6（tier 4，min_zone=star，底子约为深渊装 2.2 倍）
DELETE FROM `cfg_equipment_template` WHERE `code` IN ('star_blade','comet_bow','nebula_helm','galaxy_plate','meteor_greaves','quasar_ring');
INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('star_blade', '星陨之刃', '☄️', 'weapon', 4, 'star', '{"PATK":"580","CRITD":"0.35"}', 10, 2400, '一颗熄灭恒星的全部重量', 1, 59),
  ('comet_bow', '彗星长弓', '🌠', 'bow', 4, 'star', '{"PATK":"470","RNG":"2","ASPD":"0.18"}', 10, 2400, '箭出如彗星拖尾', 1, 60),
  ('nebula_helm', '星云兜鍪', '🌌', 'head', 4, 'star', '{"maxHP":"9000","PDEF":"80","MDEF":"130"}', 10, 1800, '兜鍪里缓缓旋转着一片星云', 1, 61),
  ('galaxy_plate', '银河重铠', '🛡️', 'chest', 4, 'star', '{"maxHP":"13000","PDEF":"150","MDEF":"110"}', 10, 2100, '把一条旋臂穿在身上', 1, 62),
  ('meteor_greaves', '流星胫甲', '🦿', 'legs', 4, 'star', '{"maxHP":"8000","PDEF":"100","SPD":"18"}', 10, 1800, '踏步即是坠落的流星', 1, 63),
  ('quasar_ring', '类星指环', '💍', 'ring1', 4, 'star', '{"MATK":"330","MREGEN":"8","EVA":"0.03"}', 10, 2200, '指环中央是一颗微缩的类星体', 1, 64);

-- 第八批第 86 轮：星界装备补缺 ×2（项链/战靴，星界此前无此二部位）
DELETE FROM `cfg_equipment_template` WHERE `code` IN ('pulsar_necklace','comet_boots');
INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('pulsar_necklace', '脉冲星项链', '📿', 'necklace', 4, 'star', '{"MATK":"260","CRIT":"0.05","MREGEN":"5"}', 10, 2300, '每一次脉冲都与你的心跳同步', 1, 65),
  ('comet_boots', '彗尾战靴', '🥾', 'boots', 4, 'star', '{"maxHP":"6000","PDEF":"70","SPD":"25","EVA":"0.03"}', 10, 1900, '鞋底拖着一条不熄的彗尾', 1, 66);

-- 第八批第 15 轮：新药剂破甲药剂
DELETE FROM `cfg_item` WHERE `code` IN ('pierce');
INSERT INTO `cfg_item`
  (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('pierce', '破甲药剂', '🏹', 'potion', 600, '{"buff":"pierce_pot","buffVal":0.2,"buffDur":60}', '60s 内攻击无视目标 20% 防御', 1, 54);

-- 第八批第 81 轮：新药剂鹰目/生机（命中与持续回复维度补强）
DELETE FROM `cfg_item` WHERE `code` IN ('focus','vigor');
INSERT INTO `cfg_item`
  (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('focus', '鹰目药剂', '🎯', 'potion', 500, '{"buff":"focus_pot","buffVal":0.12,"buffDur":90}', '90s 内命中 +12%', 1, 55),
  ('vigor', '生机药剂', '🌿', 'potion', 550, '{"buff":"vigor_pot","buffVal":150,"buffDur":60}', '60s 内每秒回复 +150', 1, 56);
DELETE FROM `cfg_buff` WHERE `code` IN ('focus_pot','vigor_pot');
INSERT INTO `cfg_buff`
  (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('focus_pot', '鹰目', '🎯', 'good', 'passive', '{"accAdd":0.12}', '90s 内命中 +12%', 1, 55),
  ('vigor_pot', '生机', '🌿', 'good', 'passive', '{"regenAdd":150}', '60s 内每秒回复 +150', 1, 56);

-- 第八批第 14 轮：新主动连环掌
DELETE FROM `cfg_skill` WHERE `code` IN ('flurry');
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('flurry', '连环掌', '🤲', 'active', 380, 25, 30, 0, '{"id":"flurry","hits":30}', '接下来 30 次命中各追加一次同额命中', 1, 29);

-- 第八批第 13 轮：新被动破甲专精
DELETE FROM `cfg_skill` WHERE `code` IN ('armor_pierce');
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('armor_pierce', '破甲专精', '🏹', 'passive', 450, 0, 0, 0, '{"effect":"armor_pierce","ignore":0.2}', '攻击无视目标 20% 的物防与魔防', 1, 28);

-- 第八批第 83 轮：新被动宝石嗅觉
DELETE FROM `cfg_skill` WHERE `code` IN ('gem_sniffer');
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('gem_sniffer', '宝石嗅觉', '👃', 'passive', 420, 0, 0, 0, '{"effect":"gem_sniffer","mul":1.5}', '词条怪的宝石掉率 +50%（10%→15%）', 1, 30);

-- 第九批第 15 轮：虚空界配方 ×2（sort 34-35）
DELETE FROM `cfg_craft` WHERE `code` IN ('c_void_blade');
INSERT INTO `cfg_craft`
  (`code`, `name`, `emoji`, `product_code`, `mats_json`, `gold_cost`, `need_blueprint`, `enabled`, `sort_no`) VALUES
  ('c_void_blade', '无相之刃铸造', '📐', 'void_blade', '{"void_shard":6,"star_shard":4}', 20000, 1, 1, 34);
DELETE FROM `cfg_craft` WHERE `code` IN ('c_void_ring');
INSERT INTO `cfg_craft`
  (`code`, `name`, `emoji`, `product_code`, `mats_json`, `gold_cost`, `need_blueprint`, `enabled`, `sort_no`) VALUES
  ('c_void_ring', '无相指环铸造', '📐', 'void_ring', '{"void_shard":5,"star_shard":3}', 18000, 1, 1, 35);

-- 第九批第 85 轮：虚空套装余件配方 ×2（兜帽/法袍）
DELETE FROM `cfg_craft` WHERE `code` IN ('c_void_hood');
INSERT INTO `cfg_craft`
  (`code`, `name`, `emoji`, `product_code`, `mats_json`, `gold_cost`, `need_blueprint`, `enabled`, `sort_no`) VALUES
  ('c_void_hood', '无相兜帽铸造', '📐', 'void_hood', '{"void_shard":5,"star_shard":3}', 16000, 1, 1, 36);
DELETE FROM `cfg_craft` WHERE `code` IN ('c_void_robe');
INSERT INTO `cfg_craft`
  (`code`, `name`, `emoji`, `product_code`, `mats_json`, `gold_cost`, `need_blueprint`, `enabled`, `sort_no`) VALUES
  ('c_void_robe', '虚空法袍铸造', '📐', 'void_robe', '{"void_shard":6,"star_shard":4}', 19000, 1, 1, 37);

-- 第九批第 14 轮：虚空界委托 ×2（sort 23-24）
DELETE FROM `cfg_quest` WHERE `code` IN ('void_patrol');
INSERT INTO `cfg_quest`
  (`code`, `name`, `emoji`, `desc`, `target_zone`, `req_json`, `target_kills`, `reward_gold`, `reward_item`, `reward_item_count`, `repeatable`, `enabled`, `sort_no`) VALUES
  ('void_patrol', '无光巡守', '🌀', '虚空界没有秩序可言——你的拳头就是秩序：于虚空界猎杀四十只魔物', 'void', '{"maxHP":200000000,"ATK":40000}', 40, 300000, 'rage', 3, 1, 1, 23);
DELETE FROM `cfg_quest` WHERE `code` IN ('void_heart');
INSERT INTO `cfg_quest`
  (`code`, `name`, `emoji`, `desc`, `target_zone`, `req_json`, `target_kills`, `reward_gold`, `reward_item`, `reward_item_count`, `repeatable`, `enabled`, `sort_no`) VALUES
  ('void_heart', '无相之心', '💜', '无相天魔没有心——公会偏要你带一颗回来：于虚空界猎杀六十只魔物', 'void', '{"maxHP":200000000,"ATK":40000}', 60, 450000, 'pierce', 3, 1, 1, 24);

-- 第九批第 84 轮：高阶委托 ×2（星界长猎/虚空拾尘）
DELETE FROM `cfg_quest` WHERE `code` IN ('star_longhunt');
INSERT INTO `cfg_quest`
  (`code`, `name`, `emoji`, `desc`, `target_zone`, `req_json`, `target_kills`, `reward_gold`, `reward_item`, `reward_item_count`, `repeatable`, `enabled`, `sort_no`) VALUES
  ('star_longhunt', '星尘长猎', '🌠', '回廊深处的星尘兽群泛滥成灾：于星界猎杀一百二十只魔物', 'star', '{"maxHP":20000000,"ATK":12000}', 120, 200000, 'stardust', 2, 1, 1, 25);
DELETE FROM `cfg_quest` WHERE `code` IN ('void_dust');
INSERT INTO `cfg_quest`
  (`code`, `name`, `emoji`, `desc`, `target_zone`, `req_json`, `target_kills`, `reward_gold`, `reward_item`, `reward_item_count`, `repeatable`, `enabled`, `sort_no`) VALUES
  ('void_dust', '虚空拾尘', '🧹', '虚空尘埃里藏着凝固的星光：于虚空界猎杀八十只魔物', 'void', '{"maxHP":200000000,"ATK":40000}', 80, 600000, 'stardust', 3, 1, 1, 26);

-- 第九批第 83 轮：新被动招财手（原名点金手与天赋重名，第 89 轮订正）
DELETE FROM `cfg_skill` WHERE `code` IN ('goldtouch');
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('goldtouch', '招财手', '🪙', 'passive', 800, 0, 0, 0, '{"effect":"goldtouch","mul":1.15}', '击杀金币 +15%', 1, 33);

-- 第九批第 24 轮：新被动宠物亲和
DELETE FROM `cfg_skill` WHERE `code` IN ('pet_bond');
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('pet_bond', '宠物亲和', '🐾', 'passive', 500, 0, 0, 0, '{"effect":"pet_bond","mul":1.3}', '出战宠物的面板加成 +30%', 1, 32);

-- 第九批第 25 轮：新药剂虚空精华
DELETE FROM `cfg_item` WHERE `code` IN ('void');
INSERT INTO `cfg_item`
  (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('void', '虚空精华', '🌀', 'potion', 900, '{"buff":"void_pot","buffVal":0.30,"buffDur":60}', '60s 内暴击伤害 +30%、命中 +5%', 1, 57);
DELETE FROM `cfg_buff` WHERE `code` IN ('void_pot');
INSERT INTO `cfg_buff`
  (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('void_pot', '虚空精华', '🌀', 'good', 'passive', '{"critdAdd":0.30,"accAdd":0.05}', '60s 内暴击伤害 +30%、命中 +5%', 1, 57);

-- 第十批第 6 轮：宠物零食（使用后出战宠物 +60 经验，usePotion 特殊分支）
DELETE FROM `cfg_item` WHERE `code` IN ('pet_treat');
INSERT INTO `cfg_item`
  (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('pet_treat', '宠物零食', '🍬', 'potion', 300, '{}', '喂给出战宠物，经验 +60', 1, 59);

-- 第九批第 81 轮：新药剂星尘甘露
DELETE FROM `cfg_item` WHERE `code` IN ('stardust');
INSERT INTO `cfg_item`
  (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('stardust', '星尘甘露', '✨', 'potion', 1200, '{"buff":"stardust_pot","buffVal":0.06,"buffDur":90}', '90s 内暴击 +6%、攻速 +1.5', 1, 58);
DELETE FROM `cfg_buff` WHERE `code` IN ('stardust_pot');
INSERT INTO `cfg_buff`
  (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('stardust_pot', '星尘甘露', '✨', 'good', 'passive', '{"critAdd":0.06,"aspdAdd":1.5}', '90s 内暴击 +6%、攻速 +1.5', 1, 58);

-- 第九批第 23 轮：新主动虚空斩
DELETE FROM `cfg_skill` WHERE `code` IN ('void_slash');
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('void_slash', '虚空斩', '🌀', 'active', 1200, 40, 45, 0, '{"id":"void_slash","mul":4}', '下一击 ×4 伤害（30 秒内有效，撕裂空间的一斩）', 1, 31);

-- 第九批第 13 轮：虚空界装备 ×6（tier 4，sort 67-72）
DELETE FROM `cfg_equipment_template` WHERE `code` IN ('void_blade','void_bow','void_hood','void_robe','void_treads','void_ring');
INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('void_blade', '无相之刃', '🌀', 'weapon', 4, 'void', '{"PATK":"1050","CRITD":"0.4"}', 10, 4200, '没有形状的刃，斩断的是存在本身', 1, 67),
  ('void_bow', '噬空长弓', '🏹', 'bow', 4, 'void', '{"PATK":"860","RNG":"2","ASPD":"0.22"}', 10, 4200, '箭矢在离弦前就已经消失', 1, 68),
  ('void_hood', '无相兜帽', '🎩', 'head', 4, 'void', '{"maxHP":"16000","PDEF":"140","MDEF":"230"}', 10, 3200, '戴上它的人，脸也成了虚空', 1, 69),
  ('void_robe', '虚空法袍', '🥋', 'chest', 4, 'void', '{"maxHP":"23000","PDEF":"260","MDEF":"200"}', 10, 3600, '把一片无穿在身上', 1, 70),
  ('void_treads', '踏虚胫甲', '🦿', 'legs', 4, 'void', '{"maxHP":"14000","PDEF":"180","SPD":"22"}', 10, 3200, '每一步都踩在不存在的地面上', 1, 71),
  ('void_ring', '无相指环', '💍', 'ring1', 4, 'void', '{"MATK":"600","MREGEN":"10","EVA":"0.04"}', 10, 3800, '指环内侧刻着一个不存在的名字', 1, 72);

-- 第九批第 86 轮：高阶装备 ×2（星核之盾/无相之瞳）
DELETE FROM `cfg_equipment_template` WHERE `code` IN ('star_aegis','void_eye_charm');
INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('star_aegis', '星核之盾', '🛡️', 'offhand', 4, 'star', '{"maxHP":"24000","PDEF":"320","MDEF":"280"}', 10, 4000, '以星核碎片锻成的盾，盾面映着整条回廊', 1, 73),
  ('void_eye_charm', '无相之瞳', '👁️', 'necklace', 4, 'void', '{"MATK":"700","CRIT":"0.05","MREGEN":"12"}', 10, 4200, '戴上它，你能看见虚空在看你', 1, 74);

-- 第十批第 13 轮：混沌界装备 ×6（tier 4，sort 75-80）
DELETE FROM `cfg_equipment_template` WHERE `code` IN ('chaos_blade','chaos_bow','chaos_hood','chaos_robe','chaos_treads','chaos_ring');
INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('chaos_blade', '混沌之刃', '🗡️', 'weapon', 4, 'chaos', '{"PATK":"2400","CRITD":"0.3"}', 10, 12000, '刃口每秒都在重组自己的形状', 1, 75),
  ('chaos_bow', '混沌长弓', '🏹', 'bow', 4, 'chaos', '{"PATK":"1900","RNG":"2","ASPD":"0.22"}', 10, 12000, '箭会先命中，然后再被射出', 1, 76),
  ('chaos_hood', '混沌兜帽', '🎩', 'head', 4, 'chaos', '{"maxHP":"90000","PDEF":"700","MDEF":"980"}', 10, 10000, '戴上它，你的影子开始有自己的想法', 1, 77),
  ('chaos_robe', '混沌法袍', '🥋', 'chest', 4, 'chaos', '{"maxHP":"120000","PDEF":"900","MDEF":"1100"}', 10, 11000, '把一片无序穿在身上', 1, 78),
  ('chaos_treads', '混沌胫甲', '🦿', 'legs', 4, 'chaos', '{"maxHP":"70000","PDEF":"800","SPD":"40"}', 10, 10000, '每一步都踩在还没出现的地面上', 1, 79),
  ('chaos_ring', '混沌指环', '💍', 'ring1', 4, 'chaos', '{"MATK":"2100","MREGEN":"40","EVA":"0.03"}', 10, 11500, '指环内侧刻着明天的日期', 1, 80);

-- 第十批第 14 轮：混沌界委托 ×2
DELETE FROM `cfg_quest` WHERE `code` IN ('chaos_patrol','chaos_heart');
INSERT INTO `cfg_quest`
  (`code`, `name`, `emoji`, `desc`, `target_zone`, `req_json`, `target_kills`, `reward_gold`, `reward_item`, `reward_item_count`, `repeatable`, `enabled`, `sort_no`) VALUES
  ('chaos_patrol', '混沌巡狩', '🌪️', '无序在混沌界边缘膨胀：于混沌界猎杀一百只魔物', 'chaos', '{"maxHP":2000000000,"ATK":150000}', 100, 1500000, 'pet_treat', 5, 1, 1, 27),
  ('chaos_heart', '混沌之心', '💠', '直面无序之源的爪牙：于混沌界猎杀六十只魔物', 'chaos', '{"maxHP":2000000000,"ATK":150000}', 60, 3000000, 'stardust', 3, 1, 1, 28);

-- 第十批第 23 轮：新主动时停 + 新被动时之体
DELETE FROM `cfg_skill` WHERE `code` IN ('time_stop','chrono_body');
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('time_stop', '时停', '⌛', 'active', 1500, 60, 90, 10, '{"id":"time_stop"}', '10s 内怪物攻速减半（时间在你这边冻结）', 1, 34),
  ('chrono_body', '时之体', '⌛', 'passive', 800, 0, 0, 0, '{"id":"chrono_body"}', '攻速 +0.08（时间在你皮肤上流速不同）', 1, 35);

-- 第十批第 25 轮：新药剂混沌精华
DELETE FROM `cfg_item` WHERE `code` IN ('chaos');
INSERT INTO `cfg_item`
  (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('chaos', '混沌精华', '🌪️', 'potion', 1500, '{"buff":"chaos_pot","buffVal":0.40,"buffDur":60}', '60s 内暴击伤害 +40%、闪避 +5%', 1, 60);
DELETE FROM `cfg_buff` WHERE `code` IN ('chaos_pot');
INSERT INTO `cfg_buff`
  (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('chaos_pot', '混沌精华', '🌪️', 'good', 'passive', '{"critdAdd":0.40,"evaAdd":0.05}', '60s 内暴击伤害 +40%、闪避 +5%', 1, 60);

-- 第十批第 81 轮：新药剂凤凰之血
DELETE FROM `cfg_item` WHERE `code` IN ('phoenix');
INSERT INTO `cfg_item`
  (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('phoenix', '凤凰之血', '🦅', 'potion', 2000, '{"buff":"phoenix_pot","buffVal":0.50,"buffDur":60}', '60s 内暴击伤害 +50%、吸血 +5%', 1, 61);
DELETE FROM `cfg_buff` WHERE `code` IN ('phoenix_pot');
INSERT INTO `cfg_buff`
  (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('phoenix_pot', '凤凰之血', '🦅', 'good', 'passive', '{"critdAdd":0.50,"lifestealAdd":0.05}', '60s 内暴击伤害 +50%、吸血 +5%', 1, 61);

-- 第十批第 82 轮：新词条 ×2（混沌/永恒，史诗池）
DELETE FROM `cfg_affix` WHERE `code` IN ('chaos_aff','eternal_aff');
INSERT INTO `cfg_affix`
  (`code`, `name`, `stat_key`, `stat_type`, `min_val`, `max_val`, `rarity`, `side_key`, `side_val`, `enabled`, `sort_no`) VALUES
  ('chaos_aff', '混沌', 'PATK', 'flat', 40, 90, 'epic', NULL, NULL, 1, 29),
  ('eternal_aff', '永恒', 'maxHP', 'flat', 8000, 20000, 'epic', NULL, NULL, 1, 30);

-- 第十批第 83 轮：新被动混沌亲和
DELETE FROM `cfg_skill` WHERE `code` IN ('chaos_affinity');
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('chaos_affinity', '混沌亲和', '🌪️', 'passive', 1200, 0, 0, 0, '{"id":"chaos_affinity"}', '对混沌界怪物伤害 +25%（与无序共鸣）', 1, 36);

-- 第十批第 84 轮：混沌界长线委托 ×2
DELETE FROM `cfg_quest` WHERE `code` IN ('chaos_longhunt','chaos_deep');
INSERT INTO `cfg_quest`
  (`code`, `name`, `emoji`, `desc`, `target_zone`, `req_json`, `target_kills`, `reward_gold`, `reward_item`, `reward_item_count`, `repeatable`, `enabled`, `sort_no`) VALUES
  ('chaos_longhunt', '混沌长猎', '🌪️', '无序的潮汐一波接一波：于混沌界猎杀二百只魔物', 'chaos', '{"maxHP":2000000000,"ATK":150000}', 200, 3000000, 'chaos', 2, 1, 1, 29),
  ('chaos_deep', '深入无序', '🌀', '走到规则崩坏的尽头再回来：于混沌界猎杀一百五十只魔物', 'chaos', '{"maxHP":2000000000,"ATK":150000}', 150, 2400000, 'pet_treat', 5, 1, 1, 30);

-- 第十批第 85 轮：混沌套装余件配方补全 ×2
DELETE FROM `cfg_craft` WHERE `code` IN ('c_chaos_hood','c_chaos_robe');
INSERT INTO `cfg_craft`
  (`code`, `name`, `emoji`, `product_code`, `mats_json`, `gold_cost`, `need_blueprint`, `enabled`, `sort_no`) VALUES
  ('c_chaos_hood', '混沌兜帽织造', '📐', 'chaos_hood', '{"chaos_core":4,"void_shard":2}', 42000, 1, 1, 40),
  ('c_chaos_robe', '混沌法袍织造', '📐', 'chaos_robe', '{"chaos_core":6,"star_shard":3}', 55000, 1, 1, 41);

-- 第十批第 86 轮：高阶装备 ×2（混沌壁垒/无序之瞳）
DELETE FROM `cfg_equipment_template` WHERE `code` IN ('chaos_aegis','chaos_eye');
INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('chaos_aegis', '混沌壁垒', '🛡️', 'offhand', 4, 'chaos', '{"maxHP":"150000","PDEF":"1500","MDEF":"1300"}', 10, 13000, '以混沌核心锻成的壁垒，形状从未重复', 1, 81),
  ('chaos_eye', '无序之瞳', '👁️', 'necklace', 4, 'chaos', '{"MATK":"2400","CRIT":"0.05","MREGEN":"45"}', 10, 12500, '戴上它，时间开始倒着流', 1, 82);

-- 第十批第 15 轮：混沌套装余件配方 ×2
DELETE FROM `cfg_craft` WHERE `code` IN ('c_chaos_blade','c_chaos_ring');
INSERT INTO `cfg_craft`
  (`code`, `name`, `emoji`, `product_code`, `mats_json`, `gold_cost`, `need_blueprint`, `enabled`, `sort_no`) VALUES
  ('c_chaos_blade', '混沌之刃铸造', '📐', 'chaos_blade', '{"chaos_core":5,"void_shard":3}', 50000, 1, 1, 38),
  ('c_chaos_ring', '混沌指环铸造', '📐', 'chaos_ring', '{"chaos_core":5,"star_shard":4}', 48000, 1, 1, 39);

-- 第十一批第 23 轮：新主动万剑归宗 + 新被动鸿蒙体
DELETE FROM `cfg_skill` WHERE `code` IN ('sword_rain','hongmeng_body');
INSERT INTO `cfg_skill`
  (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('sword_rain', '万剑归宗', '🗡️', 'active', 2500, 80, 120, 0, '{"id":"sword_rain"}', '下一击 ×5 伤害（万剑齐发，归于一击）', 1, 36),
  ('hongmeng_body', '鸿蒙体', '🌌', 'passive', 1500, 0, 0, 0, '{"id":"hongmeng_body"}', '攻速 +0.10（紫气淬体，出手如太初之风）', 1, 37);

-- 第十一批第 25 轮：新药剂鸿蒙灵液
DELETE FROM `cfg_item` WHERE `code` IN ('hongmeng');
INSERT INTO `cfg_item`
  (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('hongmeng', '鸿蒙灵液', '🌌', 'potion', 5000, '{"buff":"hongmeng_pot","buffVal":0.60,"buffDur":60}', '60s 内暴击伤害 +60%、吸血 +6%', 1, 62);
DELETE FROM `cfg_buff` WHERE `code` IN ('hongmeng_pot');
INSERT INTO `cfg_buff`
  (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('hongmeng_pot', '鸿蒙灵液', '🌌', 'good', 'passive', '{"critdAdd":0.60,"lifestealAdd":0.06}', '60s 内暴击伤害 +60%、吸血 +6%', 1, 62);

-- 第十一批第 13 轮：鸿蒙界装备 ×6（tier 4，sort 83-88）
DELETE FROM `cfg_equipment_template` WHERE `code` IN ('hongmeng_blade','hongmeng_bow','hongmeng_hood','hongmeng_robe','hongmeng_treads','hongmeng_ring');
INSERT INTO `cfg_equipment_template`
  (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('hongmeng_blade', '鸿蒙之刃', '🗡️', 'weapon', 4, 'hongmeng', '{"PATK":"6500","CRITD":"0.35"}', 10, 40000, '开天辟地的第一缕锋芒', 1, 83),
  ('hongmeng_bow', '鸿蒙长弓', '🏹', 'bow', 4, 'hongmeng', '{"PATK":"5200","RNG":"2","ASPD":"0.25"}', 10, 40000, '弦上搭着尚未诞生的星辰', 1, 84),
  ('hongmeng_hood', '鸿蒙兜帽', '🎩', 'head', 4, 'hongmeng', '{"maxHP":"260000","PDEF":"1900","MDEF":"2600"}', 10, 34000, '戴上它，你听见太初的心跳', 1, 85),
  ('hongmeng_robe', '鸿蒙法袍', '🥋', 'chest', 4, 'hongmeng', '{"maxHP":"340000","PDEF":"2400","MDEF":"3000"}', 10, 36000, '以紫气织成的袍，万法不侵', 1, 86),
  ('hongmeng_treads', '鸿蒙胫甲', '🦿', 'legs', 4, 'hongmeng', '{"maxHP":"200000","PDEF":"2100","SPD":"60"}', 10, 34000, '踏碎虚空，如履太初', 1, 87),
  ('hongmeng_ring', '鸿蒙指环', '💍', 'ring1', 4, 'hongmeng', '{"MATK":"5800","MREGEN":"110","EVA":"0.04"}', 10, 38000, '指环里封着一整个未开的天地', 1, 88);

-- 第十一批第 14 轮：鸿蒙界委托 ×2
DELETE FROM `cfg_quest` WHERE `code` IN ('hongmeng_patrol','hongmeng_heart');
INSERT INTO `cfg_quest`
  (`code`, `name`, `emoji`, `desc`, `target_zone`, `req_json`, `target_kills`, `reward_gold`, `reward_item`, `reward_item_count`, `repeatable`, `enabled`, `sort_no`) VALUES
  ('hongmeng_patrol', '鸿蒙巡狩', '🌌', '太初之地凶兽横行：于鸿蒙界猎杀一百只魔物', 'hongmeng', '{"maxHP":50000000000,"ATK":400000}', 100, 6000000, 'chaos_core', 2, 1, 1, 31),
  ('hongmeng_heart', '鸿蒙之心', '💠', '直面太初的守卫：于鸿蒙界猎杀六十只魔物', 'hongmeng', '{"maxHP":50000000000,"ATK":400000}', 60, 12000000, 'star_shard', 5, 1, 1, 32);

-- 第十一批第 15 轮：鸿蒙装备配方 ×2
DELETE FROM `cfg_craft` WHERE `code` IN ('c_hongmeng_blade','c_hongmeng_ring');
INSERT INTO `cfg_craft`
  (`code`, `name`, `emoji`, `product_code`, `mats_json`, `gold_cost`, `need_blueprint`, `enabled`, `sort_no`) VALUES
  ('c_hongmeng_blade', '鸿蒙之刃铸造', '📐', 'hongmeng_blade', '{"hongmeng_qi":6,"chaos_core":4}', 500000, 1, 1, 42),
  ('c_hongmeng_ring', '鸿蒙指环铸造', '📐', 'hongmeng_ring', '{"hongmeng_qi":4,"star_shard":6}', 400000, 1, 1, 43);

-- ============================================================
-- 整合完毕。验收参考（2026-11-04 第十批 100 轮收官口径）：
--   cfg_equipment_template 82 / cfg_item 药剂 44 / cfg_affix 30
--   cfg_craft 30 / cfg_quest 30 / cfg_skill 36（主动 15 + 被动 21）
--   怪物词条 23（代码 GameConst.MOB_TRAITS）/ 成就 98（代码 Achievements）
--   天赋 13 / 套装 9（代码 GameConst.TALENTS / Sets）/ 区域 11（含混沌界）
--   宝石 12 种（代码 GameConst.GEMS）/ 图鉴 38（代码 GameConst 口径）/ 宠物 8（代码 GameConst.PETS）
--   运行时可用 /api/health 的 content 指纹交叉核对：
--   items=44,affixes=30,equipment=82,skills=36,crafts=30,traits=23,pets=8
-- ============================================================

-- ---------- 第十一批·集群九：内容扩充七（药剂/词条/被动/委托/配方/装备） ----------

DELETE FROM `cfg_item` WHERE `code` IN ('taixu');
INSERT INTO `cfg_item` (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('taixu', '太虚神液', '🕳️', 'potion', 8000, '{"gold":0,"hp":0,"mp":0,"buff":"taixu_pot","buffVal":0.30,"duration":60}', '60s 内暴击率 +10%、暴击伤害 +30%', 1, 63);
DELETE FROM `cfg_buff` WHERE `code` IN ('taixu_pot');
INSERT INTO `cfg_buff` (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('taixu_pot', '太虚', '🕳️', 'atk', 'potion', '{"kind":"taixu"}', '太虚神液：暴击率 +10%、暴击伤害 +30%', 1, 46);

DELETE FROM `cfg_affix` WHERE `code` IN ('hongmeng_aff','taichu_aff');
INSERT INTO `cfg_affix` (`code`, `name`, `stat_key`, `stat_type`, `min_val`, `max_val`, `rarity`, `side_key`, `side_val`, `enabled`, `sort_no`) VALUES
  ('hongmeng_aff', '鸿蒙', 'MATK', 'flat', 60, 120, 'epic', NULL, NULL, 1, 31),
  ('taichu_aff', '太初', 'maxHP', 'flat', 20000, 50000, 'epic', NULL, NULL, 1, 32);

DELETE FROM `cfg_skill` WHERE `code` IN ('taixu_sword');
INSERT INTO `cfg_skill` (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('taixu_sword', '太虚剑意', '🗡️', 'passive', 0, 0, 0, 0, '{"kind":"critd","val":0.20}', '暴击伤害 +20%（剑意自太虚来）', 1, 38);

DELETE FROM `cfg_quest` WHERE `code` IN ('hongmeng_longhunt','hongmeng_deep');
INSERT INTO `cfg_quest` (`code`, `name`, `emoji`, `desc`, `target_zone`, `req_json`, `target_kills`, `reward_gold`, `reward_item`, `reward_item_count`, `repeatable`, `enabled`, `sort_no`) VALUES
  ('hongmeng_longhunt', '鸿蒙长猎', '🌌', '在鸿蒙界猎杀 200 只怪物', 'hongmeng', '{"maxHP":50000000000,"ATK":400000}', 200, 9000000, 'hongmeng', 2, 1, 1, 33),
  ('hongmeng_deep', '深入太虚', '🕳️', '在鸿蒙界猎杀 150 只怪物', 'hongmeng', '{"maxHP":50000000000,"ATK":400000}', 150, 7000000, 'pet_treat', 5, 1, 1, 34);

DELETE FROM `cfg_craft` WHERE `code` IN ('c_hongmeng_hood','c_hongmeng_robe');
INSERT INTO `cfg_craft` (`code`, `name`, `emoji`, `product_code`, `mats_json`, `gold_cost`, `need_blueprint`, `enabled`, `sort_no`) VALUES
  ('c_hongmeng_hood', '锻造鸿蒙兜帽', '🎩', 'hongmeng_hood', '{"hongmeng_qi":5,"chaos_core":2}', 450000, 0, 1, 44),
  ('c_hongmeng_robe', '锻造鸿蒙法袍', '🥋', 'hongmeng_robe', '{"hongmeng_qi":6,"chaos_core":3}', 480000, 0, 1, 45);

DELETE FROM `cfg_equipment_template` WHERE `code` IN ('hongmeng_aegis','hongmeng_eye');
INSERT INTO `cfg_equipment_template` (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('hongmeng_aegis', '鸿蒙神盾', '🛡️', 'offhand', 4, 'hongmeng', '{"maxHP":"300000","PDEF":"3200","MDEF":"3200"}', 10, 38000, '太初之气凝成的盾，挡下过开天一击', 1, 89),
  ('hongmeng_eye', '鸿蒙之瞳', '👁️', 'necklace', 4, 'hongmeng', '{"PATK":"1400","MATK":"1400","CRIT":"0.06","CRITD":"0.30"}', 10, 38000, '戴上它，你能看见紫气流动的轨迹', 1, 90);

-- ============================================================
-- 第十一批验收口径（2026-11-04，按 code 去重后的有效条目）：
--   cfg_equipment_template 90 / cfg_item 46 / cfg_affix 32 / cfg_skill 39 / cfg_quest 34 / cfg_craft 34
--   代码镜像：怪物词条 26 / 成就 113 / 天赋 14 / 套装 10 / 区域 12 / 图鉴 43 / 宠物 9
--   （Batch11FinalCountsTest 三方锁；全文件可重复执行）
-- ============================================================

-- ---------- 第十二批·集群二：归墟界（装备/委托/配方） ----------

DELETE FROM `cfg_equipment_template` WHERE `code` IN ('guixu_sword','guixu_bow','guixu_cap','guixu_armor','guixu_boots','guixu_ring');
INSERT INTO `cfg_equipment_template` (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('guixu_sword', '归墟沉剑', '🗡️', 'weapon', 4, 'guixu', '{"PATK":"16000","CRITD":"0.40"}', 10, 120000, '沉在归墟底的剑，拔出来时带起一整个海', 1, 91),
  ('guixu_bow', '归墟潮弓', '🏹', 'bow', 4, 'guixu', '{"PATK":"12800","RNG":"2","ASPD":"0.30"}', 10, 120000, '弦是退潮时绷紧的那根线', 1, 92),
  ('guixu_cap', '归墟水帽', '🎩', 'head', 4, 'guixu', '{"maxHP":"650000","PDEF":"4800","MDEF":"6500"}', 10, 102000, '戴上它，你听见万水归墟的声音', 1, 93),
  ('guixu_armor', '归墟重铠', '🥋', 'chest', 4, 'guixu', '{"maxHP":"850000","PDEF":"6000","MDEF":"7500"}', 10, 108000, '以沉船龙骨锻成的铠，万钧不沉', 1, 94),
  ('guixu_boots', '归墟踏靴', '🦿', 'legs', 4, 'guixu', '{"maxHP":"500000","PDEF":"5200","SPD":"80"}', 10, 102000, '踏浪如平地，沉渊亦可往', 1, 95),
  ('guixu_ring', '归墟珠环', '💍', 'ring1', 4, 'guixu', '{"MATK":"14500","MREGEN":"220","EVA":"0.05"}', 10, 114000, '环心嵌着归墟珠，深不见底', 1, 96);

DELETE FROM `cfg_quest` WHERE `code` IN ('guixu_patrol','guixu_heart');
INSERT INTO `cfg_quest` (`code`, `name`, `emoji`, `desc`, `target_zone`, `req_json`, `target_kills`, `reward_gold`, `reward_item`, `reward_item_count`, `repeatable`, `enabled`, `sort_no`) VALUES
  ('guixu_patrol', '归墟巡狩', '🌊', '万水之渊凶影横行：于归墟界猎杀一百只魔物', 'guixu', '{"maxHP":500000000000,"ATK":1000000}', 100, 20000000, 'hongmeng_qi', 2, 1, 1, 35),
  ('guixu_heart', '无量之心', '🖤', '深入归墟：于归墟界猎杀六十只灭世玄龟与沉渊巨鲸', 'guixu', '{"maxHP":500000000000,"ATK":1000000}', 60, 40000000, 'chaos_core', 5, 1, 1, 36);

DELETE FROM `cfg_craft` WHERE `code` IN ('c_guixu_sword','c_guixu_ring');
INSERT INTO `cfg_craft` (`code`, `name`, `emoji`, `product_code`, `mats_json`, `gold_cost`, `need_blueprint`, `enabled`, `sort_no`) VALUES
  ('c_guixu_sword', '锻造归墟沉剑', '🗡️', 'guixu_sword', '{"guixu_pearl":6,"hongmeng_qi":4}', 1500000, 0, 1, 46),
  ('c_guixu_ring', '锻造归墟珠环', '💍', 'guixu_ring', '{"guixu_pearl":4,"hongmeng_qi":6}', 1200000, 0, 1, 47);

-- ---------- 第十二批·集群三：战斗深化七（天罚/归墟体/归墟琼浆） ----------

DELETE FROM `cfg_skill` WHERE `code` IN ('heaven_wrath','guixu_body');
INSERT INTO `cfg_skill` (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('heaven_wrath', '天罚', '⚡', 'active', 3000, 100, 150, 0, '{"id":"heaven_wrath"}', '下一击 ×6 伤害（天雷代罚，一击定罪）', 1, 39),
  ('guixu_body', '归墟体', '🌊', 'passive', 0, 0, 0, 0, '{"kind":"crit","val":0.05}', '暴击率 +5%（沉渊淬体）', 1, 40);

DELETE FROM `cfg_item` WHERE `code` IN ('guixu');
INSERT INTO `cfg_item` (`code`, `name`, `emoji`, `kind`, `price`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('guixu', '归墟琼浆', '🌊', 'potion', 15000, '{"gold":0,"hp":0,"mp":0,"buff":"guixu_pot","buffVal":0.80,"duration":60}', '60s 内暴击伤害 +80%、吸血 +8%', 1, 64);
DELETE FROM `cfg_buff` WHERE `code` IN ('guixu_pot');
INSERT INTO `cfg_buff` (`code`, `name`, `emoji`, `nature`, `group_type`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('guixu_pot', '归墟', '🌊', 'atk', 'potion', '{"kind":"guixu"}', '归墟琼浆：暴击伤害 +80%、吸血 +8%', 1, 47);

-- ---------- 第十二批·集群九：内容扩充八（词条/被动/委托/配方/装备） ----------

DELETE FROM `cfg_affix` WHERE `code` IN ('guixu_aff','hanhai_aff');
INSERT INTO `cfg_affix` (`code`, `name`, `stat_key`, `stat_type`, `min_val`, `max_val`, `rarity`, `side_key`, `side_val`, `enabled`, `sort_no`) VALUES
  ('guixu_aff', '归墟', 'PATK', 'flat', 90, 180, 'epic', NULL, NULL, 1, 33),
  ('hanhai_aff', '瀚海', 'maxHP', 'flat', 50000, 120000, 'epic', NULL, NULL, 1, 34);

DELETE FROM `cfg_skill` WHERE `code` IN ('tide_body');
INSERT INTO `cfg_skill` (`code`, `name`, `emoji`, `kind`, `price`, `mp`, `cd`, `dur`, `effect_json`, `desc`, `enabled`, `sort_no`) VALUES
  ('tide_body', '潮汐之体', '🌊', 'passive', 0, 0, 0, 0, '{"kind":"eva","val":0.06}', '闪避率 +6%（身随潮汐）', 1, 41);

DELETE FROM `cfg_quest` WHERE `code` IN ('guixu_longhunt','guixu_deep');
INSERT INTO `cfg_quest` (`code`, `name`, `emoji`, `desc`, `target_zone`, `req_json`, `target_kills`, `reward_gold`, `reward_item`, `reward_item_count`, `repeatable`, `enabled`, `sort_no`) VALUES
  ('guixu_longhunt', '归墟长猎', '🌊', '在归墟界猎杀 200 只怪物', 'guixu', '{"maxHP":500000000000,"ATK":1000000}', 200, 30000000, 'guixu', 2, 1, 1, 37),
  ('guixu_deep', '沉渊之约', '🐋', '在归墟界猎杀 150 只怪物', 'guixu', '{"maxHP":500000000000,"ATK":1000000}', 150, 24000000, 'pet_treat', 5, 1, 1, 38);

DELETE FROM `cfg_craft` WHERE `code` IN ('c_guixu_cap','c_guixu_armor');
INSERT INTO `cfg_craft` (`code`, `name`, `emoji`, `product_code`, `mats_json`, `gold_cost`, `need_blueprint`, `enabled`, `sort_no`) VALUES
  ('c_guixu_cap', '锻造归墟水帽', '🎩', 'guixu_cap', '{"guixu_pearl":5,"hongmeng_qi":3}', 1200000, 0, 1, 48),
  ('c_guixu_armor', '锻造归墟重铠', '👘', 'guixu_armor', '{"guixu_pearl":6,"hongmeng_qi":4}', 1400000, 0, 1, 49);

DELETE FROM `cfg_equipment_template` WHERE `code` IN ('guixu_aegis','guixu_amulet');
INSERT INTO `cfg_equipment_template` (`code`, `name`, `emoji`, `slot`, `tier`, `min_zone`, `base_stats`, `enhance_max`, `sell_base`, `remark`, `enabled`, `sort_no`) VALUES
  ('guixu_aegis', '归墟神盾', '🛡️', 'offhand', 4, 'guixu', '{"PDEF":"7200","MDEF":"7200","maxHP":"400000"}', 10, 114000, '盾面是凝固的浪，万钧压来也只是又一朵', 1, 97),
  ('guixu_amulet', '归墟之瞳', '🧿', 'necklace', 4, 'guixu', '{"MATK":"12000","CRIT":"0.06","MREGEN":"180"}', 10, 114000, '瞳孔深处是归墟的漩涡，看久了会听见潮声', 1, 98);

-- ============================================================
-- 第十二批验收口径（2026-11-05）：cfg_equipment_template 98 / cfg_item 47 /
-- cfg_affix 34 / cfg_skill 42 / cfg_quest 38 / cfg_craft 38（均按 code 去重、enabled 计）
-- 代码侧：区域 13 / 套装 11 / 成就 128 / 天赋 15 / 怪物词条 29 / 宝石 18 / 图鉴 48
-- ============================================================
