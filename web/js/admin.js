/* ============================================================
 * 普攻 · 运维端 admin.js
 * 纯原生 JS · 无框架 · 管理员控制台
 * API 契约与游戏端一致：错误体 {ok:false,code,message}
 * ============================================================ */
(function (global) {
  'use strict';

  /* ---------- API 基址（与 web/js/api.js 同规则：取 pathname 首段前缀） ---------- */
  function apiBase() {
    var m = location.pathname.match(/^(\/[^\/.]+)(?=\/|$)/);
    return m ? m[1] : '';
  }
  const API_BASE = apiBase(); // 页面在 /gongpu/ 下 → '/gongpu'，根路径 → ''
  const TOKEN_KEY = 'gongpu.admin.token';
  const DENSE_KEY = 'gongpu_admin_dense';    // 第十五批·轮6：表格密度记忆（'1'=紧凑）
  const REFRESH_KEY = 'gongpu_admin_refresh'; // 第十五批·轮7：自动刷新间隔记忆（秒，0=关闭）

  /* ---------- 小工具 ---------- */
  const $ = function (id) { return document.getElementById(id); };

  // 所有用户输入进 innerHTML 前必须经过 esc
  function esc(v) {
    if (v === null || v === undefined) return '';
    return String(v)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function pad(n) { return n < 10 ? '0' + n : '' + n; }
  // 时间兼容：毫秒/秒时间戳、ISO 字符串；无法解析则原样（已转义）
  function fmtTime(v) {
    if (v === null || v === undefined || v === '' || v === 0) return '-';
    var n = Number(v);
    var d;
    if (isFinite(n) && String(v).trim() !== '') {
      d = new Date(n < 1e12 ? n * 1000 : n);
    } else {
      d = new Date(v);
    }
    if (isNaN(d.getTime())) return esc(v);
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) +
      ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
  }

  /* ---------- Toast ---------- */
  var toastTimer = null;
  function toast(msg, type) {
    var el = $('toast');
    if (!el) return;
    el.textContent = msg;
    el.className = 'toast show ' + (type === 'err' ? 'err' : type === 'ok' ? 'ok' : '');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.className = 'toast'; }, 2600);
  }

  /* ---------- 请求封装（API_BASE 前缀 + token 注入） ---------- */
  class ApiError extends Error {
    constructor(code, message, status) {
      super(message);
      this.code = code;
      this.status = status;
    }
  }

  // localStorage 安全封装:隐私模式/被禁用时直接读写会抛异常,整个 IIFE 挂死成空壳
  function lsGet(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
  function lsSet(k, v) { try { localStorage.setItem(k, v); } catch (e) { /* 忽略 */ } }
  function lsDel(k) { try { localStorage.removeItem(k); } catch (e) { /* 忽略 */ } }

  const state = {
    token: lsGet(TOKEN_KEY) || '',
    currentTab: 'users',
    trendMetric: 'punches', // 趋势图当前指标
    trendRows: null         // 趋势图原始 rows（null=未加载）
  };

  function setToken(t) {
    state.token = t || '';
    if (t) { lsSet(TOKEN_KEY, t); }
    else { lsDel(TOKEN_KEY); }
  }

  async function request(method, path, body, opts) {
    opts = opts || {};
    const headers = { 'Content-Type': 'application/json' };
    if (state.token) { headers['Authorization'] = 'Bearer ' + state.token; }
    let res;
    const ctl = new AbortController();
    const killer = setTimeout(function () { ctl.abort(); }, 15000);
    try {
      res = await fetch(API_BASE + path, {
        method: method,
        headers: headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: ctl.signal
      });
    } catch (e) {
      throw new ApiError('NETWORK', '网络错误，请稍后重试', 0);
    } finally {
      clearTimeout(killer);
    }
    let data = null;
    const text = await res.text();
    if (text) { try { data = JSON.parse(text); } catch (e) { data = null; } }
    if (!res.ok) {
      // 401/403：token 失效 → 清 token 回登录页（登录请求本身除外）
      if ((res.status === 401 || res.status === 403) && !opts.skipAuthRedirect) {
        setToken('');
        showLogin();
        toast('登录已失效，请重新登录', 'err');
      }
      throw new ApiError(
        (data && data.code) || ('HTTP_' + res.status),
        (data && data.message) || ('请求失败（' + res.status + '）'),
        res.status
      );
    }
    return data;
  }

  /* ============================================================
   * 第十五批·轮6：表格密度切换（紧凑/舒适）
   * body 挂 .dense 类收紧单元格 padding；localStorage gongpu_admin_dense 记忆
   * ============================================================ */
  function isDense() {
    return document.body.classList.contains('dense');
  }

  function applyDensity(dense) {
    document.body.classList.toggle('dense', !!dense);
    lsSet(DENSE_KEY, dense ? '1' : '0');
    var btn = $('btnDensity');
    if (btn) {
      // 按钮文案显示「当前可切换到的目标态」，语义与点击结果一致
      btn.textContent = dense ? '舒适' : '紧凑';
      btn.title = dense ? '当前：紧凑 · 点击切换为舒适' : '当前：舒适 · 点击切换为紧凑';
      btn.setAttribute('aria-pressed', dense ? 'true' : 'false');
    }
  }

  function toggleDensity() {
    applyDensity(!isDense());
  }

  /* ---------- 第十五批·轮7：自动刷新间隔（关闭/5s/15s/60s，localStorage 记忆，0=停止） ---------- */
  var refreshTimer = null;
  function applyRefreshInterval(sec) {
    sec = Number(sec) || 0;
    if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = null; } // 接管旧定时器
    lsSet(REFRESH_KEY, sec ? String(sec) : '0');
    var sel = $('refreshSel');
    if (sel) sel.value = String(sec);
    if (sec > 0) {
      refreshTimer = setInterval(function () {
        if (!state.token) { applyRefreshInterval(0); return; } // 已退出登录则停止
        loadTab(state.currentTab); // 重拉当前页数据
        loadHealth(); // 第十五批·轮4：健康徽章同步刷新
      }, sec * 1000);
    }
  }

  /* ---------- 登录 / 退出 ---------- */
  function showLogin() {
    $('loginMask').style.display = 'flex';
    $('adminRoot').style.display = 'none';
    $('loginPass').value = '';
    $('loginErr').textContent = '';
    setTimeout(function () { $('loginPass').focus(); }, 50);
  }

  var loginBusy = false;
  async function doLogin() {
    // 防重入：Enter/表单提交/点击三路并发只放行一路（与主站 doAuth 同款守卫）
    if (loginBusy) return;
    var pw = $('loginPass').value;
    if (!pw || !pw.trim()) { $('loginErr').textContent = '请输入管理密码'; return; }
    loginBusy = true;
    $('loginBtn').disabled = true;
    $('loginErr').textContent = '';
    try {
      var r = await request('POST', '/api/admin/login', { password: pw.trim() }, { skipAuthRedirect: true });
      if (r && r.token) {
        setToken(r.token);
        toast('登录成功', 'ok');
        enterAdmin();
      } else {
        $('loginErr').textContent = (r && r.message) || '登录失败';
      }
    } catch (e) {
      // 403 密码错误：后端错误体 {ok:false,message}
      $('loginErr').textContent = e.message || '登录失败';
    }
    loginBusy = false;
    $('loginBtn').disabled = false;
  }

  function enterAdmin() {
    $('loginMask').style.display = 'none';
    $('adminRoot').style.display = 'block';
    loadOverview();
    loadHealth(); // 第十五批·轮4：顶栏健康徽章（redis/mysql）
    loadTrend();
    activateTab(state.currentTab || 'users');
  }

  function logout() {
    setToken('');
    toast('已退出', 'ok');
    showLogin();
  }

  /* ---------- 概览 ---------- */
  async function loadOverview() {
    try {
      var r = await request('GET', '/api/admin/overview');
      $('ovUsers').textContent = (r && r.users != null) ? r.users : '-';
      $('ovSaves').textContent = (r && r.saves != null) ? r.saves : '-';
      $('ovGraves').textContent = (r && r.graves != null) ? r.graves : '-';
      // 今日运营行(后端每小时聚合的当日增量)
      var td = document.getElementById('ovToday');
      if (td) {
        if (r && r.today) {
          td.style.display = '';
          td.innerHTML = '今日 · 出拳 <b>' + esc(r.today.punches) + '</b> · 击杀 <b>' + esc(r.today.kills)
            + '</b> · 死亡 <b>' + r.today.deaths + '</b> · 转生 <b>' + r.today.rebirths
            + '</b> · 活跃档 <b>' + r.today.activeSaves + '</b>';
        } else {
          td.style.display = 'none';
        }
      }
    } catch (e) {
      if (e.status !== 401 && e.status !== 403) toast('概览加载失败：' + e.message, 'err');
    }
  }

  /* ============================================================
   * 第十五批·轮4：服务健康徽章
   * 数据源 GET /api/health（未鉴权、服务端 2 秒短缓存）→ {ok,redis,mysql,...}
   * redis/mysql 值为 "up"/"down"：up → 绿 #4caf50 圆点，down → 红 #f44336 圆点 + 文字
   * ============================================================ */
  function healthDotHtml(up) {
    return '<span class="health-dot ' + (up ? 'up' : 'down') + '"></span>';
  }

  function renderHealth(r) {
    var el = $('healthBadge');
    if (!el) return;
    if (!r) {
      el.innerHTML = '<span class="health-dot down"></span>健康状态未知';
      el.title = '健康接口不可用';
      return;
    }
    var redisUp = String(r.redis || '').toLowerCase() === 'up';
    var mysqlUp = String(r.mysql || '').toLowerCase() === 'up';
    el.innerHTML = healthDotHtml(redisUp) + 'Redis ' + (redisUp ? '正常' : '异常') +
      ' · ' + healthDotHtml(mysqlUp) + 'MySQL ' + (mysqlUp ? '正常' : '异常');
    el.title = '服务健康状态：redis=' + (r.redis || '-') + ' · mysql=' + (r.mysql || '-');
  }

  async function loadHealth() {
    try {
      var r = await request('GET', '/api/health');
      renderHealth(r);
    } catch (e) {
      renderHealth(null);
    }
  }

  /* ---------- 近 30 天运营趋势（纯 CSS 柱状图，无图表库） ----------
   * 数据源 GET /api/admin/stats → {rows:[{day,punches,kills,deaths,rebirths,activeSaves}]}
   * punches/kills 是字符串形式的增量（可能 "0"），deaths/rebirths/activeSaves 是数字；
   * rows 只含有记录的日期 → 前端按本地日期补零凑满连续 30 天窗口。
   * ============================================================ */
  var TREND_METRICS = {
    punches: '出拳', kills: '击杀', deaths: '死亡', rebirths: '转生', activeSaves: '活跃档'
  };
  var TREND_DAYS = 30;

  // 字符串/数字统一转非负数值；NaN、负数一律按 0 处理
  function toNum(v) {
    var n = parseFloat(v);
    return (isFinite(n) && n > 0) ? n : 0;
  }

  function dayKeyOf(d) {
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  }

  // 行 day 归一化为 YYYY-MM-DD（兼容 ISO 字符串、带时间部分、[y,m,d] 数组）
  function normDayKey(v) {
    if (v === null || v === undefined) return '';
    if (Array.isArray(v) && v.length >= 3) {
      return v[0] + '-' + pad(v[1]) + '-' + pad(v[2]);
    }
    var m = String(v).match(/^(\d{4})-(\d{1,2})-(\d{1,2})/);
    if (m) { return m[1] + '-' + pad(Number(m[2])) + '-' + pad(Number(m[3])); }
    return '';
  }

  function fmtInt(n) {
    try { return n.toLocaleString('zh-CN'); } catch (e) { return String(n); }
  }

  function showTrendMsg(text, isErr) {
    var chart = $('trendChart');
    var empty = $('trendEmpty');
    var stat = $('trendStat');
    if (chart) chart.style.display = 'none';
    if (empty) {
      empty.style.display = 'flex';
      empty.textContent = text;
      empty.className = 'trend-empty' + (isErr ? ' err' : '');
    }
    if (stat) stat.textContent = '';
  }

  async function loadTrend() {
    state.trendRows = null;
    showTrendMsg('加载中…');
    try {
      var r = await request('GET', '/api/admin/stats');
      state.trendRows = (r && r.rows) || [];
    } catch (e) {
      if (e.status !== 401 && e.status !== 403) {
        showTrendMsg('趋势加载失败：' + e.message, true);
      }
      return;
    }
    renderTrend();
  }

  // 按客户端本地日期补零，凑满「今天往前 30 天」的连续窗口
  function buildTrendWindow(rows) {
    var byDay = {};
    for (var i = 0; i < rows.length; i++) {
      var k = normDayKey(rows[i].day);
      if (k) { byDay[k] = rows[i]; }
    }
    var days = [];
    var today = new Date();
    for (var j = TREND_DAYS - 1; j >= 0; j--) {
      var d = new Date(today.getFullYear(), today.getMonth(), today.getDate() - j);
      var key = dayKeyOf(d);
      var row = byDay[key];
      var vals = {};
      Object.keys(TREND_METRICS).forEach(function (mk) {
        vals[mk] = row ? toNum(row[mk]) : 0;
      });
      days.push({ key: key, label: key.slice(5), vals: vals }); // label = MM-DD
    }
    return days;
  }

  function renderTrend() {
    if (state.trendRows === null) return; // 尚未加载完成
    var days = buildTrendWindow(state.trendRows);
    var metric = TREND_METRICS[state.trendMetric] ? state.trendMetric : 'punches';
    var label = TREND_METRICS[metric];

    // 底部日期范围（空数据也标注窗口）
    $('trendRange').textContent = days[0].label + ' ~ ' + days[days.length - 1].label + ' · 近 ' + TREND_DAYS + ' 天';

    var max = 0, sum = 0;
    for (var i = 0; i < days.length; i++) {
      var v = days[i].vals[metric];
      if (v > max) { max = v; }
      sum += v;
    }

    if (max <= 0) { // 该指标近 30 天全 0（含后端无任何行）→ 暂无数据
      showTrendMsg('暂无数据');
      return;
    }

    var chart = $('trendChart');
    var empty = $('trendEmpty');
    if (empty) empty.style.display = 'none';
    if (!chart) return;
    chart.style.display = 'flex';

    var html = '';
    for (var n = 0; n < days.length; n++) {
      var val = days[n].vals[metric];
      var isTick = (n % 5 === 0) || n === days.length - 1; // 稀疏日期刻度，避免挤成一团
      var tip = days[n].label + ' · ' + label + ' ' + fmtInt(val);
      html += '<div class="trend-col' + (n === days.length - 1 ? ' today' : '') + '" title="' + esc(tip) + '">' +
        '<div class="trend-slot">' +
        (val > 0
          ? '<div class="trend-bar" data-v="' + esc(tip) + '" style="height:' + Math.max(2, Math.round(val / max * 100)) + '%"></div>'
          : '<div class="trend-bar zero" data-v="' + esc(tip) + '"></div>') +
        '</div>' +
        '<div class="trend-day">' + (isTick ? days[n].label : '&nbsp;') + '</div>' +
        '</div>';
    }
    chart.innerHTML = html;

    var stat = $('trendStat');
    if (stat) { stat.innerHTML = '峰值 <b>' + fmtInt(max) + '</b> · 合计 <b>' + fmtInt(sum) + '</b>'; }
  }

  function onTrendTabClick(e) {
    var btn = e.target.closest('.trend-tab');
    if (!btn) return;
    var metric = btn.getAttribute('data-metric');
    if (!TREND_METRICS[metric]) return;
    state.trendMetric = metric;
    var tabs = document.querySelectorAll('.trend-tab');
    for (var i = 0; i < tabs.length; i++) {
      tabs[i].classList.toggle('active', tabs[i] === btn);
    }
    renderTrend(); // 切指标只重绘，不重拉
  }

  /* ---------- 导航 ---------- */
  function activateTab(tab) {
    state.currentTab = tab;
    var btns = document.querySelectorAll('.nav-btn');
    for (var i = 0; i < btns.length; i++) {
      var isCur = btns[i].getAttribute('data-tab') === tab;
      btns[i].classList.toggle('active', isCur);
      // 第十五批·轮1：当前导航项挂 aria-current="page"（点击/切换视图共用此入口，高亮 + 无障碍双保险）
      if (isCur) { btns[i].setAttribute('aria-current', 'page'); }
      else { btns[i].removeAttribute('aria-current'); }
    }
    var pages = document.querySelectorAll('.adm-page');
    for (var j = 0; j < pages.length; j++) {
      pages[j].classList.toggle('active', pages[j].id === 'page-' + tab);
    }
    loadTab(tab); // 每次切换都重拉，保证数据新鲜
  }

  function loadTab(tab) {
    if (tab === 'users') { loadUsers(); }
    else if (tab === 'audit') { loadAudit(); }
    else if (CFG[tab]) { loadConfig(tab); }
  }

  /* ============================================================
   * 第十五批·轮2：数据表格列头点击排序（asc/desc 切换 + ▲▼ 箭头指示）
   * 覆盖账号表 / 六个配置表 / 审计表；排序状态存 sortState，刷新后保持
   * ============================================================ */
  var sortState = {}; // tableId → { key, dir }

  function cmpRows(a, b, key) {
    var va = a ? a[key] : undefined, vb = b ? b[key] : undefined;
    var sa = (va === null || va === undefined) ? '' : String(va);
    var sb = (vb === null || vb === undefined) ? '' : String(vb);
    var na = Number(sa), nb = Number(sb);
    // 两边都是纯数字 → 数值比较；否则字符串比较（时间列 YYYY-MM-DD 文本天然有序）
    if (sa !== '' && sb !== '' && isFinite(na) && isFinite(nb)) { return na - nb; }
    if (sa === '' && sb !== '') { return 1; }  // 空值沉底
    if (sb === '' && sa !== '') { return -1; }
    return sa.localeCompare(sb, 'zh-CN');
  }

  function applyTableSort(tableId, rows) {
    var st = sortState[tableId];
    if (!st || !st.key || !rows) return rows || [];
    var arr = rows.slice();
    arr.sort(function (a, b) {
      var c = cmpRows(a, b, st.key);
      return st.dir === 'desc' ? -c : c;
    });
    return arr;
  }

  // 依据 sortState 重绘列头箭头（▲ 升序 / ▼ 降序），data-label 为箭头重绘基准
  function updateSortArrows(tableEl) {
    if (!tableEl) return;
    var st = sortState[tableEl.id];
    var ths = tableEl.querySelectorAll('th[data-sort]');
    for (var i = 0; i < ths.length; i++) {
      var base = ths[i].getAttribute('data-label') || ths[i].textContent.replace(/\s*[▲▼]\s*$/, '');
      ths[i].textContent = base + (st && ths[i].getAttribute('data-sort') === st.key
        ? (st.dir === 'asc' ? ' ▲' : ' ▼') : '');
    }
  }

  // 点击列头后按当前缓存行重排重绘
  function rerenderSortedTable(tableId) {
    if (tableId === 'userTable') {
      renderUsersRows(applyTableSort('userTable', userRowsCache));
    } else if (tableId === 'auditTable') {
      refreshAuditView();
    } else if (tableId.indexOf('tbl-') === 0) {
      var tab = tableId.slice(4);
      var cache = rowCache[tab] || {};
      var rows = [];
      Object.keys(cache).forEach(function (k) { rows.push(cache[k]); });
      renderConfigTable(tab, applyTableSort(tableId, rows));
    }
  }

  function onTableSortClick(e) {
    var th = e.target.closest('th[data-sort]');
    if (!th) return;
    var table = th.closest('table');
    if (!table || !table.id) return;
    var key = th.getAttribute('data-sort');
    var st = sortState[table.id] || { key: null, dir: 'asc' };
    st.dir = (st.key === key && st.dir === 'asc') ? 'desc' : 'asc'; // 同列再点翻转，异列重置升序
    st.key = key;
    sortState[table.id] = st;
    rerenderSortedTable(table.id);
  }

  /* ---------- 账号页 ---------- */
  // 第十五批·轮2：列头挂 data-sort（可排序）/ data-label（箭头重绘基准）；PvP 与操作为复合列不排序
  var USER_HEAD = '<thead><tr>' +
    '<th data-sort="id" data-label="ID">ID</th>' +
    '<th data-sort="username" data-label="用户名">用户名</th>' +
    '<th data-sort="createdAt" data-label="注册时间">注册时间</th>' +
    '<th data-sort="lastLoginAt" data-label="最近登录">最近登录</th>' +
    '<th data-sort="incense" data-label="香火">香火</th>' +
    '<th data-label="PvP 胜/负">PvP 胜/负</th>' +
    '<th data-label="操作">操作</th>' +
    '</tr></thead>';
  var userRowsCache = []; // 第十五批·轮2：账号原始行缓存（列头排序重排用）

  function renderUsersRows(rows) {
    var t = $('userTable');
    userRowsCache = rows || []; // 第十五批·轮2：缓存原始行，供列头点击排序重排
    var sorted = applyTableSort('userTable', userRowsCache);
    if (!sorted.length) {
      t.innerHTML = USER_HEAD + '<tbody><tr class="empty-row"><td colspan="7">无匹配账号</td></tr></tbody>';
      updateSortArrows(t); // 第十五批·轮2：空表也保持箭头指示
      return;
    }
    var html = USER_HEAD + '<tbody>';
    for (var i = 0; i < sorted.length; i++) {
      var u = sorted[i];
      html += '<tr>' +
        '<td class="num">' + esc(u.id) + '</td>' +
        '<td>' + esc(u.username) + '</td>' +
        '<td class="dim">' + fmtTime(u.createdAt) + '</td>' +
        '<td class="dim">' + fmtTime(u.lastLoginAt) + '</td>' +
        '<td class="num">' + (u.incense != null ? esc(u.incense) : '-') + '</td>' +
        '<td class="num">' + (u.pvpWins != null ? esc(u.pvpWins) : '0') + ' / ' + (u.pvpLosses != null ? esc(u.pvpLosses) : '0') + '</td>' +
        '<td class="op-btns">' +
          '<button class="adm-btn mini" data-act="resetpw" data-id="' + esc(u.id) + '" data-name="' + esc(u.username) + '">重置密码</button>' +
          '<button class="adm-btn mini danger" data-act="deluser" data-id="' + esc(u.id) + '" data-name="' + esc(u.username) + '">删除</button>' +
        '</td></tr>';
    }
    t.innerHTML = html + '</tbody>';
    updateSortArrows(t); // 第十五批·轮2：每次重绘同步箭头指示
  }

  async function loadUsers(kw) {
    var t = $('userTable');
    t.innerHTML = '<tbody><tr class="empty-row"><td>加载中…</td></tr></tbody>';
    try {
      var r = await request('GET', '/api/admin/users' + (kw ? ('?kw=' + encodeURIComponent(kw)) : ''));
      renderUsersRows(r && r.rows);
    } catch (e) {
      if (e.status !== 401 && e.status !== 403) {
        t.innerHTML = '<tbody><tr class="empty-row"><td>' + esc(e.message) + '</td></tr></tbody>';
      }
    }
  }

  function onUserTableClick(e) {
    var btn = e.target.closest('button');
    if (!btn) return;
    var act = btn.getAttribute('data-act');
    var id = btn.getAttribute('data-id');
    var name = btn.getAttribute('data-name');
    if (act === 'resetpw') { resetUserPw(id, name); }
    else if (act === 'deluser') { deleteUser(id, name); }
  }

  function resetUserPw(id, name) {
    var pw = prompt('为用户「' + name + '」输入新密码（至少 6 位）：');
    if (pw === null) return;
    pw = pw.trim();
    if (pw.length < 6) { alert('密码至少 6 位'); return; }
    request('POST', '/api/admin/users/' + encodeURIComponent(id) + '/reset-pw', { password: pw })
      .then(function () { toast('已重置 ' + name + ' 的密码', 'ok'); })
      .catch(function (e) { toast('重置失败：' + e.message, 'err'); });
  }

  function deleteUser(id, name) {
    if (!confirm('确认删除用户「' + name + '」？该操作不可恢复。')) return;
    request('DELETE', '/api/admin/users/' + encodeURIComponent(id))
      .then(function () {
        toast('已删除用户 ' + name, 'ok');
        loadUsers($('userKw').value.trim());
        loadOverview();
      })
      .catch(function (e) { toast('删除失败：' + e.message, 'err'); });
  }

  /* ============================================================
   * 六个配置页：列配置表驱动
   * 字段类型：text / number / select / json / checkbox
   * ============================================================ */
  var SLOT_OPTIONS = ['weapon', 'offhand', 'bow', 'head', 'chest', 'legs', 'boots', 'ring1', 'ring2', 'necklace'];   // 与 GameConst.SLOT 十部位对齐
  var TIER_OPTIONS = [1, 2, 3, 4, 5, 6, 7, 8];

  function onOffFmt(v) {
    var on = v === true || v === 1 || v === '1';
    return '<span class="off-on ' + (on ? 'on">✔ 启用' : 'off">✘ 停用') + '</span>';
  }

  const CFG = {
    equipment: {
      title: '⚔️ 装备配置', api: 'equipment',
      fields: [
        { key: 'code', label: 'code（编码）', type: 'text', required: true, mono: true },
        { key: 'name', label: 'name（名称）', type: 'text', required: true },
        { key: 'emoji', label: 'emoji', type: 'text' },
        { key: 'slot', label: 'slot（部位）', type: 'select', options: SLOT_OPTIONS },
        { key: 'tier', label: 'tier（品阶）', type: 'number' },
        { key: 'minZone', label: 'minZone（最低区域）', type: 'number' },
        { key: 'baseStats', label: 'baseStats（JSON）', type: 'json' },
        { key: 'enhanceMax', label: 'enhanceMax（强化上限）', type: 'number' },
        { key: 'sellBase', label: 'sellBase（基础售价）', type: 'number' },
        { key: 'remark', label: 'remark（备注）', type: 'text', colCls: 'dim' },
        { key: 'enabled', label: 'enabled', type: 'checkbox', fmt: onOffFmt },
        { key: 'sortNo', label: 'sortNo', type: 'number', num: true }
      ]
    },
    affix: {
      title: '✨ 词条配置', api: 'affix',
      fields: [
        { key: 'code', label: 'code', type: 'text', required: true, mono: true },
        { key: 'name', label: 'name', type: 'text', required: true },
        { key: 'statKey', label: 'statKey（属性键）', type: 'text', mono: true },
        { key: 'statType', label: 'statType', type: 'select', options: ['flat', 'pct'] },
        { key: 'minVal', label: 'minVal', type: 'number', num: true },
        { key: 'maxVal', label: 'maxVal', type: 'number', num: true },
        { key: 'rarity', label: 'rarity（稀有度）', type: 'number', num: true },
        { key: 'sideKey', label: 'sideKey（副词条键）', type: 'text', mono: true },
        { key: 'sideVal', label: 'sideVal（副词条值）', type: 'text' },
        { key: 'enabled', label: 'enabled', type: 'checkbox', fmt: onOffFmt },
        { key: 'sortNo', label: 'sortNo', type: 'number', num: true }
      ]
    },
    item: {
      title: '🛒 商品配置', api: 'item',
      fields: [
        { key: 'code', label: 'code', type: 'text', required: true, mono: true },
        { key: 'name', label: 'name', type: 'text', required: true },
        { key: 'emoji', label: 'emoji', type: 'text' },
        { key: 'kind', label: 'kind（类别）', type: 'text' },
        { key: 'price', label: 'price（价格）', type: 'number', num: true },
        { key: 'effectJson', label: 'effectJson（JSON）', type: 'json' },
        { key: 'desc', label: 'desc（描述）', type: 'text', colCls: 'dim' },
        { key: 'enabled', label: 'enabled', type: 'checkbox', fmt: onOffFmt },
        { key: 'sortNo', label: 'sortNo', type: 'number', num: true }
      ]
    },
    skill: {
      title: '📖 技能配置', api: 'skill',
      fields: [
        { key: 'code', label: 'code', type: 'text', required: true, mono: true },
        { key: 'name', label: 'name', type: 'text', required: true },
        { key: 'emoji', label: 'emoji', type: 'text' },
        { key: 'kind', label: 'kind（类别）', type: 'text' },
        { key: 'price', label: 'price（价格）', type: 'number', num: true },
        { key: 'mp', label: 'mp（耗蓝）', type: 'number', num: true },
        { key: 'cd', label: 'cd（冷却秒）', type: 'number', num: true },
        { key: 'dur', label: 'dur（持续秒）', type: 'number', num: true },
        { key: 'effectJson', label: 'effectJson（JSON）', type: 'json' },
        { key: 'desc', label: 'desc（描述）', type: 'text', colCls: 'dim' },
        { key: 'enabled', label: 'enabled', type: 'checkbox', fmt: onOffFmt },
        { key: 'sortNo', label: 'sortNo', type: 'number', num: true }
      ]
    },
    buff: {
      title: '🌀 状态配置', api: 'buff',
      fields: [
        { key: 'code', label: 'code', type: 'text', required: true, mono: true },
        { key: 'name', label: 'name', type: 'text', required: true },
        { key: 'emoji', label: 'emoji', type: 'text' },
        { key: 'nature', label: 'nature（性质）', type: 'select', options: ['buff', 'debuff'] },
        { key: 'groupType', label: 'groupType（分组）', type: 'text' },
        { key: 'effectJson', label: 'effectJson（JSON）', type: 'json' },
        { key: 'desc', label: 'desc（描述）', type: 'text', colCls: 'dim' },
        { key: 'enabled', label: 'enabled', type: 'checkbox', fmt: onOffFmt },
        { key: 'sortNo', label: 'sortNo', type: 'number', num: true }
      ]
    },
    droprate: {
      title: '🎲 爆率配置', api: 'droprate', inline: true,
      fields: [
        { key: 'zone', label: 'zone（区域）', type: 'number', required: true, num: true },
        { key: 'rate', label: 'rate（0~1）', type: 'number', step: '0.01', min: '0', max: '1', num: true },
        { key: 'batchCap', label: 'batchCap（批量上限）', type: 'number', num: true },
        { key: 'enabled', label: 'enabled', type: 'checkbox', fmt: onOffFmt },
        { key: 'sortNo', label: 'sortNo', type: 'number', num: true }
      ]
    }
  };

  /* ---------- 动态生成六个配置页 DOM（插到审计页之前） ---------- */
  function buildConfigPages() {
    var main = document.querySelector('.adm-main');
    var auditPage = $('page-audit');
    Object.keys(CFG).forEach(function (tab) {
      var c = CFG[tab];
      var sec = document.createElement('section');
      sec.className = 'adm-page';
      sec.id = 'page-' + tab;
      sec.innerHTML =
        '<div class="page-head"><h2>' + esc(c.title) + '</h2><div class="page-tools">' +
        '<button class="adm-btn small gold" data-act="add" data-table="' + esc(tab) + '">＋ 新增</button>' +
        '<button class="adm-btn small" data-act="reload" data-table="' + esc(tab) + '">刷新</button>' +
        '</div></div>' +
        '<div class="table-wrap"><table class="adm-table" id="tbl-' + esc(tab) + '"></table></div>';
      main.insertBefore(sec, auditPage);
    });
  }

  /* ---------- 通用渲染：列配置表驱动 ---------- */
  function cellHtml(v, f) {
    if (f.fmt) { return f.fmt(v); }
    if (v === null || v === undefined || v === '') { return '<span class="dim">-</span>'; }
    if (f.type === 'json') {
      // JSON 字符串：截断展示 + title 悬浮全文
      var s = String(v);
      var short = s.length > 60 ? s.slice(0, 60) + '…' : s;
      return '<span class="json-cell" title="' + esc(s) + '">' + esc(short) + '</span>';
    }
    var cls = f.colCls || (f.num ? 'num' : (f.mono ? 'mono' : ''));
    var text = esc(v);
    return cls ? '<span class="' + cls + '">' + text + '</span>' : text;
  }

  function renderConfigTable(tab, rows) {
    var c = CFG[tab];
    var t = $('tbl-' + tab);
    // 第十五批·轮2：列头挂 data-sort / data-label 支持点击排序（id 与各字段列；操作列不排序）
    var head = '<thead><tr><th data-sort="id" data-label="ID">ID</th>';
    for (var i = 0; i < c.fields.length; i++) {
      head += '<th data-sort="' + esc(c.fields[i].key) + '" data-label="' + esc(c.fields[i].key) + '">' + esc(c.fields[i].key) + '</th>';
    }
    head += '<th data-label="操作">操作</th></tr></thead>';

    if (!rows || !rows.length) {
      t.innerHTML = head + '<tbody><tr class="empty-row"><td colspan="' + (c.fields.length + 2) + '">暂无数据</td></tr></tbody>';
      updateSortArrows(t);
      return;
    }
    var body = '<tbody>';
    for (var r = 0; r < rows.length; r++) {
      var row = rows[r];
      body += '<tr><td class="num">' + esc(row.id) + '</td>';
      for (var k = 0; k < c.fields.length; k++) {
        var f = c.fields[k];
        body += '<td>' + cellHtml(row[f.key], f) + '</td>';
      }
      body += '<td class="op-btns">' +
        '<button class="adm-btn mini" data-act="edit" data-tab="' + esc(tab) + '" data-id="' + esc(row.id) + '">编辑</button>' +
        '<button class="adm-btn mini danger" data-act="del" data-tab="' + esc(tab) + '" data-id="' + esc(row.id) + '">删除</button>' +
        '</td></tr>';
    }
    t.innerHTML = head + body + '</tbody>';
    updateSortArrows(t); // 第十五批·轮2：重绘同步箭头
  }

  // tab → 当前行缓存（供编辑回填 + 第十五批·轮2 列头排序重排）
  var rowCache = {};

  async function loadConfig(tab) {
    // 第十五批·轮2：请求期间保留列头（含箭头），避免加载闪空
    var tbl = $('tbl-' + tab);
    if (tbl && !tbl.querySelector('thead')) {
      tbl.innerHTML = '<tbody><tr class="empty-row"><td>加载中…</td></tr></tbody>';
    }
    var t = $('tbl-' + tab);
    try {
      var r = await request('GET', '/api/admin/' + tab);
      var rows = (r && r.rows) || [];
      rowCache[tab] = {};
      for (var i = 0; i < rows.length; i++) { rowCache[tab][String(rows[i].id)] = rows[i]; }
      renderConfigTable(tab, rows);
    } catch (e) {
      if (e.status !== 401 && e.status !== 403) {
        t.innerHTML = '<tbody><tr class="empty-row"><td>' + esc(e.message) + '</td></tr></tbody>';
      }
    }
  }

  /* ---------- 实体表单（新增 / 编辑共用弹层） ---------- */
  var formCtx = { tab: null, id: null }; // id=null → 新增

  function buildFormField(f, val) {
    var id = 'ff-' + f.key;
    if (f.type === 'checkbox') {
      var chk = (val === true || val === 1 || val === '1') ? ' checked' : '';
      return '<div class="form-row"><div class="chk-row"><input type="checkbox" id="' + id + '"' + chk + '>' +
        '<label class="chk-label" for="' + id + '">' + esc(f.label) + '</label></div></div>';
    }
    if (f.type === 'json') {
      return '<div class="form-row"><label for="' + id + '">' + esc(f.label) + (f.required ? ' *' : '') + '</label>' +
        '<textarea id="' + id + '" class="adm-input" placeholder="{ }" spellcheck="false">' + esc(val || '') + '</textarea></div>';
    }
    if (f.type === 'select') {
      var cur = (val === null || val === undefined) ? '' : String(val);
      var curFound = false;
      var opts = '';
      for (var i = 0; i < f.options.length; i++) {
        var ov = String(f.options[i]);
        if (ov === cur) { curFound = true; }
        opts += '<option value="' + esc(ov) + '"' + (ov === cur ? ' selected' : '') + '>' + esc(ov) + '</option>';
      }
      // 当前值不在预设选项里：附加一个「原值」项，提交时按原值处理
      if (cur && !curFound) {
        opts += '<option value="' + esc(cur) + '" selected>' + esc(cur) + '（原值）</option>';
      }
      return '<div class="form-row"><label for="' + id + '">' + esc(f.label) + (f.required ? ' *' : '') + '</label>' +
        '<select id="' + id + '" class="adm-input">' + opts + '</select></div>';
    }
    // text / number
    var typ = f.type === 'number' ? 'number' : 'text';
    var extra = '';
    if (f.step !== undefined) extra += ' step="' + esc(f.step) + '"';
    if (f.min !== undefined) extra += ' min="' + esc(f.min) + '"';
    if (f.max !== undefined) extra += ' max="' + esc(f.max) + '"';
    return '<div class="form-row"><label for="' + id + '">' + esc(f.label) + (f.required ? ' *' : '') + '</label>' +
      '<input type="' + typ + '" id="' + id + '" class="adm-input" value="' + esc(val === null || val === undefined ? '' : val) + '"' + extra + '></div>';
  }

  function openForm(tab, id) {
    var c = CFG[tab];
    var row = id != null ? (rowCache[tab] && rowCache[tab][String(id)]) : null;
    formCtx.tab = tab;
    formCtx.id = id != null ? id : null;
    $('formTitle').textContent = (id != null ? '编辑' : '新增') + ' · ' + c.title + (id != null ? '（#' + id + '）' : '');
    var html = '';
    for (var i = 0; i < c.fields.length; i++) {
      var f = c.fields[i];
      var val;
      if (id != null) { val = row ? row[f.key] : ''; }
      else { val = f.type === 'checkbox' ? true : ''; } // 新增默认：启用、其余留空
      html += buildFormField(f, val);
    }
    $('entityForm').innerHTML = html;
    $('formMask').style.display = 'flex';
    var first = $('entityForm').querySelector('input:not([type=checkbox]), select, textarea');
    if (first) { first.focus(); }
  }

  function closeForm() {
    $('formMask').style.display = 'none';
    $('entityForm').innerHTML = '';
    formCtx.tab = null;
    formCtx.id = null;
  }

  async function saveForm() {
    var c = CFG[formCtx.tab];
    if (!c) return;
    var curIdRef = formCtx.id; // 第十五批·轮8：编辑态标记（新增态为 null，跳过 diff）
    var payload = {};
    for (var i = 0; i < c.fields.length; i++) {
      var f = c.fields[i];
      var el = $('ff-' + f.key);
      if (!el) continue;
      if (f.type === 'checkbox') { payload[f.key] = el.checked; continue; }

      // select「原值」占位项：取 option 的 value（即原值本身），无需解析文本
      var v = String(el.value == null ? '' : el.value).trim();
      if (f.required && v === '') { alert('「' + f.key + '」为必填项'); return; }

      if (f.type === 'number') {
        if (v === '') { payload[f.key] = null; continue; }
        var n = Number(v);
        if (!isFinite(n)) { alert('「' + f.key + '」不是合法数字：' + v); return; }
        if (f.min !== undefined && n < Number(f.min)) { alert('「' + f.key + '」不能小于 ' + f.min); return; }
        if (f.max !== undefined && n > Number(f.max)) { alert('「' + f.key + '」不能大于 ' + f.max); return; }
        payload[f.key] = n;
        continue;
      }
      if (f.type === 'json') {
        if (v === '') { payload[f.key] = ''; continue; }
        try { JSON.parse(v); } // 非法 JSON → alert 不提交
        catch (e) { alert('「' + f.key + '」不是合法 JSON：' + e.message); return; }
        payload[f.key] = v;
        continue;
      }
      payload[f.key] = v;
    }

    // 第十五批·轮8：编辑保存前 diff 预览——对比旧值→新值，有变化才 confirm
    if (curIdRef != null) {
      var oldRow = rowCache[formCtx.tab] && rowCache[formCtx.tab][String(formCtx.id)];
      if (oldRow) {
        var lines = [];
        for (var k in payload) {
          if (!Object.prototype.hasOwnProperty.call(payload, k)) continue;
          var ov = oldRow[k] === undefined || oldRow[k] === null ? '' : String(oldRow[k]);
          var nv = payload[k] === undefined || payload[k] === null ? '' : String(payload[k]);
          if (ov !== nv) lines.push(k + '：' + (ov === '' ? '（空）' : ov) + '  →  ' + (nv === '' ? '（空）' : nv));
        }
        if (lines.length === 0) { toast('没有变化，未提交', 'warn'); return; } // 无变化不打接口
        if (!confirm('以下字段将被修改：\n\n' + lines.join('\n') + '\n\n确认保存？')) return;
      }
    }

    $('formSave').disabled = true;
    var curTab = formCtx.tab, curId = formCtx.id;
    try {
      if (curId != null) {
        await request('PUT', '/api/admin/' + curTab + '/' + encodeURIComponent(curId), payload);
        toast('已保存', 'ok');
      } else {
        await request('POST', '/api/admin/' + curTab, payload);
        toast('已新增', 'ok');
      }
      closeForm();
      loadConfig(curTab);
    } catch (e) {
      if (e.status !== 401 && e.status !== 403) toast('保存失败：' + e.message, 'err');
    }
    $('formSave').disabled = false;
  }

  async function deleteEntity(tab, id) {
    if (!confirm('确认删除 #' + id + '？该操作不可恢复。')) return;
    try {
      await request('DELETE', '/api/admin/' + tab + '/' + encodeURIComponent(id));
      toast('已删除 #' + id, 'ok');
      loadConfig(tab);
    } catch (e) {
      if (e.status !== 401 && e.status !== 403) toast('删除失败：' + e.message, 'err');
    }
  }

  /* ---------- 审计页 ---------- */
  // 第十五批·轮2：列头挂 data-sort / data-label（时间/动作可排序，详情列长文本不排序）
  var AUDIT_HEAD = '<thead><tr>' +
    '<th style="width:170px" data-sort="createdAt" data-label="时间">时间</th>' +
    '<th style="width:200px" data-sort="action" data-label="动作">动作</th>' +
    '<th data-label="详情">详情</th>' +
    '</tr></thead>';
  var auditRowsCache = []; // 第十五批·轮2：审计原始行缓存（排序 + 轮3 过滤共用）

  // 第十五批·轮2/轮3：审计视图渲染 = 缓存行 → 级别+关键词过滤 → 排序 → 重绘
  function refreshAuditView() {
    var t = $('auditTable');
    if (!t) return;
    var rows = filterAuditRows(auditRowsCache); // 轮3 组合过滤
    rows = applyTableSort('auditTable', rows);
    if (!rows.length) {
      t.innerHTML = AUDIT_HEAD + '<tbody><tr class="empty-row"><td colspan="3">' +
        (auditRowsCache.length ? '无匹配记录（调整级别/关键词）' : '暂无记录') + '</td></tr></tbody>';
      updateSortArrows(t);
      return;
    }
    var html = AUDIT_HEAD + '<tbody>';
    for (var i = 0; i < rows.length; i++) {
      var a = rows[i];
      html += '<tr>' +
        '<td class="dim">' + fmtTime(a.createdAt) + '</td>' +
        '<td class="mono">' + esc(a.action) + '</td>' +
        '<td class="dim">' + esc(a.detail) + '</td></tr>';
    }
    t.innerHTML = html + '</tbody>';
    updateSortArrows(t);
  }

  // 第十五批·轮3：级别 + 关键词组合过滤（对缓存行执行，输入即刷新视图）
  function filterAuditRows(rows) {
    var levelSel = $('auditLevel');
    var kwInput = $('auditKw');
    var level = levelSel ? levelSel.value : '';
    var kw = kwInput ? kwInput.value.trim().toLowerCase() : '';
    if (!level && !kw) return rows;
    return (rows || []).filter(function (a) {
      if (level && auditRowLevel(a) !== level) return false;
      var hay = (String(a.action || '') + ' ' + String(a.detail || '')).toLowerCase();
      if (kw && hay.indexOf(kw) === -1) return false;
      return true;
    });
  }

  // 第十五批·轮3：从动作/详情推断日志级别（WARN/ERROR/INFO）
  function auditRowLevel(a) {
    var s = (String(a.action || '') + ' ' + String(a.detail || '')).toUpperCase();
    if (s.indexOf('ERROR') >= 0 || s.indexOf('失败') >= 0 || s.indexOf('ERR') >= 0) return 'ERROR';
    if (s.indexOf('WARN') >= 0 || s.indexOf('警告') >= 0) return 'WARN';
    return 'INFO';
  }

  async function loadAudit() {
    var t = $('auditTable');
    // 第十五批·轮2：加载中保留列头（含箭头），避免整表闪空
    if (!t.querySelector('thead')) {
      t.innerHTML = AUDIT_HEAD + '<tbody><tr class="empty-row"><td colspan="3">加载中…</td></tr></tbody>';
    }
    try {
      var r = await request('GET', '/api/admin/audit');
      auditRowsCache = (r && r.rows) || []; // 第十五批·轮2：缓存原始行
      refreshAuditView();
    } catch (e) {
      if (e.status !== 401 && e.status !== 403) {
        t.innerHTML = '<tbody><tr class="empty-row"><td>' + esc(e.message) + '</td></tr></tbody>';
      }
    }
  }

  /* ---------- 刷新配置 ---------- */
  // 第十五批·轮5：重载配置/清缓存类危险操作统一确认（立即生效，误触不可撤销）
  function confirmDanger() {
    return confirm('⚠️ 该操作立即生效，确定继续？');
  }

  async function reloadServerConfig() {
    if (!confirmDanger()) return; // 第十五批·轮5：重载服务端配置属危险操作，先确认
    var btn = $('btnReloadCfg');
    btn.disabled = true;
    try {
      await request('POST', '/api/admin/config/reload');
      toast('服务端配置已重载', 'ok');
      loadTab(state.currentTab);
      loadOverview();
    } catch (e) {
      if (e.status !== 401 && e.status !== 403) toast('重载失败：' + e.message, 'err');
    }
    btn.disabled = false;
  }

  /* ---------- 事件绑定 ---------- */
  function onNavClick(e) {
    var btn = e.target.closest('.nav-btn');
    if (!btn) return;
    activateTab(btn.getAttribute('data-tab'));
  }

  function onMainClick(e) {
    var btn = e.target.closest('button[data-act]');
    if (!btn) return;
    var act = btn.getAttribute('data-act');
    var tab = btn.getAttribute('data-tab') || btn.getAttribute('data-table');
    var id = btn.getAttribute('data-id');
    if (act === 'add') { openForm(tab, null); }
    else if (act === 'reload') { loadConfig(tab); }
    else if (act === 'edit') { openForm(tab, id); }
    else if (act === 'del') { deleteEntity(tab, id); }
  }

  function bind() {
    // 登录
    $('loginBtn').addEventListener('click', doLogin);
    // 表单化登录（R3）：原生 Enter 提交 + 密码管理器语义，内部防重入
    var loginForm = $('loginForm');
    if (loginForm) loginForm.addEventListener('submit', function (e) { e.preventDefault(); doLogin(); });
    $('loginPass').addEventListener('keydown', function (e) {
      if (e.key === 'Enter') { doLogin(); }
    });
    // 顶栏
    $('btnLogout').addEventListener('click', logout);
    $('btnReloadCfg').addEventListener('click', reloadServerConfig);
    $('btnDensity').addEventListener('click', toggleDensity); // 第十五批·轮6：密度切换
    $('refreshSel').addEventListener('change', function () { // 第十五批·轮7：自动刷新间隔切换
      applyRefreshInterval(Number(this.value) || 0);
    });
    // 导航
    document.querySelector('.adm-nav').addEventListener('click', onNavClick);
    // 主区：账号表与配置页按钮统一事件代理
    document.querySelector('.adm-main').addEventListener('click', function (e) {
      // 第十五批·轮2 收尾：列头排序代理先于按钮代理（th 点击优先于按钮点击，命中即终止）
      onTableSortClick(e);
      if (e.target.closest('#userTable')) { onUserTableClick(e); return; }
      onMainClick(e);
    });
    // 账号搜索（kw 回车 / 点按钮均可）
    $('userSearchBtn').addEventListener('click', function () { loadUsers($('userKw').value.trim()); });
    $('userKw').addEventListener('keydown', function (e) {
      if (e.key === 'Enter') { loadUsers(this.value.trim()); }
    });
    // 审计刷新
    $('auditRefresh').addEventListener('click', loadAudit);
    // 第十五批·轮3 收尾：级别下拉 + 关键词输入 → 只重绘过滤视图（对缓存行执行，不重拉）
    $('auditLevel').addEventListener('change', refreshAuditView);
    $('auditKw').addEventListener('input', refreshAuditView);
    // 趋势卡：指标切换 + 手动刷新
    $('trendTabs').addEventListener('click', onTrendTabClick);
    $('trendRefresh').addEventListener('click', loadTrend);
    // 实体表单
    $('formCancel').addEventListener('click', closeForm);
    $('formSave').addEventListener('click', saveForm);
    $('formMask').addEventListener('click', function (e) {
      if (e.target === this) { closeForm(); } // 点遮罩空白关闭
    });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && $('formMask').style.display === 'flex') { closeForm(); }
    });
  }

  /* ---------- 启动 ---------- */
  function init() {
    buildConfigPages();
    bind();
    applyDensity(lsGet(DENSE_KEY) === '1'); // 第十五批·轮6：恢复密度记忆
    applyRefreshInterval(Number(lsGet(REFRESH_KEY)) || 0); // 第十五批·轮7：恢复自动刷新间隔
    if (state.token) {
      enterAdmin(); // 已有 token 直接进；首个请求 401/403 会清 token 回登录页
    } else {
      showLogin();
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})(window);
