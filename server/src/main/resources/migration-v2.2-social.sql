-- v2.2 社交玩法增量：香火 / 切磋战绩 / 墓碑祭奠数
USE `gongpu`;
ALTER TABLE `account_user`
  ADD COLUMN `incense` INT NOT NULL DEFAULT 0 COMMENT '香火（复活货币）' AFTER `last_login_at`,
  ADD COLUMN `pvp_wins` INT NOT NULL DEFAULT 0 AFTER `incense`,
  ADD COLUMN `pvp_losses` INT NOT NULL DEFAULT 0 AFTER `pvp_wins`;

ALTER TABLE `game_grave`
  ADD COLUMN `tributes` INT NOT NULL DEFAULT 0 COMMENT '被祭奠次数' AFTER `epitaph`;
