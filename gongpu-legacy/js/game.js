/* ============================================================
 * 《普攻永久增加生命上限》 game.js
 * 核心：状态 / 大数成长引擎 / 批处理普攻 / 战斗 / 缓存
 * 依据 DESIGN.md v0.4（M1 范围 + 全架构预留）
 *
 * 缓存策略（皇上钦定"能用缓存就用缓存"）：
 *   1. panelCache  —— 面板派生值缓存，dirty 标记驱动
 *   2. attackProgress 批处理 —— 攻速高时一次 tick 数学批量结算
 *   3. UI 渲染节流 —— 每 tick 只做增量数字更新
 * ============================================================ */

/* global Decimal, BLESS_STEP, BLESS_CAP, CN_UNITS, INIT_LIFE, INIT_FIXED,
          SKILLS, ZONES, DMG, FLEE, TICK_MS, REBIRTH_ASPD */
const D = Decimal;

// ---------------- 状态 ----------------
function newState() {
  return {
    v: 1,
    gen: 0,                                  // 世代：第 0 世
    life: {                                  // 大数成长属性（全大数域）
      maxHP: new D(INIT_LIFE.maxHP), curHP: new D(INIT_LIFE.curHP),
      MP: new D(INIT_LIFE.MP),       curMP: new D(INIT_LIFE.curMP),
      PATK: new D(INIT_LIFE.PATK),   MATK: new D(INIT_LIFE.MATK),
      PDEF: new D(INIT_LIFE.PDEF),   MDEF: new D(INIT_LIFE.MDEF),
      REGEN: new D(INIT_LIFE.REGEN),  MREGEN: new D(INIT_LIFE.MREGEN)
    },
    fixed: Object.assign({}, INIT_FIXED),    // 固定/百分比属性
    blessings: { HP: { lv: 0 } },            // 赋能（第 0 世先天：HP +1/拳）
    gold: new D(0),
    stats: { punches: new D(0), lifetimePunches: new D(0), kills: 0, deaths: 0, born: Date.now() },
    zone: 'village',
    mob: null,                               // 当前目标 { def, curHP }
    cds: {},                                 // 技能冷却剩余秒 { burn: 0 }
    buffs: {},                               // { burn: 剩余秒 }
    autoSave: true,
    lastSaveTs: 0,
    lastSaveSlot: 0,
    equipBag: [],            // 装备背包（M3）
    equipped: {},            // 已装备 { 部位: 装备对象 }
    nextUid: 1,              // 装备唯一 ID 计数器
    skills: { passives: [], mastery: {} },  // M4: 已学被动/熟练度
    potions: {},                            // M4: 药剂库存
    newbieKills: 0,                          // 第 0 世平原击杀计数（前 5 杀保底掉武器）
    talent: null,                            // 天赋 id（新档觉醒抽卡，转生保留）
    bossFight: false,                        // Boss 战进行中
    bossSilenceCd: 0                         // Boss 沉默技能冷却（秒）
  };
}

let S = newState();

// ---------------- 成就 / 音效埋点辅助（判空调用，模块未加载时零开销） ----------------
function _ach(k, v) { try { if (window.__achievements) window.__achievements.check(k, v); } catch (e) {} }
function _sfx(n)    { try { if (window.__audio) window.__audio.play(n); } catch (e) {} }

// ---------------- 天赋系统（新档觉醒抽卡） ----------------
function talentDef() {
  return (typeof TALENTS !== 'undefined' && S.talent) ? TALENTS[S.talent] : null;
}
function talentGoldMul() {
  const tl = talentDef();
  return (tl && tl.effect && tl.effect.gold) ? (1 + tl.effect.gold) : 1;
}
function talentCdMul() {
  const tl = talentDef();
  return (tl && tl.effect && tl.effect.cd) ? (1 - tl.effect.cd) : 1;
}
// 抽取天赋：UI 层的觉醒仪式调用，仪式结束才写入 S.talent
function drawTalent() {
  if (Math.random() < NEWBIE_ETERNAL_RATE) return 'eternal';
  const pool = Object.keys(TALENTS).filter(id => id !== 'eternal');
  return pool[Math.floor(Math.random() * pool.length)];
}

// 被动永续：增益 buff 冻结不衰减；Boss「沉默」(S.buffs.silence) 期间失效，被动恢复倒计时
const ETERNAL_BUFFS = ['burn', 'block', 'rapid', 'surge', 'regen_food', 'spd_pot'];
function eternalActive() {
  return S.talent === 'eternal' && !((S.buffs.silence || 0) > 0);
}

// 装备/背包版本号：内容变化时 +1，UI 层用它跳过整包 JSON 序列化比对
// 注意：必须在 starterKit() 等早期调用之前初始化（let 存在暂时性死区）
let invVersion = 0;
function markInvDirty() { invVersion++; }

// 开局白给：背包里放一把新手木棍（设计文档定位就是"白给"）；newGame 重开档也会调用
function starterKit() {
  const stick = WEAPONS.find(w => w.name === '新手木棍');
  if (!stick) return;
  S.equipBag.push({
    uid: S.nextUid++, name: stick.name, emoji: stick.emoji, slot: stick.slot,
    base: {...stick.base}, affixes: [], quality: 0, enhance: 0, desc: stick.desc || '',
    starter: true   // 标记：开局白给，自动售卖不卖（新手引导）
  });
  markInvDirty();
}
starterKit();   // 首次加载白给（读档流程会覆盖 equipBag，不影响）

// ---------------- 赋能步长（强化纪元：step × (1+lv)） ----------------
function blessStep(key) {
  const b = S.blessings[key];
  if (!b) return null;
  return new D(BLESS_STEP[key]).times(1 + b.lv);
}

// ---------------- 面板缓存 ----------------
let panelDirty = true;
function markPanelDirty() { panelDirty = true; }
function getPanel() {
  if (!panelDirty) return panelCache;
  const L = S.life, F = S.fixed;
  const burn = (S.buffs.burn || 0) > 0;
  const burnMul = burn ? 2 : 1;
  // M4: 被动技能乘区
  const pmul = {}, padd = {};
  for (const id of (S.skills.passives||[])) {
    const ps = PASSIVE_SKILLS[id]; if (!ps) continue;
    if (ps.effect === 'mul') pmul[ps.key] = (pmul[ps.key]||1) * ps.mul;
    else if (ps.effect === 'add') padd[ps.key] = (padd[ps.key]||0) + ps.val;
    else if (ps.effect === 'mul_side') { pmul[ps.key] = (pmul[ps.key]||1) * ps.mul; padd[ps.sideKey] = (padd[ps.sideKey]||0) + ps.sideVal; }
    else if (ps.effect === 'matk_bonus') pmul['MATK'] = (pmul['MATK']||1) * (1 + ps.val);
  }
  // M4: 主动技能 buff 乘区
  const rapidMul = (S.buffs.rapid || 0) > 0 ? 3 : 1;
  const spdPotMul = (S.buffs.spd_pot || 0) > 0 ? 1.2 : 1;
  const regenFood = (S.buffs.regen_food || 0) > 0 ? 20 : 0;
  // —— 装备加成收集（底子 + 词条）——
  const flat = {}, pctMul = {};
  for (const slot of Object.keys(S.equipped || {})) {
    const item = S.equipped[slot]; if (!item) continue;
    const enh = 1 + (item.enhance || 0) * ENHANCE.statBonus;
    for (const k of Object.keys(item.base || {})) flat[k] = (flat[k]||0) + item.base[k] * enh;
    for (const a of (item.affixes || [])) {
      if (a.type === 'flat') flat[a.key] = (flat[a.key]||0) + a.val;
      else pctMul[a.key] = (pctMul[a.key]||0) + a.val;
      if (a.side) pctMul[a.side.key] = (pctMul[a.side.key]||0) + a.side.val;
    }
  }
  // —— 天赋加成（觉醒抽得，永久生效，转生保留）——
  const tlEff = (typeof TALENTS !== 'undefined' && S.talent && TALENTS[S.talent]) ? (TALENTS[S.talent].effect || {}) : {};
  if (tlEff.flat) for (const k of Object.keys(tlEff.flat)) flat[k] = (flat[k]||0) + tlEff.flat[k];
  if (tlEff.mul)  for (const k of Object.keys(tlEff.mul))  pctMul[k] = (pctMul[k]||0) + tlEff.mul[k];
  const cache = {
    maxHP: L.maxHP.plus(flat.maxHP||0).times(1 + (pctMul.maxHP||0)),
    curHP: L.curHP.plus(flat.curHP||0),
    MP: L.MP.plus(flat.MP||0).times(1 + (pctMul.MP||0)),
    curMP: L.curMP.plus(flat.curMP||0),
    PATK: L.PATK.plus(flat.PATK||0).times(1 + (pctMul.PATK||0)).times(pmul.PATK||1).times(burnMul),
    MATK: L.MATK.plus(flat.MATK||0).times(1 + (pctMul.MATK||0)).times(pmul.MATK||1).times(burnMul),
    PDEF: L.PDEF.plus(flat.PDEF||0).times(1 + (pctMul.PDEF||0)).times(pmul.PDEF||1),
    MDEF: L.MDEF.plus(flat.MDEF||0).times(1 + (pctMul.MDEF||0)).times(pmul.MDEF||1),
    REGEN: L.REGEN.plus((flat.REGEN||0) + regenFood).times(1 + (pctMul.REGEN||0)),
    MREGEN: L.MREGEN.plus(flat.MREGEN||0).times(1 + (pctMul.MREGEN||0)),
    ASPD: (F.ASPD + (flat.ASPD||0) + F.ASPD * (pctMul.ASPD||0)) * rapidMul * spdPotMul,
    RNG: F.RNG + (flat.RNG||0) + F.RNG * (pctMul.RNG||0),
    SPD: (F.SPD + (flat.SPD||0) + F.SPD * (pctMul.SPD||0)) * (pmul.SPD||1),
    CRIT: Math.min(BLESS_CAP.CRIT, F.CRIT + (flat.CRIT||0)),
    CRITD: F.CRITD + (flat.CRITD||0),
    ACC: Math.min(BLESS_CAP.ACC, F.ACC + (flat.ACC||0) + (padd.ACC||0)),
    EVA: Math.min(BLESS_CAP.EVA, F.EVA + (flat.EVA||0)),
    LIFESTEAL: flat.LIFESTEAL || 0,
    gold: S.gold
  };
  panelCache = cache;
  panelDirty = false;
  return cache;
}
let panelCache = null;

// ---------------- 普攻结算（核心循环） ----------------
// n 次普攻批量结算：赋能成长 + 伤害输出
function settlePunches(n) {
  if (n.lte(0)) return;
  const p = getPanel();
  const hitRate = p.ACC;
  const hasCombo = (S.skills.passives||[]).includes('combo');
  const hasVitality = (S.skills.passives||[]).includes('vitality');
  // 连击：15% 概率双拳 → 期望 ×1.15
  const comboMul = hasCombo ? (1 + PASSIVE_SKILLS.combo.chance) : 1;
  const hits = n.times(hitRate).times(comboMul);
  // 村口修行加成：木桩区出拳成长 ×2（新手友好，仅限安全区）
  const villageMul = S.zone === 'village' ? 2 : 1;
  for (const key of Object.keys(S.blessings)) {
    const step = blessStep(key);
    if (!step) continue;
    applyBlessGain(key, step.times(hits).times(villageMul));
  }
  // 健体：每 100 拳额外 +50 生命上限
  if (hasVitality) {
    const extra = n.times(PASSIVE_SKILLS.vitality.bonus / PASSIVE_SKILLS.vitality.interval);
    S.life.maxHP = S.life.maxHP.plus(extra);
    S.life.curHP = S.life.curHP.plus(extra);
  }
  S.stats.punches = S.stats.punches.plus(n);
  S.stats.lifetimePunches = S.stats.lifetimePunches.plus(n);
  const target = S.mob;
  if (target && target.curHP.gt(0)) {
    const nn = n.toNumber();
    if (nn <= 1000) {
      for (let i = 0; i < nn; i++) doOneHit(p, hitRate);
    } else {
      doBatchHit(p, hits, n);
    }
  }
  markPanelDirty();
}

function applyBlessGain(key, gain) {
  switch (key) {
    case 'HP':   S.life.maxHP = S.life.maxHP.plus(gain); S.life.curHP = S.life.curHP.plus(gain); break;
    case 'PATK': S.life.PATK = S.life.PATK.plus(gain); break;
    case 'MATK': S.life.MATK = S.life.MATK.plus(gain); break;
    case 'PDEF': S.life.PDEF = S.life.PDEF.plus(gain); break;
    case 'MDEF': S.life.MDEF = S.life.MDEF.plus(gain); break;
    case 'REGEN': S.life.REGEN = S.life.REGEN.plus(gain); break;
    // fixed 类（M1 不触发，第1世+选这些后才生效；实现走小步长累积）
    case 'ASPD': S.fixed.ASPD += gain.toNumber(); break;
    case 'RNG':  S.fixed.RNG += gain.toNumber(); break;
    case 'SPD':  S.fixed.SPD += gain.toNumber(); break;
    case 'CRIT': S.fixed.CRIT = Math.min(BLESS_CAP.CRIT, S.fixed.CRIT + gain.toNumber()); break;
    case 'CRITD': S.fixed.CRITD += gain.toNumber(); break;
    case 'ACC': S.fixed.ACC = Math.min(BLESS_CAP.ACC, S.fixed.ACC + gain.toNumber()); break;
    case 'EVA': S.fixed.EVA = Math.min(BLESS_CAP.EVA, S.fixed.EVA + gain.toNumber()); break;
  }
}

// 单次命中伤害（小数值域，直接 toNumber）
function rollDamage(atk, def) {
  const base = Math.max(1, atk * (1 - DMG.variance * Math.random() * 2) - def * DMG.defFactor);
  return base < 1 ? 1 : base;
}

function doOneHit(p, hitRate) {
  if (Math.random() >= hitRate) return;                  // miss：不加血（成长已按期望结算）
  const target = S.mob;
  if (!target) return;
  let dmg = rollDamage(p.PATK.toNumber(), target.def.PDEF);
  const isCrit = Math.random() < p.CRIT;
  if (isCrit) dmg *= p.CRITD;
  if (p.MATK.gt(0)) {
    let mdmg = rollDamage(p.MATK.toNumber(), target.def.MDEF);
    if (Math.random() < p.CRIT) mdmg *= p.CRITD;
    dmg += mdmg;
  }
  if ((S.buffs.heavy || 0) > 0) { dmg *= 5; delete S.buffs.heavy; markPanelDirty(); }  // 蓄力重击
  if (isCrit && typeof window.__floatDmg === 'function') window.__floatDmg('暴击 ' + (typeof fmt==='function'?fmt(dmg):dmg), 'crit');
  damageMob(dmg);
}

function doBatchHit(p, hits, n) {                          // 批量期望伤害（后期攻速爆炸时）
  const target = S.mob;
  if (!target) return;
  const critMul = 1 + p.CRIT * (p.CRITD - 1);
  const pd = Math.max(1, p.PATK.toNumber() * critMul - target.def.PDEF * DMG.defFactor);
  const md = p.MATK.gt(0) ? Math.max(1, p.MATK.toNumber() * critMul - target.def.MDEF * DMG.defFactor) : 0;
  damageMob(hits.toNumber() * (pd + md));
}

function damageMob(dmg) {
  const target = S.mob;
  if (!target || target.def.maxHP === Infinity) return;   // 木桩不掉血
  target.curHP = target.curHP.minus(dmg);
  if (target.curHP.lte(0)) {
    S.stats.kills++;
    _ach('kills', S.stats.kills);
    _sfx('kill');
    const goldMul = (typeof talentGoldMul === 'function') ? talentGoldMul() : 1;
    S.gold = S.gold.plus((target.def.gold || 0) * goldMul);
    if ((target.def.gold || 0) > 0 && typeof window.__floatDmg === 'function') window.__floatDmg('+' + target.def.gold, 'kill');
    if (S.bossFight) {
      bossDrop();          // Boss 专属掉落（必紫 25% 橙 + 成就钩子）
      endBossFight(true);  // 回到普通刷怪
    } else {
      tryDropEquip(S.zone);                                // M3: 掉落装备
      spawnMob(S.zone, true);                              // 击杀后自动寻下一只
    }
    markPanelDirty();
  }
}

// Boss 专属掉落：必掉稀有装，25% 概率升史诗
function bossDrop() {
  const tierBoost = Math.min(3, Math.floor(Object.keys(ZONES).indexOf(S.zone) / 2));
  // 75% 稀有（quality 2 蓝色），25% 史诗（quality 3 紫色）
  const qIdx = Math.random() < 0.25 ? 3 : 2;
  const quality = QUALITY[qIdx];
  const slotKeys = ['weapon','offhand','head','chest','legs','boots','ring','necklace','bow'];
  let pool = [];
  for (const sk of slotKeys) {
    if (sk === 'weapon' || sk === 'bow') pool = pool.concat(WEAPONS.filter(w => (sk==='bow'? w.slot==='bow' : w.slot==='weapon')));
    else if (ARMOR_BASE[sk]) pool = pool.concat(ARMOR_BASE[sk].map(a => ({...a, slot: sk==='ring'?'ring1':sk})));
  }
  const minTier = Math.min(3, tierBoost);
  pool = pool.filter(p => (p.tier||0) >= minTier || Math.random() < 0.3);
  if (pool.length === 0) pool = WEAPONS;
  const base = pool[Math.floor(Math.random() * pool.length)];
  const affixes = [];
  const affixPool = [...AFFIXES];
  for (let i = 0; i < quality.affixCount && affixPool.length; i++) {
    const idx = Math.floor(Math.random() * affixPool.length);
    const a = affixPool.splice(idx, 1)[0];
    const val = a.min + Math.random() * (a.max - a.min);
    affixes.push({ name:a.name, key:a.key, type:a.type, val:val, rarity:a.rarity, side:a.side||null });
  }
  const item = {
    uid: S.nextUid++,
    name: base.name, emoji: base.emoji || '📦',
    slot: base.slot, base: {...base.base},
    affixes, quality: qIdx, enhance: 0,
    desc: (base.desc || '') + ' [Boss 掉落]'
  };
  if (S.equipBag.length >= BAG_MAX) {
    if (typeof toast === 'function') toast('🎒 背包已满，Boss 掉落无法拾取');
  } else {
    S.equipBag.push(item);
    markInvDirty();
    _ach('equipGain', 1); _sfx('drop');
    if (typeof toast === 'function') toast('👑 Boss 掉落！' + quality.name + ' ' + item.emoji + ' ' + item.name);
  }
}

// ---------------- 怪物出手 / 承伤 ----------------
function mobAttack(dt) {
  const target = S.mob;
  if (!target || target.def.maxHP === Infinity) return;
  // Boss 沉默技能：冷却结束后释放，30 秒内玩家的被动（含永续）失效
  if (S.bossFight && target.def.silenceCd) {
    S.bossSilenceCd -= dt;
    if (S.bossSilenceCd <= 0) {
      S.buffs.silence = target.def.silenceDur;
      S.bossSilenceCd = target.def.silenceCd;
      markPanelDirty();
      if (typeof toast === 'function') toast('🤐 ' + target.def.name + ' 释放沉默！30 秒内被动失效');
    }
  }
  if (canKite(target.def)) return;   // 风筝成立：怪永远够不着你
  target.prog = (target.prog || 0) + target.def.ASPD * dt;
  while (target.prog >= 1) {
    target.prog -= 1;
    const p = getPanel();
    if (Math.random() < p.EVA) continue;                 // 闪避
    const raw = rollDamage(target.def.PATK, p.PDEF.toNumber());
    const mraw = target.def.MATK > 0 ? rollDamage(target.def.MATK, p.MDEF.toNumber()) : 0;
    let dmg = raw + mraw;
    if ((S.buffs.block || 0) > 0) { dmg *= 0.5; damageMob(dmg * 0.5); }  // 格挡反击：减半+反弹 50%
    S.life.curHP = S.life.curHP.minus(dmg);
    // 怪物特性：毒蟾上毒、火山怪上灼烧（刷新持续时间，不叠加层数）
    const tags = target.def.tags || [];
    if (tags.includes('poison')) S.buffs.poison = Math.max(S.buffs.poison || 0, 8);
    if (tags.includes('burn'))   S.buffs.burn_dot = Math.max(S.buffs.burn_dot || 0, 6);
    if (S.life.curHP.lte(0)) { onPlayerDeath(); return; }
  }
}

// ---------------- 持续伤害（毒/灼烧，无视防御，可被解毒剂清除） ----------------
function dotDamage(dt) {
  const p = getPanel();
  let dot = 0;
  if ((S.buffs.poison || 0) > 0)   dot += Math.max(2, p.maxHP.times(0.012).toNumber());  // 中毒：每秒 1.2% 上限
  if ((S.buffs.burn_dot || 0) > 0) dot += Math.max(4, p.maxHP.times(0.02).toNumber());   // 灼烧：每秒 2% 上限
  if (dot <= 0) return;
  S.life.curHP = S.life.curHP.minus(dot * dt);
  if (S.life.curHP.lte(0)) onPlayerDeath();
}

// ---------------- 回复 ----------------
function regen(dt) {
  const p = getPanel();
  // 装备特殊词条：每秒回复 X% 生命上限（如保底戒指的"回春"）
  let pctRegen = 0;
  for (const slot of Object.keys(S.equipped || {})) {
    const item = S.equipped[slot]; if (!item) continue;
    for (const a of (item.affixes || [])) {
      if (a.key === 'REGEN_PCT') pctRegen += a.val;
    }
  }
  const totalRegen = p.REGEN.plus(p.maxHP.times(pctRegen));
  S.life.curHP = D.min(p.maxHP, S.life.curHP.plus(totalRegen.times(dt)));
  S.life.curMP = D.min(p.MP, S.life.curMP.plus(p.MREGEN.times(dt)));
}

// ---------------- 区域 / 目标 ----------------
// 区域准入检查（属性门槛）
function checkZoneReq(zoneId) {
  const req = ZONE_REQ[zoneId];
  if (!req) return { ok: true };
  const p = getPanel();
  for (const k of Object.keys(req)) {
    const have = (k === 'maxHP') ? p.maxHP : (p[k] !== undefined ? p[k] : null);
    if (have === null) continue;
    const hv = (have instanceof Decimal) ? have.toNumber() : have;
    if (hv < req[k]) return { ok:false, key: (k==='maxHP'?'生命上限':k), need:req[k], have:hv };
  }
  return { ok: true };
}

// 风筝判定：玩家射程 > 怪射程 且 移速 >= 怪移速 → 怪够不着你
function canKite(mobDef) {
  if (!mobDef || mobDef.maxHP === Infinity) return false;
  const p = getPanel();
  return p.RNG > mobDef.RNG && p.SPD >= mobDef.SPD;
}

function gotoZone(zoneId) {
  if (!ZONES[zoneId]) return false;
  const chk = checkZoneReq(zoneId);
  if (!chk.ok) {
    if (typeof toast === 'function') toast('🚫 不满足准入：' + chk.key + ' 需 ≥ ' + chk.need + '（当前 ' + (typeof fmt==='function'?fmt(chk.have):chk.have) + '）');
    return false;
  }
  S.zone = zoneId;
  if (S.bossFight) { S.bossFight = false; S.bossSilenceCd = 0; }  // 切图退出 Boss 战
  S.mob = null;
  spawnMob(zoneId, false);
  markPanelDirty();
  if (zoneId === 'village' && S.autoSave) autoSaveVillage();  // 村口自动存档
  return true;
}

function spawnMob(zoneId, keepProg) {
  // Boss 战中不刷普通怪
  if (S.bossFight) return;
  const zone = ZONES[zoneId];
  const def = zone.mobs[Math.floor(Math.random() * zone.mobs.length)];
  S.mob = { def, curHP: new D(def.maxHP), prog: 0 };
  markPanelDirty();
}

// ---------------- Boss 挑战 ----------------
function startBossFight() {
  const boss = ZONE_BOSSES[S.zone];
  if (!boss || S.bossFight || S.zone === 'village') return { ok:false, msg:'当前区域没有 Boss' };
  S.bossFight = true;
  S.bossSilenceCd = boss.silenceCd;   // 首个沉默在 silenceCd 秒后来袭
  S.mob = { def: {...boss, isBoss: true}, curHP: new D(boss.maxHP), prog: 0 };
  markPanelDirty();
  return { ok:true, msg:'👑 Boss 战开始：' + boss.emoji + ' ' + boss.name + '！小心沉默' };
}

function endBossFight(won) {
  if (!S.bossFight) return;
  S.bossFight = false;
  S.bossSilenceCd = 0;
  if (won) spawnMob(S.zone, true);   // 胜利后回到刷怪
}

// ---------------- 技能：燃烧生命 ----------------
// 通用主动技能（M4）
function castSkill(id) {
  const sk = ACTIVE_SKILLS[id]; if (!sk) return false;
  if ((S.cds[id] || 0) > 0) return false;
  const surgeActive = (S.buffs.surge || 0) > 0;
  if (sk.mp > 0 && !surgeActive && S.life.curMP.lt(sk.mp)) return false;
  if (id === 'burn' && S.life.curHP.lte(S.life.maxHP.times(0.3))) return false;
  if (sk.mp > 0 && !surgeActive) S.life.curMP = S.life.curMP.minus(sk.mp);
  if (id === 'burn') { S.life.curHP = S.life.curHP.times(0.75); S.buffs.burn = sk.dur; }
  else if (id === 'block') { S.buffs.block = sk.dur; }
  else if (id === 'rapid')  { S.buffs.rapid = sk.dur; }
  else if (id === 'surge')  { S.buffs.surge = sk.dur; }
  else if (id === 'heavy')  { S.buffs.heavy = 1; }
  else if (id === 'roar')   { spawnMob(S.zone, true); }
  const cdMul = (typeof talentCdMul === 'function') ? talentCdMul() : 1;
  S.cds[id] = sk.cd * cdMul;
  markPanelDirty();
  return true;
}
// 燃烧生命快捷（兼容 M1）
function castBurn() { return castSkill('burn'); }

// 药剂（M4）
function usePotion(id) {
  const pot = POTIONS[id]; if (!pot) return { ok:false, msg:'无此药剂' };
  if ((S.potions[id]||0) <= 0) return { ok:false, msg:pot.name + ' 不足' };
  S.potions[id] = (S.potions[id]||0) - 1;
  const p = getPanel();
  if (pot.healHP) {
    const heal = p.maxHP.times(pot.healHP);
    S.life.curHP = D.min(p.maxHP, S.life.curHP.plus(heal));
  }
  if (pot.healMP) {
    const heal = p.MP.times(pot.healMP);
    S.life.curMP = D.min(p.MP, S.life.curMP.plus(heal));
  }
  if (pot.buff) S.buffs[pot.buff] = pot.dur;
  if (id === 'cure') { delete S.buffs.poison; delete S.buffs.burn_dot; }
  markPanelDirty();
  _ach('usePotion', 1);
  _sfx('buy');
  return { ok:true, msg:'使用 ' + pot.emoji + ' ' + pot.name };
}
function buyPotion(id) {
  const pot = POTIONS[id]; if (!pot) return { ok:false, msg:'无此药剂' };
  if (S.gold.lt(pot.price)) return { ok:false, msg:'金币不足' };
  S.gold = S.gold.minus(pot.price);
  S.potions[id] = (S.potions[id]||0) + 1;
  markPanelDirty();
  _sfx('buy');
  return { ok:true, msg:'购买 ' + pot.name + '（-' + pot.price + ' 金币）' };
}
function learnSkill(id) {
  if (PASSIVE_SKILLS[id] && !(S.skills.passives||[]).includes(id)) {
    S.skills.passives = [...(S.skills.passives||[]), id];
    markPanelDirty();
    return { ok:true, msg:'学会被动技能 ' + PASSIVE_SKILLS[id].emoji + ' ' + PASSIVE_SKILLS[id].name };
  }
  return { ok:false, msg:'已学或无效' };
}

// ---------------- 主循环 ----------------
let playerProg = 0;        // 攻击进度（大数域，攻速可到 1e44）
let lastTick = null;

function tick(now) {
  if (lastTick === null) lastTick = now;
  let dt = Math.min((now - lastTick) / 1000, 1.0);   // 后台节流补偿上限 1s（M5 做完整离线）
  lastTick = now;

  if (!GAME_OVER) {
    // buff / CD 计时（M4 通用化）
    for (const k of Object.keys(S.buffs)) {
      if (eternalActive() && ETERNAL_BUFFS.indexOf(k) >= 0) continue;   // 被动永续：增益冻结，永不衰减
      S.buffs[k] -= dt;
      if (S.buffs[k] <= 0) { delete S.buffs[k]; markPanelDirty(); }
    }
    for (const k of Object.keys(S.cds)) {
      if (S.cds[k] > 0) S.cds[k] -= dt;
    }

    const p = getPanel();
    // 玩家攻击进度（攻速 = 唯一成长倍率）
    playerProg += p.ASPD * dt;
    if (playerProg >= 1) {
      const n = Math.floor(playerProg);
      playerProg -= n;
      settlePunches(new D(n));
    }
    mobAttack(dt);
    dotDamage(dt);
    regen(dt);
  }
  if (typeof UI !== 'undefined' && UI.render) UI.render(dt);
}

// ---------------- 死亡 ----------------
let GAME_OVER = false;       // 死亡画面中，世界暂停
let deathInfo = null;

function onPlayerDeath() {
  GAME_OVER = true;
  const p = getPanel();
  deathInfo = {
    killer: S.mob ? S.mob.def : null,
    panel: p,
    punches: S.stats.punches.toString(),
    lifetimePunches: S.stats.lifetimePunches.toString(),
    livedMs: Date.now() - S.stats.born,
    gen: S.gen,
    zone: S.zone,
    analysis: analyzeDeath(p, S.mob ? S.mob.def : null)   // M5 尸检
  };
  S.stats.deaths++;
  _ach('deaths', S.stats.deaths);
  _sfx('death');
  if (typeof UI !== 'undefined' && UI.showDeath) UI.showDeath(deathInfo);
}

// M5: 尸检报告
function analyzeDeath(p, mob) {
  if (!mob) return { cause:'未知', suggestions:[] };
  const physGap = Math.max(0, mob.PATK - p.PDEF.toNumber() * DMG.defFactor);
  const magGap = mob.MATK > 0 ? Math.max(0, mob.MATK - p.MDEF.toNumber() * DMG.defFactor) : 0;
  const suggestions = [];
  if (physGap > 0) suggestions.push('PDEF 需 ≥ ' + Math.ceil(mob.PATK / DMG.defFactor) + '（当前 ' + Math.floor(p.PDEF.toNumber()) + '）');
  if (magGap > 0) suggestions.push('MDEF 需 ≥ ' + Math.ceil(mob.MATK / DMG.defFactor) + '（当前 ' + Math.floor(p.MDEF.toNumber()) + '）');
  if (p.REGEN.toNumber() < mob.PATK) suggestions.push('REGEN 需 ≥ ' + mob.PATK + '/s（当前 ' + Math.floor(p.REGEN.toNumber()) + '）');
  return { cause: magGap > physGap ? '魔伤击穿' : '物伤击穿', physDmg: physGap, magDmg: magGap, suggestions };
}

// M5: 离线结算（上限 8h，数学批量）
function settleOffline(deltaSec) {
  const capped = Math.min(deltaSec, MAX_OFFLINE_HOURS * 3600);
  if (capped < 60) return { skip:true };
  const p = getPanel();
  const punches = new D(p.ASPD * capped);
  settlePunches(punches);
  const pp = getPanel();
  S.life.curHP = D.min(pp.maxHP, S.life.curHP.plus(pp.REGEN.times(capped)));
  S.life.curMP = D.min(pp.MP, S.life.curMP.plus(pp.MREGEN.times(capped)));
  let died = false;
  if (!ZONES[S.zone].safe && S.mob) {
    if (!canKite(S.mob.def)) {
      const mobHits = Math.floor(S.mob.def.ASPD * capped * (1 - pp.EVA));
      if (mobHits > 0) {
        const physDmg = Math.max(1, S.mob.def.PATK - pp.PDEF.toNumber() * DMG.defFactor);
        const magDmg = S.mob.def.MATK > 0 ? Math.max(1, S.mob.def.MATK - pp.MDEF.toNumber() * DMG.defFactor) : 0;
        S.life.curHP = S.life.curHP.minus(mobHits * (physDmg + magDmg));
        if (S.life.curHP.lte(0)) { onPlayerDeath(); died = true; }
      }
    }
  }
  // 离线装备掉落：估算击杀数 × 区域掉率（最多 3 件，防通胀）
  let offlineDrops = 0;
  if (!died && !ZONES[S.zone].safe && S.mob && S.mob.def.maxHP !== Infinity) {
    const pEst = getPanel();
    const dmgPerPunch = Math.max(1, pEst.PATK.toNumber() + pEst.MATK.toNumber() - (S.mob.def.PDEF || 0) * DMG.defFactor);
    const estKills = Math.floor(punches.toNumber() * dmgPerPunch / S.mob.def.maxHP);
    if (estKills > 0) {
      const dropRate = DROP_RATE[S.zone] || 0;
      const drops = Math.min(3, Math.floor(estKills * dropRate));
      for (let i = 0; i < drops; i++) {
        if (S.equipBag.length >= BAG_MAX) break;
        const item = generateEquip(S.zone, true);   // force：期望已折算
        if (item) { S.equipBag.push(item); offlineDrops++; }
      }
      S.stats.kills += estKills;
      if (offlineDrops > 0) markInvDirty();
    }
  }
  markPanelDirty();
  return { died, seconds: capped, punches: punches.toString(), drops: offlineDrops };
}

// 逃跑（M2：移速差挂钩成功率）
function flee() {
  if (S.zone === 'village') return;
  const mob = S.mob;
  let rate = FLEE.base;
  if (mob) rate = Math.min(FLEE.cap, FLEE.base + (getPanel().SPD - mob.def.SPD) / FLEE.spdFactor);
  if (Math.random() < rate) {
    if (typeof toast === 'function') toast('🏃 逃跑成功，安全回村');
  } else {
    S.life.curHP = S.life.curHP.times(1 - FLEE.failCost);
    if (typeof toast === 'function') toast('💢 逃跑失败，扣 30% 血强制回村');
  }
  gotoZone('village');
  markPanelDirty();
}

// ============================================================
// 装备系统（M3：底子 + 随机词条）
// ============================================================

// 生成随机装备（按区域危险度决定品质和底子）
// force=true：跳过自身掉率判定（离线结算已折算期望，避免双重掷骰）
function generateEquip(zoneId, force) {
  const zone = ZONES[zoneId]; if (!zone || zone.safe) return null;
  if (!force) {
    const dropRate = DROP_RATE[zoneId] || 0;
    if (Math.random() > dropRate) return null;
  }
  // 品质随区域提升
  const tierBoost = Math.min(3, Math.floor(Object.keys(ZONES).indexOf(zoneId) / 2));
  const qRoll = Math.random() * 100 + tierBoost * 15;
  const qIdx = qRoll < 50 ? 0 : qRoll < 75 ? 1 : qRoll < 92 ? 2 : 3;
  const quality = QUALITY[qIdx];
  // 选底子：武器或防具
  const slotKeys = ['weapon','offhand','head','chest','legs','boots','ring','necklace','bow'];
  // 区域有弓手/冰法/炎魔时提高弓掉落
  let pool = [];
  for (const sk of slotKeys) {
    if (sk === 'weapon' || sk === 'bow') pool = pool.concat(WEAPONS.filter(w => (sk==='bow'? w.slot==='bow' : w.slot==='weapon')));
    else if (ARMOR_BASE[sk]) pool = pool.concat(ARMOR_BASE[sk].map(a => ({...a, slot: sk==='ring'?'ring1':sk})));  // ring 分配到 ring1
  }
  // 区域越深底子 tier 越高
  const minTier = Math.min(3, tierBoost);
  pool = pool.filter(p => (p.tier||0) >= minTier || Math.random() < 0.3);
  if (pool.length === 0) pool = WEAPONS;
  const base = pool[Math.floor(Math.random() * pool.length)];
  // 生成词条
  const affixes = [];
  const affixPool = [...AFFIXES];
  for (let i = 0; i < quality.affixCount && affixPool.length; i++) {
    const idx = Math.floor(Math.random() * affixPool.length);
    const a = affixPool.splice(idx, 1)[0];
    const val = a.min + Math.random() * (a.max - a.min);
    affixes.push({ name:a.name, key:a.key, type:a.type, val:val, rarity:a.rarity, side:a.side||null });
  }
  const item = {
    uid: S.nextUid++,
    name: base.name, emoji: base.emoji || '📦',
    slot: base.slot, base: {...base.base},
    affixes, quality: qIdx, enhance: 0,
    desc: base.desc || ''
  };
  return item;
}

// 新手保底武器（第 0 世平原前 5 杀必掉其一，助力脱离 1 攻地狱）
const NEWBIE_WEAPONS = ['新手木棍', '兔骨匕首', '铁剑', '匕首', '轻弩'];
function newbieGuaranteedDrop() {
  const name = NEWBIE_WEAPONS[S.newbieKills % NEWBIE_WEAPONS.length];
  const base = WEAPONS.find(w => w.name === name) || WEAPONS[0];
  // 保底装也走词条：固定白装品质（1 词条），保留刷词条乐趣
  const affixes = [];
  const affixPool = [...AFFIXES];
  if (affixPool.length) {
    const a = affixPool[Math.floor(Math.random() * affixPool.length)];
    const val = a.min + Math.random() * (a.max - a.min);
    affixes.push({ name:a.name, key:a.key, type:a.type, val:val, rarity:a.rarity, side:a.side||null });
  }
  return {
    uid: S.nextUid++,
    name: base.name, emoji: base.emoji || '📦',
    slot: base.slot, base: {...base.base},
    affixes, quality: 1, enhance: 0,
    desc: base.desc || ''
  };
}

// 掉落（击杀时调用）
function tryDropEquip(zoneId) {
  // 本世新手保底：任意非村口区域击杀都计数（转生后每世重新保底）
  // 前 5 杀必掉新手武器；第 ≥10 杀必掉史诗「回春戒」——flag 防错过，背包满不消耗机会
  if (zoneId !== 'village') {
    S.newbieKills = (S.newbieKills||0) + 1;
    // 戒指保底优先补发：≥10 杀且本世未发过
    if (S.newbieKills >= 10 && !S.ringGranted) {
      if (S.equipBag.length >= BAG_MAX) {
        if (typeof toast === 'function') toast('🎒 背包已满，戒指保底等待中——清理背包后下次击杀必得');
        return;  // 不置 flag，下次击杀重试
      }
      const ring = {
        uid: S.nextUid++,
        name: '回春戒', emoji: '💍', slot: 'ring1',
        base: { PATK: 2, REGEN: 3 },
        affixes: [
          { name:'生命泉涌', key:'REGEN_PCT', type:'pct_hp', val:0.01, rarity:'epic', side:null },
          { name:'锋利', key:'PATK', type:'pct', val:0.15, rarity:'common', side:null },
          { name:'坚韧', key:'REGEN', type:'pct', val:0.18, rarity:'common', side:null },
          { name:'灵巧', key:'SPD', type:'pct', val:0.10, rarity:'common', side:null }
        ],
        quality: 3, enhance: 0,
        desc: '十杀的嘉奖：每秒回复 1% 生命上限'
      };
      S.equipBag.push(ring);
      S.ringGranted = true;
      markInvDirty();
      _ach('equipGain', 1); _sfx('drop');
      if (typeof toast === 'function') toast('💜 史诗！掉落 💍 回春戒（生命泉涌：每秒回 1% 血量）');
      return;
    }
    // 前 5 杀：必掉新手武器
    if (S.newbieKills <= 5) {
      if (S.equipBag.length >= BAG_MAX) {
        if (typeof toast === 'function') toast('🎒 背包已满，保底武器掉落无法拾取');
        return;
      }
      const item = newbieGuaranteedDrop();
      S.equipBag.push(item);
      markInvDirty();
      _ach('equipGain', 1); _sfx('drop');
      if (typeof toast === 'function') toast('🎁 新手保底！掉落 ' + item.emoji + ' ' + item.name + '（' + S.newbieKills + '/5）');
      return;
    }
  }
  const item = generateEquip(zoneId);
  if (!item) return;
  if (S.equipBag.length >= BAG_MAX) {
    if (typeof toast === 'function') toast('🎒 背包已满，掉落了 ' + item.emoji + ' ' + item.name + ' 但无法拾取');
    return;
  }
  S.equipBag.push(item);
  markInvDirty();
  _ach('equipGain', 1); _sfx('drop');
  const q = QUALITY[item.quality];
  if (typeof toast === 'function') toast(q.name + '！掉落 ' + item.emoji + ' ' + item.name + '（' + q.affixCount + '词条）');
}

// 装备（从背包装备到对应部位）
function equipItem(bagIdx) {
  const item = S.equipBag[bagIdx];
  if (!item) return { ok:false, msg:'无此装备' };
  // 武器与弓互斥：装弓时卸武器，反之亦然
  if (item.slot === 'bow' && S.equipped.weapon) { S.equipBag.push(S.equipped.weapon); S.equipped.weapon = null; }
  if (item.slot === 'weapon' && S.equipped.bow) { S.equipBag.push(S.equipped.bow); S.equipped.bow = null; }
  // ring1/ring2 互不冲突
  let slot = item.slot;
  if (slot === 'ring1' || slot === 'ring') {
    if (S.equipped.ring1 && S.equipped.ring2) slot = 'ring1';  // 都满则替 ring1
    else slot = S.equipped.ring1 ? 'ring2' : 'ring1';
  }
  // 卸下旧装备
  if (S.equipped[slot]) S.equipBag.push(S.equipped[slot]);
  S.equipped[slot] = item;
  S.equipBag.splice(bagIdx, 1);
  markPanelDirty();
  markInvDirty();
  _ach('equipWear', item.quality);
  return { ok:true, msg:'已装备 ' + item.emoji + ' ' + item.name + ' → ' + slot };
}

// 卸装
function unequipItem(slot) {
  const item = S.equipped[slot];
  if (!item) return { ok:false, msg:'该部位无装备' };
  if (S.equipBag.length >= BAG_MAX) return { ok:false, msg:'背包已满' };
  S.equipBag.push(item);
  delete S.equipped[slot];
  markPanelDirty();
  markInvDirty();
  return { ok:true, msg:'已卸下 ' + item.emoji + ' ' + item.name };
}

// 丢弃
function discardItem(bagIdx) {
  const item = S.equipBag[bagIdx];
  if (!item) return { ok:false, msg:'无此装备' };
  S.equipBag.splice(bagIdx, 1);
  markInvDirty();
  return { ok:true, msg:'已丢弃 ' + item.emoji + ' ' + item.name };
}

// 强化（消耗金币，+5%底子/级）
function enhanceItem(bagIdx) {
  const item = S.equipBag[bagIdx];
  if (!item) return { ok:false, msg:'无此装备' };
  if ((item.enhance||0) >= ENHANCE.maxLv) return { ok:false, msg:'已强化满级' };
  const cost = Math.floor(ENHANCE.costBase * Math.pow(ENHANCE.costMul, item.enhance||0) * (item.quality+1));
  if (S.gold.lt(cost)) return { ok:false, msg:'金币不足（需 ' + cost + '）' };
  S.gold = S.gold.minus(cost);
  item.enhance = (item.enhance||0) + 1;
  markPanelDirty();
  markInvDirty();
  return { ok:true, msg:item.name + ' 强化 +' + item.enhance + '（消耗 ' + cost + ' 金币）' };
}

// 卖装备
function sellItem(bagIdx) {
  const item = S.equipBag[bagIdx];
  if (!item) return { ok:false, msg:'无此装备' };
  const price = Math.floor(20 * (item.quality+1) * (1 + (item.enhance||0)*0.5) * Math.pow(2, item.base?.tier||0));
  S.gold = S.gold.plus(price);
  S.equipBag.splice(bagIdx, 1);
  markPanelDirty();
  markInvDirty();
  _sfx('sell');
  return { ok:true, msg:'卖出 ' + item.name + ' +' + (item.enhance||0) + '，获得 ' + price + ' 金币' };
}

// 自动售卖：卖出背包中品阶低于 keepQ 的装备（0普通 1精良 2稀有 3史诗）
function autoSellBag(keepQ) {
  const keep = Math.max(0, Math.min(3, Number(keepQ) || 0));
  let count = 0, gold = 0;
  for (let i = S.equipBag.length - 1; i >= 0; i--) {
    const item = S.equipBag[i];
    if (!item || (item.quality || 0) >= keep) continue;   // 保底戒指(3)/高品质按阈值保留
    if (item.starter) continue;                            // 开局白给装备不卖（新手引导）
    const price = Math.floor(20 * (item.quality+1) * (1 + (item.enhance||0)*0.5) * Math.pow(2, item.base?.tier||0));
    S.gold = S.gold.plus(price);
    gold += price;
    S.equipBag.splice(i, 1);
    count++;
  }
  if (count > 0) { markPanelDirty(); markInvDirty(); }
  return { ok:true, count, gold, msg: count ? '自动售出 ' + count + ' 件，获得 ' + gold + ' 金币' : '' };
}

// ---------------- 通关判定 + 飞升转生（M7） ----------------
function checkRebirthReady() {
  return getPanel().ASPD >= Number(REBIRTH_ASPD);
}

function rebirth(chosenKey) {
  if (!checkRebirthReady()) return { ok:false, msg:'攻速未达 1 载次/秒' };
  if (chosenKey && BLESS_STEP[chosenKey]) {
    if (S.blessings[chosenKey]) {
      S.blessings[chosenKey].lv = (S.blessings[chosenKey].lv || 0) + 1;  // 强化纪元
    } else {
      S.blessings[chosenKey] = { lv: 0 };  // 新增赋能
    }
  }
  if (typeof graveyardAdd === 'function') {
    graveyardAdd({
      name: '拳圣', gen: S.gen, lived: typeof fmtDur==='function' ? fmtDur(Date.now()-S.stats.born) : '?',
      maxHP: getPanel().maxHP.toString(), cause: null, type: 'rebirth',
      epitaph: '第' + S.gen + '世飞升', ts: Date.now()
    });
  }
  const savedBlessings = S.blessings;
  const savedGen = S.gen + 1;
  const savedTalent = S.talent;      // 天赋与灵魂同在：转生保留
  S = newState();
  S.blessings = savedBlessings;
  S.gen = savedGen;
  S.talent = savedTalent;
  GAME_OVER = false; deathInfo = null;
  playerProg = 0; lastTick = null;
  spawnMob('village', false);
  markPanelDirty();
  _ach('rebirth', S.gen);
  _sfx('levelup');
  return { ok:true, msg:'飞升！第' + savedGen + '世' };
}

// ---------------- 启动 ----------------
function startEngine() {
  // M5: 自动加载最近存档（触发离线结算）
  if (typeof loadLatest === 'function') loadLatest();
  spawnMob(S.zone, false);
  setInterval(() => tick(Date.now()), TICK_MS);
}

// ---------------- 自动管家接口（136 特色） ----------------
window.__game = {
  get state() { return S; },
  panel: getPanel,
  gotoZone, flee, castBurn,
  settlePunches,
  markPanelDirty,
  isGameOver: () => GAME_OVER,
  deathInfo: () => deathInfo,
  canKite, checkZoneReq,
  equipItem, unequipItem, discardItem, enhanceItem, sellItem, generateEquip, tryDropEquip,
  castSkill, usePotion, buyPotion, learnSkill,
  autoSellBag, drawTalent, talentDef, eternalActive,
  startBossFight, endBossFight, settleOffline,
  rebirth, checkRebirthReady
};
