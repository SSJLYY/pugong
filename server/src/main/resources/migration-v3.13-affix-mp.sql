-- ============================================================
-- v3.13:新增词条「法力洪流」——蓝上限(MP)+100~500
-- 背景:蓝上限固定 80 无成长线,纯魔法弹(BULLET_MP)流派无配套成长;
--       魔弹枪玩家靠本词条堆蓝上限,每帧可打发数 = 蓝上限 ÷ 每发耗蓝
-- 幂等:同 code 先删后插,可重复执行(与 seed 同口径)
-- ============================================================
USE `gongpu`;

DELETE FROM `cfg_affix` WHERE `code`='mana_tide';
INSERT INTO `cfg_affix`
  (`code`, `name`, `stat_key`, `stat_type`, `min_val`, `max_val`, `rarity`, `side_key`, `side_val`, `enabled`, `sort_no`) VALUES
  ('mana_tide',    '法力洪流', 'MP',        'flat', 100,   500,   'rare',     NULL,   NULL,   1, 14);
