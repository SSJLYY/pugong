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

  // 第十五批·轮 5：token 键版本化——读写 gongpu_token_v2；旧键启动时迁移后移除
  const TOKEN_KEY = 'gongpu_token_v2';
  const USER_KEY = 'gongpu.user';
  // 历史旧键：规范指定键 gongpu_token + 实际代码沿用过的 gongpu.token，都要迁走
  const LEGACY_TOKEN_KEYS = ['gongpu_token', 'gongpu.token'];

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

  // 第十五批·轮 5：旧 token 键迁移——v2 键为空则从旧键搬入，随后一律 removeItem 旧键（老用户不掉登录）
  (function migrateLegacyToken() {
    if (!lsGet(TOKEN_KEY)) {
      for (var i = 0; i < LEGACY_TOKEN_KEYS.length; i++) {
        var legacy = lsGet(LEGACY_TOKEN_KEYS[i]);
        if (legacy) {
          lsSet(TOKEN_KEY, legacy);
          break;
        }
      }
    }
    for (var j = 0; j < LEGACY_TOKEN_KEYS.length; j++) {
      lsDel(LEGACY_TOKEN_KEYS[j]);
    }
  })();

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

  // 第十五批·轮 1：默认请求超时 15s；request(method, path, body, opts) 可用 opts.timeout 覆盖
  const REQUEST_TIMEOUT_MS = 15000;
  // 第十五批·轮 2：幂等 GET 遇 429 时指数退避重试（1s/2s/4s，最多 3 次）；写请求不重试
  const GET_RETRY_BASE_MS = 1000;
  const GET_RETRY_MAX = 3;

  class ApiError extends Error {
    constructor(code, message, status) {
      super(message);
      this.code = code;
      this.status = status;
    }
  }

  // 第十五批·C1f·轮 1：错误文案映射表——所有抛 ApiError 的地方统一从这里取中文文案；
  // app.js 直接 toast(e.message)，改表即改全站报错话术，不用再到处找内联文案
  const ERR_TEXT = {
    ERR_NET: '网络连接失败，请检查网络',
    ERR_TIMEOUT: '请求超时，请稍后重试',
    ERR_SERVER: '服务器开小差了，稍后再试',
    ERR_AUTH: '登录已过期，请重新登录'
  };

  // 第十五批·轮 1/2/5：原始 fetch 封装——超时 abort、429 重试、GET 并发节流都在这一层
  async function rawRequest(method, path, body, timeout) {
    const headers = { 'Content-Type': 'application/json' };
    if (state.token) {
      headers['Authorization'] = 'Bearer ' + state.token;
    }
    let res;
    const t0 = performance.now();
    const ctl = new AbortController();
    const killer = setTimeout(() => ctl.abort(), timeout);
    try {
      res = await fetch(API_BASE + path, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: ctl.signal
      });
      state.online = true;
      // 第十五批·轮8：响应耗时埋点——最近一次请求的往返毫秒数，调试面板/排障时可直接读
      window.__gp_last_latency = Math.round(performance.now() - t0);
      // 滑动续期：服务端在令牌快到期时通过响应头回新令牌，无感替换本地存储
      const nt = res.headers.get('X-New-Token');
      if (nt && nt !== state.token) {
        state.token = nt;
        lsSet(TOKEN_KEY, nt);
      }
    } catch (e) {
      state.online = false;
      // 超时(abort)抛 ERR_TIMEOUT,与断线区分;轮询循环 catch 后自动续转,不会永久卡死
      if (e && (e.name === 'AbortError' || e.name === 'TimeoutError')) {
        throw new ApiError('ERR_TIMEOUT', ERR_TEXT.ERR_TIMEOUT, 0);
      }
      throw new ApiError('ERR_NET', ERR_TEXT.ERR_NET, 0);
    } finally {
      clearTimeout(killer);
    }
    return res;
  }

  // 第十五批·轮 5：GET 并发节流——FIFO 队列，同刻最多 GET_CONCURRENCY 个 GET 在飞，超出排队；写请求不排队
  const GET_CONCURRENCY = 4;
  const getQueue = [];
  let getInFlight = 0;

  function acquireGetSlot() {
    return new Promise(resolve => {
      if (getInFlight < GET_CONCURRENCY) {
        getInFlight++;
        resolve();
      } else {
        getQueue.push(resolve);
      }
    });
  }

  function releaseGetSlot() {
    const next = getQueue.shift();
    if (next) {
      next();
    } else {
      getInFlight = Math.max(0, getInFlight - 1);
    }
  }

  // 第十五批·轮 2/C1f：429 重试包装（仅幂等 GET）——超时/断网由 rawRequest 抛 ApiError，
  // 429 重试只对 GET 生效；拿到响应后统一交给 parseAndCheck 解析（含 !res.ok 抛错）
  async function request(method, path, body, opts) {
    opts = opts || {};
    const timeout = opts.timeout > 0 ? opts.timeout : REQUEST_TIMEOUT_MS;
    if (method !== 'GET') {
      // 写请求：不节流不重试，直接解析
      const res = await rawRequest(method, path, body, timeout);
      return parseAndCheck(res, path);
    }
    // GET：走并发节流 + 429 指数退避重试
    await acquireGetSlot();
    try {
      for (var attempt = 0; ; attempt++) {
        const res = await rawRequest('GET', path, body, timeout);
        if (res.status !== 429) {
          return await parseAndCheck(res, path);
        }
        if (attempt >= GET_RETRY_MAX) {
          return await parseAndCheck(res, path); // 超过最大重试次数：把 429 响应交给解析层抛错
        }
        await sleep(GET_RETRY_BASE_MS * Math.pow(2, attempt));
      }
    } finally {
      releaseGetSlot();
    }
  }

  function sleep(ms) {
    return new Promise(r => setTimeout(r, ms));
  }

  // 第十五批·C1f·轮 1：响应统一解析——原函数头在上一轮编辑中丢失导致顶层 await 语法错，
  // 已重建并接回 request()；非 2xx 一律抛 ApiError，文案优先走 ERR_TEXT 映射表
  async function parseAndCheck(res, path) {
    let data = null;
    const text = await res.text();
    if (text) {
      try { data = JSON.parse(text); } catch (e) { data = null; }
    }

    if (!res.ok) {
      const isAuthApi = path.indexOf('/api/auth/') === 0;
      // 401 只对会话过期生效;登录/注册接口的 401 是"密码错误",不能误清 token + 广播"已过期"
      if (res.status === 401 && !isAuthApi) {
        clearAuth();
        global.dispatchEvent(new CustomEvent('gongpu:unauthorized'));
      }
      const code = (data && data.code) || 'HTTP_' + res.status;
      let message = (data && data.message) || '请求失败（' + res.status + '）';
      // 第十五批·C1f·轮 1：5xx 对玩家统一"服务器开小差"；会话过期 401 固定提示重登——
      // 后端这两类的报错文案常是开发向措辞/英文堆栈，直接 toast 出去没有意义
      if (res.status >= 500) {
        message = ERR_TEXT.ERR_SERVER;
      } else if (res.status === 401 && !isAuthApi) {
        message = ERR_TEXT.ERR_AUTH;
      }
      throw new ApiError(code, message, res.status);
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
    renameSave: (slot, name) => request('POST', '/api/saves/' + slot + '/rename', { name }),
    loadSave: (slot) => request('GET', '/api/saves/' + slot),
    manualSave: (slot) => request('POST', '/api/saves/' + slot + '/save'),
    deleteSave: (slot) => request('DELETE', '/api/saves/' + slot),

    // ---------- 游戏 ----------
    gameState: (slot) => request('GET', '/api/game/' + slot + '/state'),
    setZone: (slot, zone) => request('POST', '/api/game/' + slot + '/zone', { zone }),
    sweep: (slot, count) => request('POST', '/api/game/' + slot + '/sweep', { count }),
    inherit: (slot, from, to) => request('POST', '/api/game/' + slot + '/inherit', { from, to }),
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
    rerollItem: (slot, index, lockIndex, lockIndex2) => request('POST', '/api/game/' + slot + '/reroll', { index, lockIndex: lockIndex == null ? -1 : lockIndex, lockIndex2: lockIndex2 == null ? -1 : lockIndex2 }),
    temperItem: (slot, index) => request('POST', '/api/game/' + slot + '/temper', { index }),
    starUp: (slot, index) => request('POST', '/api/game/' + slot + '/star-up', { index }),
    awaken: (slot, index) => request('POST', '/api/game/' + slot + '/awaken', { index }),
    socketGem: (slot, index, gem) => request('POST', '/api/game/' + slot + '/socket', { index, gem }),
    unsocketGem: (slot, index, gemIndex) => request('POST', '/api/game/' + slot + '/unsocket', { index, gemIndex }),
    fuseGem: (slot, id) => request('POST', '/api/game/' + slot + '/fuse-gem', { id }),
    sellAllMaterials: (slot) => request('POST', '/api/game/' + slot + '/sell-all-materials'),
    fuseAllGems: (slot) => request('POST', '/api/game/' + slot + '/fuse-all-gems'),
    unsocketAll: (slot) => request('POST', '/api/game/' + slot + '/unsocket-all'),
    feedPetBatch: (slot) => request('POST', '/api/game/' + slot + '/feed-pet-batch'),
    feedPetMax: (slot) => request('POST', '/api/game/' + slot + '/feed-pet-max'),
    sellExtraBlueprints: (slot) => request('POST', '/api/game/' + slot + '/sell-extra-blueprints'),
    matExchangeAll: (slot, from, to) => request('POST', '/api/game/' + slot + '/mat-exchange-all', { from, to }),
    claimDaily: (slot, id) => request('POST', '/api/game/' + slot + '/daily-claim', { id }),
    claimWeekly: (slot, id) => request('POST', '/api/game/' + slot + '/weekly-claim', { id }),
    claimMonthly: (slot, id) => request('POST', '/api/game/' + slot + '/monthly-claim', { id }),
    adoptPet: (slot, id) => request('POST', '/api/game/' + slot + '/adopt-pet', { id }),
    evolvePet: (slot, id) => request('POST', '/api/game/' + slot + '/evolve-pet', { id }),
    switchPet: (slot, id) => request('POST', '/api/game/' + slot + '/switch-pet', { id }),
    feedPet: (slot) => request('POST', '/api/game/' + slot + '/feed-pet'),
    renamePet: (slot, id, name) => request('POST', '/api/game/' + slot + '/rename-pet', { id, name }),
    marketBuy: (slot, id) => request('POST', '/api/game/' + slot + '/market-buy', { id }),
    autoSell: (slot, keep) => request('POST', '/api/game/' + slot + '/autosell', { keep: keep }),
    flee: (slot) => request('POST', '/api/game/' + slot + '/flee'),
    autoEquip: (slot) => request('POST', '/api/game/' + slot + '/autoequip'),
    quests: (slot) => request('GET', '/api/game/' + slot + '/quests'),
    craftBoard: (slot) => request('GET', '/api/game/' + slot + '/crafts'),
    craftDo: (slot, code) => request('POST', '/api/game/' + slot + '/craft', { code }),
    matExchange: (slot, from, to) => request('POST', '/api/game/' + slot + '/mat-exchange', { from, to }),
    sellMaterial: (slot, id, count) => request('POST', '/api/game/' + slot + '/sell-material', { id, count }),
    buyMaterial: (slot, id, count) => request('POST', '/api/game/' + slot + '/buy-material', { id, count }),
    equipSet: (slot, id) => request('POST', '/api/game/' + slot + '/equip-set', { id }),
    buyGem: (slot, id) => request('POST', '/api/game/' + slot + '/buy-gem', { id }),
    buyFill: (slot, id, target) => request('POST', '/api/game/' + slot + '/buyfill', { id, target }),
    buyFillKit: (slot) => request('POST', '/api/game/' + slot + '/buyfill-kit', {}),
    questAccept: (slot, code) => request('POST', '/api/game/' + slot + '/quest/accept', { code }),
    questDeliver: (slot, code) => request('POST', '/api/game/' + slot + '/quest/deliver', { code }),
    questAbandon: (slot, code) => request('POST', '/api/game/' + slot + '/quest/abandon', { code }),
    enchantItem: (slot, scroll, index) => request('POST', '/api/game/' + slot + '/enchant', { scroll, index }),
    autoConfig: (slot, hpLine, mpLine, autoCure, fleeLine, autoBurn, autoSkill, autoRebirth, autoSellKeep, autoEquipBest, autoEat, autoSmeltKeep, autoAdvance, autoBoss) => request('POST', '/api/game/' + slot + '/autoconfig', { hpLine, mpLine, autoCure, fleeLine, autoBurn, autoSkill, autoRebirth, autoSellKeep, autoEquipBest, autoEat, autoSmeltKeep, autoAdvance, autoBoss }),
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
