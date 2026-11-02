/* =====================================================================
 * gongpu 成就系统（独立 IIFE 模块，不修改任何现有 js）
 * ---------------------------------------------------------------------
 * 【接入方式】
 * 1) index.html：在 ui.js 之后追加一行
 *      <script src="js/achievements.js"></script>
 *    （成就内部会做每秒轮询兜底，挂不上钩子也能覆盖大部分成就；
 *      但下列"事件型"成就仍建议埋钩子，保证即时触发）
 *
 * 2) game.js 需要埋的钩子（调用 window.__achievements.check(condition, value)）：
 *
 *    a) 击杀结算点（kills 累加处，即杀怪掉落/经验处）：
 *         if (window.__achievements) __achievements.check('kills', S.stats.kills);
 *       —— 触发：首杀/百杀/千杀
 *
 *    b) 死亡结算点（S.stats.deaths++ 之后，showDeath 前后）：
 *         if (window.__achievements) __achievements.check('deaths', S.stats.deaths);
 *       —— 触发：首次死亡
 *
 *    c) 转生成功点（rebirth() 返回 ok 前，savedGen 已写入 S.gen）：
 *         if (window.__achievements) __achievements.check('rebirth', S.gen);
 *       —— 触发：首次转生
 *
 *    d) 天赋觉醒仪式成功写入 S.talent 处（觉醒抽卡确认后）：
 *         if (window.__achievements) __achievements.check('talent', S.talent);
 *       —— 触发：觉醒天赋
 *
 *    e) 装备入包点（tryDropEquip / generateEquip 成功后 push 进 equipBag 处，
 *       以及 starterKit 白给处可忽略——收藏成就按"获得装备次数"计）：
 *         if (window.__achievements) __achievements.check('equipGain', 1);
 *       —— 触发：收集 5 件装备（内部累计，跨世代保留）
 *
 *    f) 穿戴装备成功点（equipItem 成功处）：
 *         if (window.__achievements) __achievements.check('equipWear', item.quality);
 *       —— 触发：穿上史诗（quality >= 3）
 *
 *    g) 喝药成功点（usePotion 返回 ok:true 处）：
 *         if (window.__achievements) __achievements.check('usePotion', 1);
 *       —— 触发：使用 10 瓶药剂（内部累计，跨世代保留）
 *
 *    h) 打开自动管家面板点（autobot.js 面板打开/启用处）：
 *         if (window.__achievements) __achievements.check('butler', true);
 *       —— 触发：开启管家
 *
 * 3) 以下成就由本模块内置的 1 秒轮询自动完成，无需埋钩子：
 *      出拳 1 万次（stats.lifetimePunches）、存活 30 分钟（stats.born 起的
 *      本世连续在线时间按 session 计）、到达火山（S.zone === 'volcano'）、
 *      金币 1 万（S.gold）。若希望"到达火山/金币"即时弹窗，也可在
 *      gotoZone 成功处 / 金币入账处手动 check('zone', S.zone) / check('gold', S.gold)。
 *
 * 【持久化】localStorage key = gongpu_achievements
 *      结构：{ unlocked: {成就id: 解锁时间戳}, counters: {equipGain: n, usePotion: n} }
 *      成就不随转生清空；转生前的解锁记录保留。
 * ===================================================================== */
(function () {
  'use strict';

  var STORE_KEY = 'gongpu_achievements';

  /* ---------------- 成就定义 ----------------
   * cond: 事件名（check(condition, value) 或轮询使用）
   * target: 目标值；progress() 用它算进度
   */
  var ACH = [
    { id: 'first_kill',   name: '初试拳脚',   emoji: '👊', desc: '击杀第 1 只怪物',        cond: 'kills',      target: 1 },
    { id: 'kill_100',     name: '百炼成钢',   emoji: '🗡️', desc: '累计击杀 100 只怪物',     cond: 'kills',      target: 100 },
    { id: 'kill_1000',    name: '千人斩',     emoji: '⚔️', desc: '累计击杀 1000 只怪物',    cond: 'kills',      target: 1000 },
    { id: 'first_death',  name: '人生无常',   emoji: '💀', desc: '首次死亡',                cond: 'deaths',     target: 1 },
    { id: 'first_rebirth',name: '轮回转生',   emoji: '🔄', desc: '完成首次转生',            cond: 'rebirth',    target: 1 },
    { id: 'talent_awake', name: '天赋觉醒',   emoji: '✨', desc: '觉醒一枚本命天赋',        cond: 'talent',     target: 1 },
    { id: 'equip_5',      name: '小有家当',   emoji: '🎒', desc: '累计获得 5 件装备',        cond: 'equipGain',  target: 5 },
    { id: 'wear_epic',    name: '紫气东来',   emoji: '💜', desc: '穿上一件史诗品质装备',     cond: 'equipWear',  target: 3 },
    { id: 'punch_10k',    name: '拳不离手',   emoji: '🥊', desc: '出拳累计 10000 次',        cond: 'punches',    target: 10000 },
    { id: 'alive_30m',    name: '稳如老狗',   emoji: '🕰️', desc: '单世存活 30 分钟',         cond: 'aliveSec',   target: 1800 },
    { id: 'to_volcano',   name: '岩浆行者',   emoji: '🌋', desc: '抵达火山区域',            cond: 'zone',       target: 'volcano' },
    { id: 'gold_10k',     name: '万贯家财',   emoji: '🪙', desc: '持有金币达到 10000',      cond: 'gold',       target: 10000 },
    { id: 'potion_10',    name: '药不能停',   emoji: '🧪', desc: '累计使用 10 瓶药剂',       cond: 'usePotion',  target: 10 },
    { id: 'open_butler',  name: '有人伺候',   emoji: '🤵', desc: '开启自动管家',            cond: 'butler',     target: 1 }
  ];

  var defs = {};
  var i;
  for (i = 0; i < ACH.length; i++) defs[ACH[i].id] = ACH[i];

  /* ---------------- 持久化 ---------------- */
  var store = { unlocked: {}, counters: {} };

  function load() {
    try {
      var raw = window.localStorage ? window.localStorage.getItem(STORE_KEY) : null;
      if (!raw) return;
      var obj = JSON.parse(raw);
      if (obj && typeof obj === 'object') {
        if (obj.unlocked && typeof obj.unlocked === 'object') store.unlocked = obj.unlocked;
        if (obj.counters && typeof obj.counters === 'object') store.counters = obj.counters;
      }
    } catch (e) { /* 存档损坏时静默重置 */ }
  }

  function save() {
    try {
      if (window.localStorage) {
        window.localStorage.setItem(STORE_KEY, JSON.stringify(store));
      }
    } catch (e) { /* 隐私模式等写失败场景忽略 */ }
  }

  /* ---------------- 数值归一 ----------------
   * break_infinity 大数 → number；普通 number 原样返回 */
  function toNum(v) {
    if (v === null || v === undefined) return 0;
    if (typeof v === 'number') return v;
    if (typeof v.toNumber === 'function') return v.toNumber();
    if (typeof v.valueOf === 'function') {
      var n = Number(v.valueOf());
      return isNaN(n) ? 0 : n;
    }
    return Number(v) || 0;
  }

  /* ---------------- toast ---------------- */
  function notify(msg) {
    if (typeof window.toast === 'function') {
      window.toast(msg);
      return;
    }
    /* 自建简易 toast：右下角，3 秒消失 */
    try {
      var el = window.document.createElement('div');
      el.className = 'ach-toast';
      el.textContent = msg;
      var st = el.style;
      st.position = 'fixed'; st.right = '16px'; st.bottom = '16px'; st.zIndex = '99999';
      st.background = 'rgba(20,20,28,.92)'; st.color = '#ffd257';
      st.border = '1px solid #ffd257'; st.borderRadius = '8px';
      st.padding = '10px 14px'; st.fontSize = '14px'; st.fontFamily = 'inherit';
      st.boxShadow = '0 4px 16px rgba(0,0,0,.5)';
      st.transition = 'opacity .4s'; st.opacity = '0';
      window.document.body.appendChild(el);
      setTimeout(function () { st.opacity = '1'; }, 30);
      setTimeout(function () {
        st.opacity = '0';
        setTimeout(function () { if (el.parentNode) el.parentNode.removeChild(el); }, 450);
      }, 3000);
    } catch (e) { /* DOM 不可用时静默 */ }
  }

  /* ---------------- 解锁判定 ---------------- */
  function isUnlocked(id) { return !!store.unlocked[id]; }

  function unlock(rule) {
    store.unlocked[rule.id] = Date.now();
    save();
    notify('🏆 成就解锁 [' + rule.name + '] ' + rule.desc);
  }

  /*
   * condition 值 → 判定规则：
   *  kills/deaths/rebirth/punches/aliveSec/gold/equipGain/usePotion
   *      —— 数值型，value >= target 即解锁
   *  equipGain/usePotion —— 累加型：check 传增量，内部累计进 counters
   *  talent —— 任意非空天赋 id 即解锁
   *  equipWear —— value 为装备品质数字，>= 3（史诗）解锁
   *  zone —— value 字符串等于 target（如 'volcano'）
   *  butler —— 传任意真值即解锁
   */
  var ACCUM = { equipGain: true, usePotion: true };

  function check(condition, value) {
    var now;
    for (var idx = 0; idx < ACH.length; idx++) {
      var rule = ACH[idx];
      if (rule.cond !== condition || isUnlocked(rule.id)) continue;

      var ok = false;
      if (condition === 'talent') {
        ok = !!value;
      } else if (condition === 'zone' || condition === 'butler') {
        ok = (typeof rule.target === 'string') ? (value === rule.target) : !!value;
      } else if (ACCUM[condition]) {
        now = toNum(value);
        store.counters[condition] = toNum(store.counters[condition]) + now;
        ok = store.counters[condition] >= rule.target;
        save();
      } else {
        ok = toNum(value) >= rule.target;
      }

      if (ok) unlock(rule);
    }
  }

  /* ---------------- 查询接口 ---------------- */
  function list() {
    var out = [];
    for (var idx = 0; idx < ACH.length; idx++) {
      var r = ACH[idx];
      out.push({
        id: r.id, name: r.name, desc: r.desc, emoji: r.emoji,
        unlocked: isUnlocked(r.id),
        unlockedAt: store.unlocked[r.id] || null
      });
    }
    return out;
  }

  function unlocked() {
    var all = list(), out = [];
    for (var idx = 0; idx < all.length; idx++) {
      if (all[idx].unlocked) out.push(all[idx]);
    }
    return out;
  }

  function currentOf(rule, state) {
    if (ACCUM[rule.cond]) return Math.min(toNum(store.counters[rule.cond]), rule.target);
    if (!state) return 0;
    switch (rule.cond) {
      case 'kills':      return toNum(state.stats && state.stats.kills);
      case 'deaths':     return toNum(state.stats && state.stats.deaths);
      case 'rebirth':    return toNum(state.gen);
      case 'talent':     return state.talent ? 1 : 0;
      case 'equipWear': {
        var eq = state.equipped || {}, best = 0;
        for (var slot in eq) {
          var it = eq[slot];
          if (it && toNum(it.quality) > best) best = toNum(it.quality);
        }
        return best;
      }
      case 'punches':    return toNum(state.stats && state.stats.lifetimePunches);
      case 'aliveSec':   return state.stats && state.stats.born ? (Date.now() - state.stats.born) / 1000 : 0;
      case 'gold':       return toNum(state.gold);
      case 'zone':       return state.zone === rule.target ? 1 : 0;
      case 'butler':     return isUnlocked(rule.id) ? 1 : 0;
      default:           return 0;
    }
  }

  function progress() {
    var state = getState();
    var out = [];
    for (var idx = 0; idx < ACH.length; idx++) {
      var r = ACH[idx];
      var numeric = (typeof r.target === 'number');
      var cur = numeric ? currentOf(r, state) : (isUnlocked(r.id) ? 1 : 0);
      var tgt = numeric ? r.target : 1;
      out.push({
        id: r.id,
        name: r.name,
        unlocked: isUnlocked(r.id),
        current: cur,
        target: numeric ? r.target : r.target,
        pct: Math.max(0, Math.min(1, cur / tgt))
      });
    }
    return out;
  }

  function getState() {
    try {
      return (window.__game && window.__game.state) ? window.__game.state : null;
    } catch (e) { return null; }
  }

  /* ---------------- 轮询兜底（状态型成就） ---------------- */
  var POLL_CONDS = ['kills', 'deaths', 'punches', 'aliveSec', 'zone', 'gold', 'talent', 'rebirth', 'equipWear'];

  function poll() {
    var state = getState();
    if (!state) return; // 游戏未就绪
    for (var idx = 0; idx < POLL_CONDS.length; idx++) {
      var cond = POLL_CONDS[idx];
      for (var j = 0; j < ACH.length; j++) {
        var r = ACH[j];
        if (r.cond !== cond || isUnlocked(r.id)) continue;
        check(cond, condValue(cond, state));
      }
    }
  }

  function condValue(cond, state) {
    switch (cond) {
      case 'kills':      return state.stats && state.stats.kills;
      case 'deaths':     return state.stats && state.stats.deaths;
      case 'punches':    return state.stats && state.stats.lifetimePunches;
      case 'aliveSec':   return state.stats && state.stats.born ? (Date.now() - state.stats.born) / 1000 : 0;
      case 'zone':       return state.zone;
      case 'gold':       return state.gold;
      case 'talent':     return state.talent;
      case 'rebirth':    return state.gen;
      case 'equipWear': {
        var eq = state.equipped || {}, best = 0;
        for (var slot in eq) {
          var it = eq[slot];
          if (it && toNum(it.quality) > best) best = toNum(it.quality);
        }
        return best;
      }
      default: return 0;
    }
  }

  /* ---------------- 启动 ---------------- */
  load();
  /* 1 秒轮询：状态型成就即使没埋钩子也能解锁 */
  if (typeof window.setInterval === 'function') {
    window.setInterval(poll, 1000);
  }

  /* ---------------- 对外接口 ---------------- */
  window.__achievements = {
    check: check,
    list: list,
    unlocked: unlocked,
    progress: progress,
    _store: store
  };
})();
