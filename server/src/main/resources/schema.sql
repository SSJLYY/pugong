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
