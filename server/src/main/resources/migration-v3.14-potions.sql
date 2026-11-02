-- ============================================================
-- v3.14:商店新增 10 种药剂 + 配套增益 buff
-- 设计口径:全属性乘区/加区均受沉默(Silence)压制,与既有药剂一致;
--         护盾为吸收型新机制(先于血量扣除,GameEngine.mobAttack 接线)
-- 幂等:同 code 先删后插,可重复执行(与 seed 同口径)
-- ============================================================
USE `gongpu`;

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
