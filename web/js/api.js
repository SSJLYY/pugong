/* ============================================================
 * 普攻永久增加生命上限 v2.0 · api.js
 * 与后端通信层：所有数值都由服务端结算，前端只发意图
 * ============================================================ */
(function (global) {
  'use strict';

  // 页面部署在 /gongpu/ 下，nginx 已将 /gongpu/api/ 反代到后端 /api/。
  // 用相对路径：本地调试（页面直接在后端根路径打开）也能工作。
  function apiBase() {
    // 首段不含点(排除 /index.html 这类文件名)视为部署前缀;/gongpu 与 /gongpu/ 都兼容
    var m = location.pathname.match(/^(\/[^\/.]+)(?=\/|$)/);
    return m ? m[1] : '';
  }
  // 例：页面在 /gongpu/ 下 → API_BASE='/gongpu'，请求 '/gongpu/api/...'
  //     页面在后端根下 → API_BASE=''，请求 '/api/...'
  const API_BASE = apiBase();

  const TOKEN_KEY = 'gongpu.token';
  const USER_KEY = 'gongpu.user';

  // localStorage 在隐私模式/被禁用/损坏时抛异常——不能让整个 api.js IIFE 挂掉整页白屏
  function lsGet(k) {
    try { return localStorage.getItem(k); } catch (e) { return null; }
  }
  function lsSet(k, v) {
    try { localStorage.setItem(k, v); } catch (e) { /* 忽略 */ }
  }
  function lsDel(k) {
    try { localStorage.removeItem(k); } catch (e) { /* 忽略 */ }
  }
  function lsJson(k) {
    try { return JSON.parse(lsGet(k) || 'null'); } catch (e) { return null; }
  }

  const state = {
    token: lsGet(TOKEN_KEY) || '',
    user: lsJson(USER_KEY),
    slot: Number(lsGet('gongpu.slot') || 0) || 0,
    online: true
  };

  function setAuth(payload) {
    state.token = payload.token;
    state.user = { id: payload.userId, username: payload.username };
    lsSet(TOKEN_KEY, state.token);
    lsSet(USER_KEY, JSON.stringify(state.user));
  }

  function clearAuth() {
    state.token = '';
    state.user = null;
    lsDel(TOKEN_KEY);
    lsDel(USER_KEY);
  }

  function setSlot(slot) {
    state.slot = slot;
    if (slot) {
      lsSet('gongpu.slot', String(slot));
    } else {
      lsDel('gongpu.slot');
    }
  }

  const REQUEST_TIMEOUT_MS = 15000;

  class ApiError extends Error {
    constructor(code, message, status) {
      super(message);
      this.code = code;
      this.status = status;
    }
  }

  async function request(method, path, body) {
    const headers = { 'Content-Type': 'application/json' };
    if (state.token) {
      headers['Authorization'] = 'Bearer ' + state.token;
    }
    let res;
    const ctl = new AbortController();
    const killer = setTimeout(() => ctl.abort(), REQUEST_TIMEOUT_MS);
    try {
      res = await fetch(API_BASE + path, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: ctl.signal
      });
      state.online = true;
      // 滑动续期：服务端在令牌快到期时通过响应头回新令牌，无感替换本地存储
      const nt = res.headers.get('X-New-Token');
      if (nt && nt !== state.token) {
        state.token = nt;
        lsSet(TOKEN_KEY, nt);
      }
    } catch (e) {
      state.online = false;
      // AbortController 超时与断线同义:轮询循环 catch 后自动续转,不会永久卡死
      throw new ApiError('NETWORK', '网络断开，正在重连…', 0);
    } finally {
      clearTimeout(killer);
    }

    let data = null;
    const text = await res.text();
    if (text) {
      try { data = JSON.parse(text); } catch (e) { data = null; }
    }

    if (!res.ok) {
      // 401 只对会话过期生效;登录/注册接口的 401 是"密码错误",不能误清 token + 广播"已过期"
      if (res.status === 401 && path.indexOf('/api/auth/') !== 0) {
        clearAuth();
        global.dispatchEvent(new CustomEvent('gongpu:unauthorized'));
      }
      throw new ApiError(
        (data && data.code) || 'HTTP_' + res.status,
        (data && data.message) || '请求失败（' + res.status + '）',
        res.status
      );
    }
    return data;
  }

  const api = {
    state,
    ApiError,
    setAuth,
    clearAuth,
    setSlot,
    statPoint: (slot, stat, count) => request('POST', '/api/game/' + slot + '/statpoint', { stat, count }),

    // ---------- 账号 ----------
    register: (username, password) =>
      request('POST', '/api/auth/register', { username, password }),
    login: (username, password) =>
      request('POST', '/api/auth/login', { username, password }),
    changePassword: (oldPassword, newPassword) =>
      request('POST', '/api/auth/password', { oldPassword, newPassword }),

    // ---------- 元数据 ----------
    meta: () => request('GET', '/api/config/meta'),
    health: () => request('GET', '/api/health'),

    // ---------- 存档 ----------
    listSaves: () => request('GET', '/api/saves'),
    createSave: (slot, slotName) => request('POST', '/api/saves/' + slot, { slotName }),
    loadSave: (slot) => request('GET', '/api/saves/' + slot),
    manualSave: (slot) => request('POST', '/api/saves/' + slot + '/save'),
    deleteSave: (slot) => request('DELETE', '/api/saves/' + slot),

    // ---------- 游戏 ----------
    gameState: (slot) => request('GET', '/api/game/' + slot + '/state'),
    setZone: (slot, zone) => request('POST', '/api/game/' + slot + '/zone', { zone }),
    castSkill: (slot, id) => request('POST', '/api/game/' + slot + '/skill', { id }),
    usePotion: (slot, id) => request('POST', '/api/game/' + slot + '/potion', { id }),
    buyPotion: (slot, id, count) => request('POST', '/api/game/' + slot + '/buy', { id, count }),
    equip: (slot, index) => request('POST', '/api/game/' + slot + '/equip', { index }),
    equipExtra: (slot, index) => request('POST', '/api/game/' + slot + '/equip-extra', { index }),
    unequip: (slot, equipSlot) => request('POST', '/api/game/' + slot + '/unequip', { slot: equipSlot }),
    sellItem: (slot, index) => request('POST', '/api/game/' + slot + '/sell', { index }),
    dismantleItem: (slot, index) => request('POST', '/api/game/' + slot + '/dismantle', { index }),
    dismantleAll: (slot, keep) => request('POST', '/api/game/' + slot + '/dismantle-all', { keep: keep }),
    claimAchievement: (slot, id) => request('POST', '/api/game/' + slot + '/ach-claim', { id }),
    claimAllAchievements: (slot) => request('POST', '/api/game/' + slot + '/ach-claim-all', {}),
    enhanceItem: (slot, index) => request('POST', '/api/game/' + slot + '/enhance', { index }),
    discardItem: (slot, index) => request('POST', '/api/game/' + slot + '/discard', { index }),
    toggleLock: (slot, index) => request('POST', '/api/game/' + slot + '/lock', { index }),
    sortBag: (slot) => request('POST', '/api/game/' + slot + '/sortbag'),
    rerollItem: (slot, index, lockIndex) => request('POST', '/api/game/' + slot + '/reroll', { index, lockIndex: lockIndex == null ? -1 : lockIndex }),
    autoSell: (slot, keep) => request('POST', '/api/game/' + slot + '/autosell', { keep: keep }),
    flee: (slot) => request('POST', '/api/game/' + slot + '/flee'),
    autoEquip: (slot) => request('POST', '/api/game/' + slot + '/autoequip'),
    quests: (slot) => request('GET', '/api/game/' + slot + '/quests'),
    craftBoard: (slot) => request('GET', '/api/game/' + slot + '/crafts'),
    craftDo: (slot, code) => request('POST', '/api/game/' + slot + '/craft', { code }),
    matExchange: (slot, from, to) => request('POST', '/api/game/' + slot + '/mat-exchange', { from, to }),
    buyFill: (slot, id, target) => request('POST', '/api/game/' + slot + '/buyfill', { id, target }),
    questAccept: (slot, code) => request('POST', '/api/game/' + slot + '/quest/accept', { code }),
    questDeliver: (slot, code) => request('POST', '/api/game/' + slot + '/quest/deliver', { code }),
    questAbandon: (slot, code) => request('POST', '/api/game/' + slot + '/quest/abandon', { code }),
    enchantItem: (slot, scroll, index) => request('POST', '/api/game/' + slot + '/enchant', { scroll, index }),
    autoConfig: (slot, hpLine, mpLine, autoCure, fleeLine, autoBurn, autoSkill, autoRebirth, autoSellKeep, autoEquipBest, autoEat, autoSmeltKeep) => request('POST', '/api/game/' + slot + '/autoconfig', { hpLine, mpLine, autoCure, fleeLine, autoBurn, autoSkill, autoRebirth, autoSellKeep, autoEquipBest, autoEat, autoSmeltKeep }),
    startBoss: (slot) => request('POST', '/api/game/' + slot + '/boss'),
    learnPassive: (slot, id) => request('POST', '/api/game/' + slot + '/learn', { id }),
    dailyClaim: (slot) => request('POST', '/api/game/' + slot + '/daily'),
    rebirth: (slot, key) => request('POST', '/api/game/' + slot + '/rebirth', { key }),
    blessOptions: (slot) => request('GET', '/api/game/' + slot + '/bless-options'),
    deathAction: (slot, action, extra) => request('POST', '/api/game/' + slot + '/death', Object.assign({ action: action }, extra || {})),

    // ---------- 榜单 ----------
    leaderboard: (board) => request('GET', '/api/leaderboard' + (board ? '?board=' + encodeURIComponent(board) : '')),

    // ---------- 天赋 ----------
    talentView: (slot) => request('GET', '/api/game/' + slot + '/talent'),
    drawTalent: (slot, key) => request('POST', '/api/game/' + slot + '/talent', { key }),

    // ---------- 社交 ----------
    socialMe: () => request('GET', '/api/social/me'),
    bulletin: () => request('GET', '/api/social/bulletin'),
    worldGraveyard: () => request('GET', '/api/social/graveyard'),
    tribute: (graveId, slot) => request('POST', '/api/social/tribute', { graveId, slot }),
    incenseRevive: (slot) => request('POST', '/api/social/revive', { slot }),
    duel: (target, slot) => request('POST', '/api/social/duel', { target, slot }),
    graveyardMine: () => request('GET', '/api/graveyard')
  };

  global.GP_API = api;
})(window);
