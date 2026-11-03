/* ============================================================
 * 普攻永久增加生命上限 v2.0 · app.js
 * 前端：只做渲染与意图提交，所有数值以服务端返回为准
 * ============================================================ */
(function () {
  'use strict';

  const API = window.GP_API;
  const $ = (id) => document.getElementById(id);

  // ---------------- 大数格式化 ----------------
  // 与后端 GameConst.ETERNAL_BUFFS 对齐:永续天赋下这些增益永不消逝
  const ETERNAL_KEYS = ['burn', 'block', 'rapid', 'surge', 'regen_food', 'spd_pot', 'growth_pot', 'iron_pot'];

  const CN_UNITS = [
    ['万', 1e4, 1e8], ['亿', 1e8, 1e12], ['兆', 1e12, 1e16], ['京', 1e16, 1e20],
    ['垓', 1e20, 1e24], ['秭', 1e24, 1e28], ['穰', 1e28, 1e32], ['沟', 1e32, 1e36],
    ['涧', 1e36, 1e40], ['正', 1e40, 1e44], ['载', 1e44, Infinity]
  ];

  function fmt(v) {
    if (v === null || v === undefined) return '0';
    if (v === 'Infinity' || v === '-Infinity' || v === Infinity || v === -Infinity) return '∞';
    let d;
    try { d = new Decimal(v); } catch (e) { d = new Decimal(Number(v) || 0); }
    if (d.lt(10000)) return Math.floor(d.toNumber()).toLocaleString('zh-CN');
    const mant = d.mantissa, exp = d.exponent; // d = mantissa × 10^exponent
    for (let i = 0; i < CN_UNITS.length; i++) {
      const u = CN_UNITS[i];
      const lo = Math.log10(u[1]);
      if (exp >= lo && exp < Math.log10(u[2])) {
        const scaled = d.div(u[1]);
        const n = scaled.toNumber();
        if (!Number.isFinite(n)) {
          // 量级超 double(≥1e308)时 toNumber 溢出为 Infinity,会显示成"Infinity载"——
          // 用尾数+指数科学计数保住量级(如 1.00e264载)
          return scaled.mantissa.toFixed(2) + 'e' + scaled.exponent + u[0];
        }
        return n.toFixed(2) + u[0];
      }
    }
    return d.toExponential(2);
  }

  function fmtPct(v) { return (Number(v) * 100).toFixed(1) + '%'; }

  // ---------------- 金色雨（天赋天选仪式，复刻旧版） ----------------
  function goldRain(container, n) {
    const glyphs = ['✨', '⭐', '🌟', '💫', '🟡', '💰'];
    let html = '';
    for (let i = 0; i < n; i++) {
      const g = glyphs[Math.floor(Math.random() * glyphs.length)];
      const left = Math.random() * 100;
      const dur = 1.8 + Math.random() * 2.4;
      const delay = Math.random() * 2.5;
      const size = 12 + Math.random() * 18;
      html += '<span class="grain" style="left:' + left.toFixed(1) + '%;animation-duration:' + dur.toFixed(2) + 's;animation-delay:' + delay.toFixed(2) + 's;font-size:' + size.toFixed(0) + 'px">' + g + '</span>';
    }
    container.innerHTML = html;
  }

  // ---------------- 战斗飘字（预算节流防爆屏） ----------------
  let _floatLayer = null, _floatBudget = 0, _floatLast = 0;
  function floatDmg(text, cls) {
    const now = performance.now();
    if (now - _floatLast > 120) { _floatBudget = 4; _floatLast = now; }
    if (_floatBudget <= 0) return;
    _floatBudget--;
    if (!_floatLayer) {
      _floatLayer = document.createElement('div');
      _floatLayer.id = 'floatLayer';
      _floatLayer.style.cssText = 'position:fixed;inset:0;pointer-events:none;z-index:9000;overflow:hidden;';
      document.body.appendChild(_floatLayer);
    }
    const mob = document.getElementById('mobEmoji');
    const r = mob ? mob.getBoundingClientRect() : { left: innerWidth / 2, top: innerHeight / 2, width: 0 };
    const el = document.createElement('span');
    el.className = 'float-dmg ' + (cls || '');
    el.textContent = text;
    const jitter = (Math.random() - 0.5) * 40;
    el.style.left = (r.left + r.width / 2 + jitter) + 'px';
    el.style.top = (r.top - 6) + 'px';
    _floatLayer.appendChild(el);
    setTimeout(() => el.remove(), 750);
  }

  // HTML 转义：所有用户输入（用户名/墓志铭/装备名之外的外部数据）进 innerHTML 前必须过这里
  function esc(x) {
    return String(x == null ? '' : x)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function fmtDur(sec) {
    sec = Math.max(0, Math.floor(sec));
    const d = Math.floor(sec / 86400);
    const h = Math.floor((sec % 86400) / 3600);
    const m = Math.floor((sec % 3600) / 60);
    const s = sec % 60;
    if (d > 0) return d + '天' + h + '小时';
    if (h > 0) return h + '小时' + m + '分';
    // 冷却/增益大多是秒级(3~30s),只显示"X分"会全糊成 0 分
    if (m > 0) return m + '分' + s + '秒';
    return s + '秒';
  }

  // ---------------- 全局状态 ----------------
  const S = {
    view: null,          // 服务端返回的完整视图
    meta: null,          // 静态元数据
    auto: true,
    timer: null,
    busy: false,
    lastErr: '',
    wasDead: false,
    rebirthShown: false,
    offlineShown: false,
    lastMobId: null,
    lastMobHp: null,
    lastMaxHP: null,
    lastWriteAt: 0
  };

  const ZONE_REQ_TEXT = {
    village: '', plain: '≥500血', forest: '≥3千血/5防', mine: '≥1.2万血/攻60',
    swamp: '≥4万血/50回/攻150', snow: '≥10万血/30魔防/攻400', volcano: '≥30万血/双防80/攻1200'
  };

  function toast(msg, kind) {
    const el = $('toast');
    el.textContent = msg;
    el.className = 'toast show ' + (kind || '');
    clearTimeout(el._t);
    el._t = setTimeout(() => { el.className = 'toast'; }, 2200);
  }

  function setNet(ok) {
    const el = $('netState');
    el.textContent = ok ? '●' : '○';
    el.className = ok ? 'netok' : 'netbad';
    el.title = ok ? '云端连接正常' : '连接中断，自动重试中';
  }

  function sfx(name) {
    try { if (window.__audio) window.__audio.play(name); } catch (e) { /* 音效失败不影响游戏 */ }
  }

  async function call(fn, okMsg, _retry) {
    // 1) 等当前轮询让锁:轮询每秒持锁约百毫秒,玩家点击撞进窗口就吃 409"操作太快"
    const t0 = Date.now();
    while (S.busy && Date.now() - t0 < 1500) {
      await new Promise(r => setTimeout(r, 60));
    }
    // 2) 双击同一动作(<400ms)并入进行中的写请求;更晚的不同动作排队串行执行
    if (S._writingP && Date.now() - (S._writingStart || 0) < 400) {
      return S._writingP;
    }
    S._writeChain = (S._writeChain || Promise.resolve()).catch(() => { /* 前序失败不阻断后续 */ });
    const mine = S._writeChain.then(() => execWrite(fn, okMsg, _retry));
    S._writeChain = mine;
    return mine;
  }

  async function execWrite(fn, okMsg, _retry) {
    S.lastWriteAt = Date.now();   // 标记写请求发起时刻,晚于此的过期轮询帧会被丢弃
    S._writingStart = Date.now();
    S._writingP = (async () => {
      const r = await fn();
      setNet(true);
      return r;
    })();
    try {
      const r = await S._writingP;
      if (okMsg) toast(okMsg, 'ok');
      return r;
    } catch (e) {
      // 争锁 409(双开窗口/极端时序):静默重试一次,不打扰玩家
      if (!_retry && e.code === 'CONFLICT') {
        await new Promise(r => setTimeout(r, 400));
        return execWrite(fn, okMsg, true);
      }
      setNet(e.code !== 'NETWORK');
      toast(e.message || '操作失败', 'err');
      throw e;
    } finally {
      S._writingP = null;
    }
  }

  // ================= 认证 =================
  let authMode = 'login';

  function initAuth() {
    document.querySelectorAll('.auth-tab').forEach(btn => {
      btn.addEventListener('click', () => {
        authMode = btn.dataset.mode;
        document.querySelectorAll('.auth-tab').forEach(b =>
          b.classList.toggle('active', b === btn));
        $('authSubmit').textContent = authMode === 'login' ? '进入江湖' : '开天辟地';
        $('authErr').textContent = '';
      });
    });

    $('authSubmit').addEventListener('click', doAuth);
    $('authPass').addEventListener('keydown', e => { if (e.key === 'Enter') doAuth(); });
    $('authUser').addEventListener('keydown', e => { if (e.key === 'Enter') $('authPass').focus(); });

    $('btnLogout').addEventListener('click', () => {
      API.clearAuth();
      API.setSlot(0);
      S.view = null;
      stopLoop();
      $('gameRoot').classList.remove('on');
      $('slotMask').style.display = 'none';
      $('authMask').style.display = 'flex';
    });
  }

  async function doAuth() {
    const u = $('authUser').value.trim();
    const p = $('authPass').value;
    if (!u || !p) { $('authErr').textContent = '请填写用户名和密码'; return; }
    $('authErr').textContent = '';
    $('authSubmit').disabled = true;
    try {
      const payload = authMode === 'login'
        ? await API.login(u, p)
        : await API.register(u, p);
      API.setAuth(payload);
      $('authMask').style.display = 'none';
      await showSlots();
    } catch (e) {
      $('authErr').textContent = e.message;
    } finally {
      $('authSubmit').disabled = false;
    }
  }

  // ================= 存档槽 =================
  async function showSlots() {
    const list = await call(() => API.listSaves());
    $('whoami').textContent = API.state.user ? ('当前账号：' + API.state.user.username) : '';
    const box = $('slotChoices');
    box.innerHTML = '';
    list.forEach(slot => {
      const div = document.createElement('div');
      div.className = 'slot-choice' + (slot.empty ? ' empty' : '');
      if (slot.empty) {
        div.innerHTML = `
          <div class="sc-head"><strong>槽位 ${slot.slot}</strong><span class="sc-tag">空</span></div>
          <div class="sc-meta">还没有存档</div>
          <button class="sc-btn create">＋ 新建一世</button>`;
        div.querySelector('.sc-btn').addEventListener('click', async () => {
          const name = prompt('给这一世起个名字（可留空）', '第0世') || '';
          await call(() => API.createSave(slot.slot, name), '新的一世开始了');
          await enterGame(slot.slot);
        });
      } else {
        div.innerHTML = `
          <div class="sc-head"><strong>槽位 ${slot.slot}${slot.slotName ? ' · ' + esc(slot.slotName) : ''}</strong>
            <span class="sc-tag">第 ${slot.gen} 世</span></div>
          <div class="sc-meta">生命上限 ${fmt(slot.maxHp)} · 存活 ${fmtDur(slot.aliveSeconds)}</div>
          <div class="sc-actions">
            <button class="sc-btn play">继续</button>
            <button class="sc-btn exp">导出</button>
            <button class="sc-btn del">删除</button>
          </div>`;
        div.querySelector('.play').addEventListener('click', async () => {
          try {
            await enterGame(slot.slot);
          } catch (e) {
            // 载入失败(网络/档坏)必须回退到选槽界面,不能留一块空白卡死
            $('gameRoot').classList.remove('on');
            $('slotMask').style.display = 'flex';
          }
        });
        div.querySelector('.exp').addEventListener('click', async () => {
          try {
            // 带令牌 fetch 后转 blob 下载（普通链接带不上 Authorization 头）；
            // 前缀算法与 api.js 的 apiBase 保持一致（兼容 /gongpu 与后端根两种部署）
            const m = location.pathname.match(/^(\/[^/.]+)(?=\/|$)/);
            const resp = await fetch((m ? m[1] : '') + '/api/saves/' + slot.slot + '/export', {
              headers: { 'Authorization': 'Bearer ' + API.state.token }
            });
            if (!resp.ok) throw new Error('导出失败');
            const blob = await resp.blob();
            const a = document.createElement('a');
            a.href = URL.createObjectURL(blob);
            a.download = 'gongpu-save-slot' + slot.slot + '.json';
            a.click();
            setTimeout(() => URL.revokeObjectURL(a.href), 5000);
            toast('存档已导出（仅备份留存，游戏不提供导入）', 'ok');
          } catch (e) {
            toast('导出失败，请稍后再试', 'err');
          }
        });
        div.querySelector('.del').addEventListener('click', async () => {
          if (!confirm('删除该存档？此操作不可恢复。')) return;
          await call(() => API.deleteSave(slot.slot), '存档已删除');
          await showSlots();
        });
      }
      box.appendChild(div);
    });
    $('slotMask').style.display = 'flex';
  }

  // ================= 进入游戏 =================
  async function enterGame(slot) {
    API.setSlot(slot);
    // 重置上一档的会话状态：药剂页签名/转生弹窗/离线提示,防止串档残留
    renderPotions._sig = null;
    S.rebirthShown = false;
    S.offlineShown = false;
    S.wasDead = false;
    $('slotMask').style.display = 'none';
    $('gameRoot').classList.add('on');
    if (!S.meta) {
      S.meta = await call(() => API.meta());
      renderZoneButtons();
      renderSkillButtons();
    }
    S._metaAt = Date.now();
    const view = await call(() => API.loadSave(slot));
    applyView(view);
    if (view.dead) {
      renderDeath(view);
    } else if (!view.talent) {
      showTalentRitual();
    } else if (view.clearReady) {
      showRebirth(view);
    }
    startLoop();
    // 新手引导：仅当天赋已觉醒（无待办仪式）时才在首进触发；
    // 未觉醒时由 finishTalent（仪式完成）后再启动，避免引导遮罩挡住觉醒仪式
    if (window.__tutorial && view.talent) {
      setTimeout(() => window.__tutorial.start(), 1200);
    }
  }

  // ================= 轮询循环 =================
  function stopLoop() {
    if (S.timer) { clearInterval(S.timer); S.timer = null; }
  }

  function startLoop() {
    stopLoop();
    S.timer = setInterval(tick, 1000);
    tick();
  }

  async function tick() {
    if (S.busy || !API.state.slot) return;
    if (!S.auto) return;
    // 元数据每 10 分钟静默重拉:管理端改了药剂/被动/区域配置后,在线玩家不必重登才能看到
    if (Date.now() - (S._metaAt || 0) > 600000) {
      S._metaAt = Date.now();
      API.meta().then(m => {
        S.meta = m;
        renderZoneButtons();
        renderSkillButtons();
      }).catch(() => { /* 静默:下个周期再试 */ });
    }
    // 页面隐藏(后台标签/锁屏)时 5 次才发 1 次轮询:移动端省电省流量,服务端少一半空转
    if (document.hidden) {
      S._hiddenSkip = ((S._hiddenSkip || 0) + 1) % 5;
      if (S._hiddenSkip !== 0) return;
    }
    // 失败退避:服务端重启/断网时不每秒硬撞,连败间隔指数放大(2→4→8…封顶 30 秒),
    // 一次成功即清零;切回前台时 visibilitychange 会清退避立即补一帧
    if (S._nextPollAt && Date.now() < S._nextPollAt) return;
    S.busy = true;
    try {
      const t0 = Date.now();
      const view = await API.gameState(API.state.slot);
      setNet(true);
      S._failStreak = 0;
      S._nextPollAt = 0;
      // 写请求(嗑药/穿装/切图等)发起于本次轮询之后 → 轮询快照已过期,丢弃防 UI 回退
      // >= :写请求与轮询同毫秒发起时,旧值同样不能盖掉新写入
      if (S.lastWriteAt >= t0) return;
      applyView(view);
      loadBulletin();
      // Boss 首杀播报（服务端单次下发）
      if (view.bossFirstKill) {
        toast('👑 首杀达成！击败 ' + esc(view.bossFirstKill.boss) + '，获得 ' + fmt(view.bossFirstKill.gold) + ' 金币！', 'ok');
      }
      // 离线收益提示（每次会话只提示一次，避免轮询刷屏）
      if (view.offline && !S.offlineShown) {
        S.offlineShown = true;
        const o = view.offline;
        const hrs = (o.seconds / 3600).toFixed(1);
        if (o.died) {
          // §10.2 离线死亡真实结算:死亡画面由下一轮轮询(renderDeath)接管
          toast('💀 离线的 ' + hrs + ' 小时里，你没能撑住……（尸检报告见下）', 'err');
        } else {
          toast('🌙 离线挂机 ' + hrs + ' 小时：出拳 ' + fmt(o.punches) + ' 次 · 击杀 ' + fmt(o.kills) + ' 只' +
            (o.drops > 0 ? '，捡到 ' + o.drops + ' 件装备' : '') + '（效率 50%）', 'ok');
        }
      }
      if (view.dead) {
        stopLoop();
        renderDeath(view);
      } else if (view.clearReady && !S.rebirthShown) {
        showRebirth(view);
      }
    } catch (e) {
      setNet(e.code !== 'NETWORK');
      S._failStreak = (S._failStreak || 0) + 1;
      S._nextPollAt = Date.now() + Math.min(30000, 1000 * Math.pow(2, S._failStreak));
    } finally {
      S.busy = false;
    }
  }

  // ================= 渲染 =================
  // 委托/合成/江湖数据只在切 tab 时拉取,挂机杀怪推进度后界面不更新。
  // 轮询帧里对"当前激活中的"数据 tab 做 10s 节流刷新。
  function refreshActiveDataTabs() {
    const active = document.querySelector('.game-tab.active');
    if (!active) return;
    const tab = active.dataset.tab;
    const now = Date.now();
    const gap = tab === 'quest' || tab === 'craft' ? 10000 : 30000;
    if (now - (S._tabRefreshAt || 0) < gap) return;
    S._tabRefreshAt = now;
    if (tab === 'quest') loadQuests().catch(() => { /* 静默 */ });
    else if (tab === 'social') loadSocial().catch(() => { /* 静默 */ });
  }

  // ================= 管家悬浮面板 =================
  // 悬浮速览卡:生命/蓝条、当前怪、自动管护与自动售卖快捷开关、嗑药吃食。
  // 每秒视图驱动,签名按 5% 分桶防 DOM 抖动;控件只挂一次,写操作走 call() 统一通道。
  function renderButlerPanel(view) {
    if (!$('butlerFab')) {
      const fab = document.createElement('button');
      fab.id = 'butlerFab';
      fab.textContent = '🤖';
      fab.title = '管家悬浮面板';
      fab.addEventListener('click', () => $('butlerPanel').classList.toggle('on'));
      document.body.appendChild(fab);
      const el = document.createElement('div');
      el.id = 'butlerPanel';
      el.innerHTML =
        '<div class="bp-row bp-mob" id="bpMob"></div>' +
        '<div class="bp-bar"><div class="bp-fill hp" id="bpHp"></div><span id="bpHpTx"></span></div>' +
        '<div class="bp-bar"><div class="bp-fill mp" id="bpMp"></div><span id="bpMpTx"></span></div>' +
        '<label class="bp-row"><input type="checkbox" id="bpMaintain"> 自动管护（卖/吞噬/穿最强）</label>' +
        '<label class="bp-row">自动售卖 <select id="bpSell">' +
        '<option value="0">关</option><option value="1">白及以下</option><option value="2">绿及以下</option>' +
        '<option value="3">蓝及以下</option><option value="4">紫及以下</option></select></label>' +
        '<div class="bp-row bp-btns">' +
        '<button id="bpDrinkHp">🧪 血药</button><button id="bpDrinkMp">🔵 蓝药</button><button id="bpEat">🍖 吃食</button></div>';
      document.body.appendChild(el);
      const saveAuto = async (patch) => {
        const v = S.view;
        if (!v) return;
        const r = await call(() => API.autoConfig(API.state.slot,
          v.autoHpLine, v.autoMpLine, v.autoCure, v.autoFleeLine, v.autoBurn, v.autoSkill, v.autoRebirth,
          patch.autoSellKeep !== undefined ? patch.autoSellKeep : v.autoSellKeep,
          patch.autoEquipBest !== undefined ? patch.autoEquipBest : v.autoEquipBest));
        if (r) applyView(r);
      };
      $('bpMaintain').addEventListener('change', (e) => saveAuto({ autoEquipBest: e.target.checked }));
      $('bpSell').addEventListener('change', (e) => saveAuto({ autoSellKeep: Number(e.target.value) }));
      const drink = async (ids) => {
        const v = S.view;
        if (!v) return;
        const id = ids.find(i => (v.potions || {})[i] > 0);
        if (!id) { toast('没有可用的了', 'err'); return; }
        const r = await call(() => API.usePotion(API.state.slot, id));
        if (r) applyView(r);
      };
      $('bpDrinkHp').addEventListener('click', () => drink(['hp_l', 'hp_s']));
      $('bpDrinkMp').addEventListener('click', () => drink(['mp']));
      $('bpEat').addEventListener('click', () => drink(['food']));
    }
    const p = view.panel;
    const hpPct = Math.max(0, Math.min(100, new Decimal(p.curHP).div(new Decimal(p.maxHP).max(1)).toNumber() * 100));
    const mpPct = Math.max(0, Math.min(100, new Decimal(p.curMP).div(new Decimal(p.MP).max(1)).toNumber() * 100));
    const pot = view.potions || {};
    const sig = [Math.round(hpPct / 5), Math.round(mpPct / 5), view.zone, view.mob ? view.mob.name : '',
      view.autoEquipBest ? 1 : 0, view.autoSellKeep || 0,
      pot.hp_s || 0, pot.hp_l || 0, pot.mp || 0, pot.food || 0, view.dead ? 1 : 0].join('|');
    if (renderButlerPanel._sig === sig) return;
    renderButlerPanel._sig = sig;
    $('bpHp').style.width = hpPct.toFixed(0) + '%';
    $('bpHpTx').textContent = '❤ ' + fmt(p.curHP) + ' / ' + fmt(p.maxHP);
    $('bpMp').style.width = mpPct.toFixed(0) + '%';
    $('bpMpTx').textContent = '🔵 ' + fmt(p.curMP) + ' / ' + fmt(p.MP);
    $('bpMob').textContent = view.dead ? '💀 已阵亡' : ('第 ' + view.gen + ' 世 · ' + (view.mob ? view.mob.name : '—'));
    const mc = $('bpMaintain');
    if (mc.checked !== !!view.autoEquipBest) mc.checked = !!view.autoEquipBest;
    const bs = $('bpSell');
    if (String(bs.value) !== String(view.autoSellKeep || 0)) bs.value = String(view.autoSellKeep || 0);
    $('bpDrinkHp').textContent = '🧪 血药 ×' + ((pot.hp_s || 0) + (pot.hp_l || 0));
    $('bpDrinkMp').textContent = '🔵 蓝药 ×' + (pot.mp || 0);
    $('bpEat').textContent = '🍖 吃食 ×' + (pot.food || 0);
  }

  function applyView(view) {
    if (!view || !view.panel) return;   // 写去重并入的调用在失败时拿到 undefined,直接忽略
    S.view = view;
    renderButlerPanel(view);
    const p = view.panel;

    refreshActiveDataTabs();

    $('hpMax').textContent = fmt(p.maxHP);
    $('hpCur').textContent = fmt(p.curHP);
    $('mpCur').textContent = fmt(p.curMP);
    $('mpMax').textContent = fmt(p.MP);   // 蓝上限随「法力洪流」词条/装备成长
    // 护盾(v3.14 护盾药剂):血量后显示吸收盾余量,无盾隐藏
    const shEl = $('shieldVal');
    if (shEl) {
      const sh = Number(p.shield || 0);
      const has = sh > 0 && !view.dead;
      shEl.textContent = has ? (' 🛡' + fmt(sh)) : '';
      shEl.hidden = !has;
    }

    // +N 跳字：生命上限增长反馈（帧间对比，节流防刷屏）
    if (S.lastMaxHP !== null && S.lastMaxHP !== undefined) {
      try {
        const cur = new Decimal(p.maxHP);
        const prev = new Decimal(S.lastMaxHP);
        if (cur.gt(prev)) {
          const gain = cur.minus(prev);
          const now = performance.now();
          if (now - (S._plusLast || 0) > 800) {
            S._plusLast = now;
            floatDmg('+' + fmt(gain), 'plus');
          }
        }
      } catch (e) { /* 忽略 */ }
    }
    S.lastMaxHP = p.maxHP;
    const hpPct = Math.min(100, new Decimal(p.curHP).div(new Decimal(p.maxHP).max(1)).toNumber() * 100);
    $('hpBar').style.width = hpPct.toFixed(1) + '%';
    // 头像方块内的迷你血条同步
    $('charHpFill').style.width = hpPct.toFixed(1) + '%';
    // 低血量警示：血线 <25% 时血条与当前血量数字红色闪烁
    const low = !view.dead && hpPct < 25;
    $('hpBar').classList.toggle('low-hp', low);
    $('hpCur').classList.toggle('low-hp', low);
    $('charHpFill').classList.toggle('low-hp', low);
    const mpPct = Math.min(100, new Decimal(p.curMP).div(new Decimal(p.MP).max(1)).toNumber() * 100);
    $('mpBar').style.width = mpPct.toFixed(1) + '%';

    $('gen').textContent = '第 ' + view.gen + ' 世';

    // 顶栏天赋徽章：当前觉醒天赋常驻可见
    const TAL_META = {
      eternal: ['♾️ 被动永续', '增益永不消逝', 'gold'],
      baglord: ['🧳 无限装备栏', '无相位栏无限:任何装备都能穿,属性叠加', 'gold'],
      statlord: ['💠 无限属性点', '每杀一怪+1自由属性点,点击属性行加点', 'gold'],
      might: ['💪 天生神力', '物攻 +10%', ''],
      shell: ['🐢 铜皮铁骨', '双防 +10%', ''],
      wind: ['💨 疾风之躯', '移速 +15%', ''],
      goldhand: ['🪙 点金手', '金币 +20%', ''],
      master: ['📕 武学奇才', '冷却 -15%', '']
    };
    const badge = $('talentBadge');
    if (badge) {
      const tm = view.talent && TAL_META[view.talent];
      if (tm) {
        badge.style.display = 'inline-block';
        badge.textContent = tm[0];
        badge.title = tm[1];
        badge.className = 'talent-badge ' + (tm[2] || '');
      } else {
        badge.style.display = 'none';
      }
    }

    // 赋能行
    const order = ['HP', 'PATK', 'MATK', 'PDEF', 'MDEF', 'ASPD', 'RNG', 'SPD', 'CRIT', 'CRITD', 'ACC', 'EVA', 'REGEN'];
    const nameMap = { HP: 'HP', PATK: '物攻', MATK: '魔攻', PDEF: '物防', MDEF: '魔防', ASPD: '攻速', RNG: '射程', SPD: '移速', CRIT: '暴击', CRITD: '暴伤', ACC: '命中', EVA: '闪避', REGEN: '回血' };
    const parts = [];
    order.forEach(k => {
      if (view.blessings && view.blessings[k] !== undefined) {
        parts.push(nameMap[k] + '+' + trimNum(view.blessings[k]) + '/拳');
      }
    });
    $('blessRow').textContent = '🧬 赋能: ' + (parts.join(' · ') || 'HP+1/拳');

    $('stPATK').textContent = fmt(p.PATK);
    $('stMATK').textContent = fmt(p.MATK);
    $('stPDEF').textContent = fmt(p.PDEF);
    $('stMDEF').textContent = fmt(p.MDEF);
    $('stASPD').textContent = fmt(p.ASPD) + '/s';
    $('stRNG').textContent = Number(p.RNG).toFixed(2) + ' 步';
    $('stSPD').textContent = fmt(p.SPD);
    $('stCRIT').textContent = fmtPct(p.CRIT);
    $('stCRITD').textContent = Number(p.CRITD).toFixed(2) + '×';
    $('stACC').textContent = fmtPct(p.ACC);
    $('stEVA').textContent = fmtPct(p.EVA);
    // 回血行 = 固定回血 + 「每秒回复 X% 生命上限」类词条(回春戒生命泉涌)的实际量
    // ——只显示固定值时,1%上限的大头(7.7万血≈775/s)在面板里完全不可见
    $('stREGEN').textContent = fmt(new Decimal(p.REGEN || 0).add(new Decimal(p.regenHpPerSec || 0))) + '/s';
    $('stMREGEN').textContent = fmt(p.MREGEN) + '/s';
    $('stLIFESTEAL').textContent = fmt(p.LIFESTEAL);
    $('stGold').textContent = fmt(view.gold);

    // 无限属性点天赋:自由点数行 + 属性行可点(statlord 专属)
    const statCardEl = $('statCard');
    const isStatlord = view.talent === 'statlord';
    statCardEl.classList.toggle('statlord', isStatlord);
    const fpLine = $('freePointLine');
    fpLine.hidden = !isStatlord;
    if (isStatlord) {
      fpLine.textContent = '💠 自由属性点:' + fmt(view.freePoints) + ' · 点击属性行加点';
    }

    $('stPunch').textContent = fmt(view.stats.punches);
    $('stKill').textContent = fmt(view.stats.kills);
    $('stLived').textContent = fmtDur(view.stats.aliveSeconds);

    // 区域与怪物
    const zones = S.meta ? S.meta.zones : [];
    const z = zones.find(x => x.id === view.zone);
    $('zoneName').textContent = z ? (z.emoji + ' ' + z.name) : '🏡 村口';

    if (view.mob) {
      $('mobEmoji').textContent = view.mob.emoji;
      $('mobName').textContent = view.mob.name + (view.mob.boss ? ' 👑' : '')
        + (view.mob.trait ? ' ' + view.mob.trait.emoji : '');
      // 战斗飘字：怪物血量下降 → 弹伤害（暴击概率抽样 / 击杀提示）
      if (S.lastMobId === view.mob.id && S.lastMobHp !== null) {
        try {
          const cur = new Decimal(view.mob.curHP);
          const prev = new Decimal(S.lastMobHp);
          if (cur.lt(prev) && S.lastMobHp !== 'Infinity' && view.mob.curHP !== 'Infinity') {
            const dmg = prev.minus(cur);
            // 按玩家暴击率抽样决定是否展示为暴击样式（视觉表现，数值由服务端权威）
            const critRate = Number(p.CRIT) || 0;
            const isCrit = Math.random() < critRate;
            floatDmg('-' + fmt(dmg), isCrit ? 'crit' : '');
            sfx('punch');
            // 血条跌破 30% 换目标前，若上一帧怪被打死会走 mob id 变化分支
          }
        } catch (e) { /* Decimal 异常忽略 */ }
        if (view.mob.curHP === 'Infinity') {
          // 木桩不飘伤害（无限血没有血量差）
        }
      }
      if (view.mob.curHP !== 'Infinity' && S.lastMobHp !== 'Infinity' && S.lastMobId && S.lastMobId !== view.mob.id && S.lastMobHp !== null) {
        floatDmg('💀 击杀', 'kill');
        sfx('kill');
      }
      S.lastMobId = view.mob.id;
      S.lastMobHp = view.mob.curHP;
      const mhp = view.mob.maxHP;
      if (mhp === 'Infinity') {
        $('mobHp').textContent = '∞';
        $('mobBar').style.width = '100%';
      } else {
        $('mobHp').textContent = fmt(view.mob.curHP) + ' / ' + fmt(mhp);
        const pct = Math.max(0, Math.min(100, new Decimal(view.mob.curHP).div(new Decimal(mhp).max(1)).toNumber() * 100));
        $('mobBar').style.width = pct.toFixed(1) + '%';
      }
      $('mobDesc').textContent = view.mob.boss
        ? 'Boss 战 · 会施放沉默封印你的被动'
        : (view.mob.trait
            ? '词条「' + view.mob.trait.emoji + ' ' + view.mob.trait.name + '」' + view.mob.trait.desc
            : '出拳就是修行');
    } else {
      $('mobEmoji').textContent = '💨';
      $('mobName').textContent = '寻找目标中…';
      $('mobHp').textContent = '-';
      $('mobBar').style.width = '0%';
      $('mobDesc').textContent = '';
    }

    // 区域按钮状态
    document.querySelectorAll('.zone-btn').forEach(b => {
      b.classList.toggle('active', b.dataset.zone === view.zone);
    });

    // 挂机未存档警示条（DESIGN.md §10.1：战斗区且距上次存档超 5 分钟）
    let warnBar = $('saveWarnBar');
    if (!warnBar) {
      warnBar = document.createElement('div');
      warnBar.id = 'saveWarnBar';
      warnBar.className = 'save-warn-bar';
      document.querySelector('.topbar').after(warnBar);
    }
    const lastSave = view.lastSaveAt || 0;
    const unsavedMin = lastSave ? (Date.now() - lastSave) / 60000 : 999;
    const showWarn = !view.dead && view.zone !== 'village' && unsavedMin > 5;
    warnBar.style.display = showWarn ? 'block' : 'none';
    if (showWarn) {
      warnBar.textContent = '⚠️ 已挂机 ' + Math.floor(unsavedMin) + ' 分钟未存档——死了档就没了，回村口存一下！';
    }

    renderEffects(view);
    // 成就核对（纯展示层，从服务端统计推导）
    if (window.__achievements) {
      window.__achievements.check(view);
      window.__achievements.drain().forEach(function (a) {
        toast('🏅 成就解锁：' + a.emoji + ' ' + a.name, 'ok');
      });
      var achBox = document.getElementById('achArea');
      if (achBox) window.__achievements.render(achBox);
    }
    renderBag(view);
    renderPotions(view);
    renderPassives(view);
    renderSaveHint(view);
    renderTitles(view);
    renderCodex(view);

    // 飞升按钮
    $('btnRebirth').style.display = view.clearReady ? 'inline-block' : 'none';

    // 技能冷却显示
    renderSkillCooldowns(view);

    // 被动状态栏（buff-chip 横条）：当前生效增益/减益
    // 永续天赋下永久生效的增益不再逐个展示(8 个常驻 chip 挤满主画面毫无信息量),
    // 悬浮顶栏「♾️ 被动永续」徽章可查看永久清单(initTalentPop)
    const buffBar = $('buffBar');
    if (buffBar) {
      const CHIP_META = {
        burn: ['🔥', '燃烧生命', true], block: ['🛡️', '格挡反击', true],
        rapid: ['⚡', '急速射击', true], surge: ['✨', '魔力涌动', true],
        heavy: ['💥', '蓄力重击', true],
        regen_food: ['🍖', '兔肉补给', true], spd_pot: ['💨', '速度药剂', true],
        growth_pot: ['🍵', '修行药剂', true], iron_pot: ['🛡️', '铁壁药剂', true],
        silence: ['🤐', '沉默', false], poison: ['☠️', '中毒', false],
        burn_dot: ['🌋', '灼烧', false],
        // v3.14 新药剂增益芯片
        crit_pot: ['👁️', '锐眼', true], critd_pot: ['🩸', '残暴', true],
        lifesteal_pot: ['🦇', '嗜血', true], atk_pot: ['⚔️', '狂暴', true],
        def_pot: ['🪨', '磐石', true], aspd_pot: ['🌪️', '疾风', true],
        acc_pot: ['🎯', '明察', true], eva_pot: ['🌫️', '幻影', true],
        mregen_pot: ['🧿', '凝神', true], shield_pot: ['🛡', '护盾', true]
      };
      const isEt = view.talent === 'eternal';
      let html = '';
      // 纯魔法弹哑火警示:每发耗蓝 > 蓝上限时一发都打不起,永久哑火(必须换武器)
      const bulletMp = Number((view.panel || {}).BULLETMP || 0);
      const mpCap = Number((view.panel || {}).MP || 0);
      if (bulletMp > 0 && bulletMp > mpCap) {
        html += '<span class="buff-chip bad" title="每发耗蓝 ' + bulletMp + ' > 蓝上限 ' + mpCap +
                '，一发都打不起——请更换武器或堆蓝上限"><span>🔇 弹药哑火</span><span class="buff-time">耗蓝' + bulletMp + '</span></span>';
      }
      Object.entries(view.buffs || {}).forEach(([k, v]) => {
        if (!(v > 0)) return;
        if (isEt && ETERNAL_KEYS.includes(k)) return;   // 永久生效 → 不占主画面
        const m = CHIP_META[k];
        if (!m) return;
        html += '<span class="buff-chip ' + (m[2] ? 'good' : 'bad') + '" title="剩余 ' + fmtDur(v) + '">' +
          '<span>' + m[0] + ' ' + m[1] + '</span>' +
          '<span class="buff-time">' + fmtDur(v) + '</span>' +
          '</span>';
      });
      // 签名守卫(范式同 renderPotions):chip 集合不变只更新倒计时文本,
      // 不整条 innerHTML 重建——autoSkill/autoBurn 常驻时此前每秒销毁重建整条
      const sig = JSON.stringify([html ? html.replace(/buff-time">[^<]*/g, 'buff-time">') : '', bulletMp, mpCap]);
      if (sig === applyView._buffSig) {
        // 只同步倒计时:chip 数量/种类未变,剩余时长文本逐个更新
        const times = buffBar.querySelectorAll('.buff-time');
        const vals = [];
        Object.entries(view.buffs || {}).forEach(([k, v]) => {
          if (v > 0 && !(isEt && ETERNAL_KEYS.includes(k)) && CHIP_META[k]) vals.push(fmtDur(v));
        });
        if (bulletMp > 0 && bulletMp > mpCap) vals.unshift('耗蓝' + bulletMp);
        times.forEach((el, i) => { if (vals[i] !== undefined && el.textContent !== vals[i]) el.textContent = vals[i]; });
        return;
      }
      applyView._buffSig = sig;
      buffBar.innerHTML = html;
    }

    // 燃烧状态条
    const burnLeft = view.buffs && view.buffs.burn;
    $('burnBuff').textContent = burnLeft ? ('🔥 燃烧中 · 攻击×2 · 剩余 ' + fmtDur(burnLeft)) : '';
  }

  function trimNum(v) {
    const n = Number(v);
    if (!isFinite(n)) return String(v);
    if (n >= 1) return String(Math.round(n * 100) / 100);
    return String(Math.round(n * 100000) / 100000);
  }

  function renderZoneButtons() {
    const box = $('zoneBtns');
    box.innerHTML = '';
    S.meta.zones.forEach(z => {
      const b = document.createElement('button');
      b.className = 'zone-btn';
      b.dataset.zone = z.id;
      b.innerHTML = z.emoji + ' ' + z.name +
        (ZONE_REQ_TEXT[z.id] ? '<span class="req">' + ZONE_REQ_TEXT[z.id] + '</span>' : '');
      b.addEventListener('click', async () => {
        const r = await call(() => API.setZone(API.state.slot, z.id));
        applyView(r);
        toast('已前往' + z.name, 'ok');
      });
      box.appendChild(b);
    });
  }

  function renderSkillButtons() {
    const map = {
      btnBurn: 'burn', btnRapid: 'rapid', btnHeavy: 'heavy',
      btnBlock: 'block', btnSurge: 'surge', btnRoar: 'roar'
    };
    Object.entries(map).forEach(([btnId, skillId]) => {
      $(btnId).addEventListener('click', async () => {
        const r = await call(() => API.castSkill(API.state.slot, skillId));
        applyView(r);
      });
    });

    $('btnFlee').addEventListener('click', async () => {
      const r = await call(() => API.flee(API.state.slot));
      applyView(r);
      if (r.result && r.result.ok) toast('🏃 逃跑成功', 'ok');
      else toast('💢 逃跑失败，扣血强行回村', 'err');
    });

    $('btnBoss').addEventListener('click', async () => {
      if (!confirm('挑战当前区域 Boss？它血厚攻高，还会沉默你的被动。')) return;
      const r = await call(() => API.startBoss(API.state.slot));
      applyView(r);
      toast('👑 Boss 出现！', 'err');
    });

    $('btnAuto').addEventListener('click', () => {
      S.auto = !S.auto;
      $('btnAuto').textContent = S.auto ? '🤖 自动挂机（开）' : '⏸️ 自动挂机（关）';
      if (S.auto) startLoop();
    });

    $('btnRebirth').addEventListener('click', () => showRebirth(S.view));

    $('btnManualSave').addEventListener('click', async () => {
      await call(() => API.manualSave(API.state.slot), '已存档到云端');
    });

    $('btnDaily').addEventListener('click', async () => {
      try {
        sfx('levelup');
        const r = await call(() => API.dailyClaim(API.state.slot));
        const w = r.result && r.result.reward;
        if (w) toast('🎁 签到成功：金币 ' + fmt(w.gold) + ' + 血药×' + (w.hp_s || 0) + ' 蓝药×' + (w.mp || 0) +
          (w.growth ? ' + 修行药剂×1' : '') + (w.ironpot ? ' + 铁壁药剂×1' : ''), 'ok');
        applyView(r);
        markDailyDone();
      } catch (e) { /* call 已 toast */ }
    });

    $('btnBackToSlots').addEventListener('click', () => {
      stopLoop();
      $('gameRoot').classList.remove('on');
      showSlots();
    });

    // 每日签到按钮状态：当天已签则置灰(localStorage 按日标记,刷新后仍有效)
    function dailyKey() {
      // 本地日期(UTC+8 玩家在 0:00~8:00 的 toISOString 还是昨天,会错置灰一整个早上)
      const d = new Date();
      return 'gongpu_daily_' + d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
    }
    function markDailyDone() {
      try { localStorage.setItem(dailyKey(), '1'); } catch (e) { /* 隐私模式忽略 */ }
      const b = $('btnDaily');
      if (b) {
        b.disabled = true;
        b.title = '今日已签到，明天再来';
        b.textContent = '✅ 已签到';
      }
    }
    function renderDailyState() {
      let done = false;
      try { done = !!localStorage.getItem(dailyKey()); } catch (e) { /* 忽略 */ }
      const b = $('btnDaily');
      if (b && done) {
        b.disabled = true;
        b.textContent = '✅ 已签到';
        b.title = '今日已签到，明天再来';
      }
    }
    renderDailyState();

    // 标签页
    document.querySelectorAll('.game-tab').forEach(tab => {
      tab.addEventListener('click', () => {
        document.querySelectorAll('.game-tab').forEach(t => t.classList.toggle('active', t === tab));
        document.querySelectorAll('.tab-pane').forEach(p => {
          const on = p.dataset.pane === tab.dataset.tab;
          p.hidden = !on;
          p.classList.toggle('active', on);
        });
        if (tab.dataset.tab === 'rank') loadRank();
        if (tab.dataset.tab === 'save') loadGraveyard();
        if (tab.dataset.tab === 'social') loadSocial();
        if (tab.dataset.tab === 'quest') loadQuests();
      });
    });
  }

  function renderSkillCooldowns(view) {
    const map = { btnBurn: 'burn', btnRapid: 'rapid', btnHeavy: 'heavy', btnBlock: 'block', btnSurge: 'surge', btnRoar: 'roar' };
    Object.entries(map).forEach(([btnId, id]) => {
      const el = $(btnId);
      const cd = view.cds && view.cds[id];
      if (cd && cd > 0) {
        el.disabled = true;
        el.dataset.cd = fmtDur(cd);
      } else {
        el.disabled = false;
        delete el.dataset.cd;
      }
      // 技能熟练度提示（服务端 mastery 下发）：每 20 次 +1 档，每档 CD-3%，最高 5 档
      const mv = view.mastery && view.mastery[id];
      if (mv) {
        el.title = '熟练度 Lv.' + (mv.lv || 0) + '（已施放 ' + fmt(mv.casts) + ' 次）· 每档冷却 -3%';
      }
    });
  }

  function renderEffects(view) {
    // 签名守卫:无增益的挂机帧(绝大多数)直接跳过;有增益时剩余时长每秒真变,照常重建
    const sig = JSON.stringify([view.talent, view.buffs, view.passives]);
    if (sig === renderEffects._sig) return;
    renderEffects._sig = sig;
    const area = $('effectArea');
    const buffMeta = {
      burn: ['🔥', '燃烧生命', '攻击 ×2'],
      block: ['🛡️', '格挡反击', '受伤减半'],
      rapid: ['⚡', '急速射击', '攻速 ×3'],
      surge: ['✨', '魔力涌动', '技能不耗蓝'],
      regen_food: ['🍖', '兔肉补给', '回血 +20'],
      spd_pot: ['💨', '速度药剂', '攻速 +20%'],
      silence: ['🤐', '沉默', '被动失效'],
      poison: ['☠️', '中毒', '持续掉血'],
      burn_dot: ['🌋', '灼烧', '持续掉血']
    };
    const talentMeta = {
      eternal: ['♾️', '被动永续', '增益永不消逝'],
      might: ['💪', '天生神力', '物攻 +10%'],
      shell: ['🐢', '铜皮铁骨', '双防 +10%'],
      wind: ['💨', '疾风之躯', '移速 +15%'],
      goldhand: ['🪙', '点金手', '金币 +20%'],
      master: ['📕', '武学奇才', '冷却 -15%']
    };

    const talent = view.talent && talentMeta[view.talent];
    // 天赋为被动永续时：状态区展示常驻标记（永续天赋本身的效果=增益不衰减）
    const eternalTalentRow = view.talent === 'eternal'
      ? [['♾️', '被动永续', '所有增益永不消逝（天赋常驻）']] : [];
    const passives = (view.passives || []).map(id => {
      const def = (S.meta && S.meta.passives) ? S.meta.passives.find(x => x.id === id) : null;
      return def ? [def.emoji, def.name, def.desc] : null;
    }).filter(Boolean);

    // 永续天赋持有者：增益条目带 ♾️ 标记（永不衰减）
    const isEternal = view.talent === 'eternal';
    const ETERNAL_SET = ['burn', 'block', 'rapid', 'surge', 'regen_food', 'spd_pot', 'growth_pot', 'iron_pot'];
    const buffs = Object.entries(view.buffs || {})
      .filter(([, v]) => v > 0)
      .map(([k, v]) => {
        const m = buffMeta[k];
        if (!m) return null;
        const forever = isEternal && ETERNAL_SET.includes(k);
        return [m[0], m[1], m[2] + (forever ? ' · ♾️ 永续' : ' · ' + fmtDur(v))];
      }).filter(Boolean);

    function group(title, items, emptyText) {
      const list = items.length
        ? items.map(([e, n, d]) => `<div class="effect-item"><span>${e}</span><b>${n}</b><i>${d}</i></div>`).join('')
        : `<div class="effect-none">${emptyText}</div>`;
      return `<div class="effect-group"><div class="effect-group-title">${title}</div>${list}</div>`;
    }

    area.innerHTML =
      group('🧬 天赋', talent ? [talent] : [], '尚未觉醒天赋') +
      group('📖 被动', passives, '打怪学习被动技能（药剂页学习）') +
      group('⚡ 状态', eternalTalentRow.concat(buffs), '暂无增益 / 减益');
  }

  function renderSaveHint(view) {
    const el = $('saveState');
    if (view.dead) {
      el.textContent = '💀 已阵亡';
      el.className = 'savebad';
    } else if (view.zone !== 'village') {
      el.textContent = '⚔️ 战斗中（不可存档）';
      el.className = 'savewarn';
    } else {
      el.textContent = '✅ 安全区';
      el.className = 'saveok';
    }
    const hint = $('saveRuleHint');
    if (hint) {
      hint.textContent = view.zone === 'village'
        ? '当前位于安全区，可以存档'
        : '战斗中不可存档：请先回村口';
    }
  }

  // ================= 装备 / 背包 =================
  // 真指针设备(可 hover):已装备槽保留 title 提示 + 点击即卸下;
  // 触屏设备 hover 失效,点击改为弹词条小卡(卸下走卡内按钮)
  const CAN_HOVER = window.matchMedia && window.matchMedia('(hover: hover)').matches;
  const SLOT_NAME = {
    weapon: '武器', offhand: '副手', bow: '弓', head: '头',
    chest: '胸', legs: '腿', boots: '鞋', ring1: '戒指1', ring2: '戒指2', necklace: '项链'
  };
  const QUALITY_COLOR = ['#aaa', '#5cb85c', '#4a9eff', '#b366ff'];
  const QUALITY_NAME = ['普通', '精良', '稀有', '史诗'];

  function affixText(a) {
    // 每秒回复 X% 生命上限类(回春戒「生命泉涌」)——刻意保留百分比语义
    if (a.type === 'pct_hp') {
      return a.name + ' +' + (Number(a.val) * 100).toFixed(1) + '%生命上限/s';
    }
    if (a.type === 'flat') {
      if (a.key === 'REGEN' || a.key === 'MREGEN' || a.key === 'ASPD') {
        return a.name + ' +' + Number(a.val).toFixed(2) + '/s';   // 回血/魔回/攻速:按每秒显示,与面板单位一致
      }
      // 率型词条(暴击/暴伤/命中/闪避/吸血):按百分点显示直接数值,不带 %
      // ——内部仍存小数概率(0.15),面板照常显示 %;此处只做展示层换算
      if (['CRIT', 'CRITD', 'ACC', 'EVA', 'LIFESTEAL'].includes(a.key)) {
        return a.name + ' +' + String(Math.round(Number(a.val) * 1000) / 10);
      }
      if (a.key === 'MP') {
        return a.name + ' +' + trimNum(a.val) + ' 蓝上限';   // 蓝上限词条(法力洪流)
      }
      return a.name + ' +' + trimNum(a.val);   // 物攻/魔攻/防御/移速/射程:直接加面板数值
    }
    return a.name + ' +' + (Number(a.val) * 100).toFixed(1) + '%';   // 旧档 pct 词条:真是乘算,保留 %
  }

  // ---- 已装备词条小卡(触屏):title 悬浮在手机上不存在,点击槽位弹出词条与卸下按钮 ----
  let itemPopEl = null;
  function hideItemPop() {
    if (itemPopEl) itemPopEl.style.display = 'none';
  }
  function showItemPop(it, onUnequip) {
    if (!itemPopEl) {
      itemPopEl = document.createElement('div');
      itemPopEl.id = 'itemPop';
      itemPopEl.className = 'item-pop';
      document.body.appendChild(itemPopEl);
      itemPopEl.addEventListener('click', e => e.stopPropagation());
      document.addEventListener('click', e => {   // 点外部关闭
        if (itemPopEl && itemPopEl.style.display === 'block' && !itemPopEl.contains(e.target)) hideItemPop();
      });
    }
    itemPopEl.innerHTML = `
      <div class="ip-name" style="color:${QUALITY_COLOR[it.quality]}">${it.emoji} ${it.name}${it.enhance ? ' +' + it.enhance : ''}</div>
      <div class="ip-affixes">${(it.affixes || []).map(affixText).join('<br>') || '（无词条）'}</div>
      <div class="ip-btns"><button class="ip-unequip">卸下</button><button class="ip-close">关闭</button></div>`;
    itemPopEl.style.display = 'block';
    itemPopEl.querySelector('.ip-unequip').onclick = async () => { hideItemPop(); await onUnequip(); };
    itemPopEl.querySelector('.ip-close').onclick = hideItemPop;
  }

  // ---- 通用点选模态:替代 prompt 输序号/输 1/2/3(移动端几乎不可用且易误输) ----
  // 返回所选 choices[].value;点遮罩取消返回 null。视觉复用 death-mask/rebirth-choice。
  function pickModal(title, intro, choices) {
    return new Promise(resolve => {
      const mask = document.createElement('div');
      mask.className = 'death-mask';
      mask.innerHTML = `<div class="death-box pick-box">
        <h2>${title}</h2>
        ${intro ? `<p class="pick-intro">${intro}</p>` : ''}
        <div class="pick-choices"></div>
      </div>`;
      const wrap = mask.querySelector('.pick-choices');
      choices.forEach(c => {
        const b = document.createElement('button');
        b.className = 'rebirth-choice';
        b.innerHTML = c.label + (c.sub ? `<span class="talent-tip">${c.sub}</span>` : '');
        b.addEventListener('click', () => { mask.remove(); resolve(c.value); });
        wrap.appendChild(b);
      });
      mask.addEventListener('click', e => {
        if (e.target === mask) { mask.remove(); resolve(null); }
      });
      document.body.appendChild(mask);
    });
  }

  function renderBag(view) {
    // 内容签名守卫:挂机时背包/穿戴不变就跳过重建——每秒销毁重建 40+ 节点×7 按钮
    // 会导致移动端掉帧、按钮监听器每秒换新引发偶发点击丢失(签名范式同 renderPotions)
    const sig = JSON.stringify([view.bag, view.equipped, view.extraSlots, view.bagMax, view.bagTotal]);
    if (sig === renderBag._sig) return;
    renderBag._sig = sig;
    const box = $('equippedSlots');
    box.innerHTML = '';
    Object.keys(SLOT_NAME).forEach(slot => {
      const it = view.equipped && view.equipped[slot];
      const d = document.createElement('div');
      d.className = 'equip-slot' + (it ? '' : ' empty');
      if (it) {
        d.style.borderColor = QUALITY_COLOR[it.quality];
        d.innerHTML = `<div class="es-name" style="color:${QUALITY_COLOR[it.quality]}">${it.emoji} ${it.name}${it.enhance ? ' +' + it.enhance : ''}</div>
                       <div class="es-slot">${SLOT_NAME[slot]}</div>`;
        d.title = (it.affixes || []).map(affixText).join('\n');
        d.addEventListener('click', async () => {
          // 触屏:hover title 失效,点击先弹词条小卡,卸下走卡内按钮
          if (!CAN_HOVER) {
            showItemPop(it, async () => {
              const r = await call(() => API.unequip(API.state.slot, slot), '已卸下');
              applyView(r);
            });
            return;
          }
          const r = await call(() => API.unequip(API.state.slot, slot), '已卸下');
          applyView(r);
        });
      } else {
        d.innerHTML = `<div class="es-name muted">— ${SLOT_NAME[slot]} —</div>`;
      }
      box.appendChild(d);
    });
    // 无限装备栏天赋：无相位栏单独横向滑轨——extras 无限增长不再撑爆已装备面板,
    // 原生 10 格保持两列,超出部分横向滚动(滑块)
    const extraOpen = Math.max(0, Number(view.extraSlots) || 0);
    const strip = $('extraStrip');
    const stripWrap = $('extraStripWrap');
    stripWrap.hidden = extraOpen === 0;
    if (extraOpen > 0) {
      const usedExtra = Object.keys(view.equipped || {}).filter(k => k.startsWith('extra_')).length;
      $('extraStripCount').textContent = '已穿 ' + usedExtra + ' 格 · 无限 · 横向滑动查看';
    }
    strip.innerHTML = '';
    for (let i = 0; i < extraOpen; i++) {
      const key = 'extra_' + i;
      const it = view.equipped && view.equipped[key];
      const d = document.createElement('div');
      d.className = 'equip-slot extra-slot' + (it ? '' : ' empty');
      if (it) {
        d.style.borderColor = QUALITY_COLOR[it.quality];
        d.innerHTML = `<div class="es-name" style="color:${QUALITY_COLOR[it.quality]}">${it.emoji} ${it.name}${it.enhance ? ' +' + it.enhance : ''}</div>
                       <div class="es-slot">🧳 无相位 ${i + 1}</div>`;
        d.title = (it.affixes || []).map(affixText).join('\n') + '\n(点击卸下;卸下后才能强化/洗炼/卖出)';
        d.addEventListener('click', async () => {
          if (!CAN_HOVER) {
            showItemPop(it, async () => {
              const r = await call(() => API.unequip(API.state.slot, key), '已卸下');
              applyView(r);
            });
            return;
          }
          const r = await call(() => API.unequip(API.state.slot, key), '已卸下');
          applyView(r);
        });
      } else {
        d.innerHTML = `<div class="es-name muted">— 🧳 无相位 ${i + 1} —</div>`;
        d.title = '无相位栏无限：任何装备都能穿上，属性全部叠加';
      }
      strip.appendChild(d);
    }

    const bagTotal = Number(view.bagTotal != null ? view.bagTotal : (view.bag ? view.bag.length : 0));
    $('bagCount').textContent = bagTotal + '/' + view.bagMax
      + (bagTotal > (view.bag ? view.bag.length : 0) ? '（仅显示前' + (view.bag ? view.bag.length : 0) + '件）' : '');
    // 一键穿最强按钮：只挂一次
    if (!$('btnAutoEquip')) {
      const eb = document.createElement('button');
      eb.id = 'btnAutoEquip';
      eb.textContent = '⚡ 穿最强';
      eb.title = '按评分自动换装+把剩余装备全穿入无相位栏;同名装备自动吞噬合并(属性累加),历史同名膨胀一并清理';
      eb.addEventListener('click', async () => {
        try {
          const r = await call(() => API.autoEquip(API.state.slot));
          const res = r.result || {};
          toast(res.equippedCount > 0 ? ('⚡ 已换装 ' + res.equippedCount + ' 件') : '当前已是最优配置', 'ok');
          applyView(r);
        } catch (e) { /* call 已 toast */ }
      });
      $('bagCount').parentNode.appendChild(eb);
    }
    // 一键分级卖按钮组：只挂一次（白/绿/蓝/紫及以下，keep=1~4）
    if (!$('btnAutoSell1')) {
      const tiers = [
        { keep: 1, label: '🧹 卖白装', text: '普通（白）' },
        { keep: 2, label: '卖绿装及以下', text: '精良（绿）及以下' },
        { keep: 3, label: '卖蓝装及以下', text: '稀有（蓝）及以下' },
        { keep: 4, label: '卖紫装及以下', text: '史诗（紫）及以下，即全部装备' },
      ];
      for (const t of tiers) {
        const btn = document.createElement('button');
        btn.id = 'btnAutoSell' + t.keep;
        btn.textContent = t.label;
        btn.title = '一键卖出背包内' + t.text + '品质装备';
        btn.addEventListener('click', async () => {
          if (!confirm('卖出背包内' + t.text + '品质的全部装备？')) return;
          try {
            const r = await call(() => API.autoSell(API.state.slot, t.keep));
            const res = r.result || {};
            toast('卖出 ' + (res.soldCount || 0) + ' 件，+' + fmt(res.soldFor || 0) + ' 金币', 'ok');
            applyView(r);
          } catch (e) { /* call 已 toast */ }
        });
        $('bagCount').parentNode.appendChild(btn);
      }
    }
    const bag = $('bagList');
    bag.innerHTML = '';
    if (!view.bag || !view.bag.length) {
      bag.innerHTML = '<p class="empty-hint">击杀怪物掉落装备</p>';
      return;
    }
    view.bag.forEach((it, idx) => {
      const d = document.createElement('div');
      d.className = 'bag-item';
      d.style.borderLeftColor = QUALITY_COLOR[it.quality];
      d.innerHTML = `
        <div class="bi-head" style="color:${QUALITY_COLOR[it.quality]}">
          ${it.emoji} ${it.name}${it.enhance ? ' +' + it.enhance : ''}
          <span class="bi-q">${QUALITY_NAME[it.quality]}</span>
        </div>
        <div class="bi-affix">${(it.affixes || []).map(affixText).join(' · ') || '（无词条）'}</div>
        <div class="bi-actions">
          <button data-act="equip">装备</button>
          ${(Number(view.extraSlots) > 0) ? '<button data-act="equipExtra" title="穿入无相位栏;同名装备自动吞噬合并,属性累加">加穿</button>' : ''}
          <button data-act="enhance">强化</button>
          <button data-act="reroll">洗炼</button>
          ${((it.slot === 'weapon' || it.slot === 'bow') && !(it.affixes || []).some(a => a.sideKey === 'ENCHANT')) ? '<button data-act="enchant">附魔</button>' : ''}
          <button data-act="sell">卖出</button>
          <button data-act="discard" class="bi-discard">丢弃</button>
        </div>`;
      d.querySelectorAll('button').forEach(b => {
        b.addEventListener('click', async (ev) => {
          ev.stopPropagation();
          const act = b.dataset.act;
          if (act === 'discard' && !confirm('丢弃该装备？不可恢复。')) return;
          let lockIdx = -1;
          if (act === 'reroll') {
            const afx = it.affixes || [];
            if (afx.length > 1) {
              // 多词条：模态点选锁定词条（费用×3），或全部重刷——替代 prompt 输序号
              const choices = afx.map((a, i) => ({
                label: (i + 1) + '. ' + esc(affixText(a)),
                sub: '锁定此词条 · 其余重刷 · 费用×3',
                value: i - 0
              })).concat([{ label: '全部重刷', sub: '不锁定 · 费用正常', value: -1 }]);
              const pick = await pickModal('🔁 洗炼 · ' + esc(it.name), '底子与强化保留，只重刷未锁定词条。', choices);
              if (pick === null) return;   // 取消
              lockIdx = pick;
            } else if (!confirm('洗炼会重刷全部词条（底子与强化保留）。确认？')) {
              return;   // 单词条无可锁,保留一道确认防误点
            }
          }
          if (act === 'enchant') {
            // 选卷轴（火/冰/雷）——模态点选,替代 prompt 输 1/2/3
            const code = await pickModal('📜 附魔 · ' + esc(it.name), '为武器附魔，附加固定魔攻词条。', [
              { label: '🔥 火之卷轴', sub: '魔攻 +15', value: 'scroll_fire' },
              { label: '🧊 冰之卷轴', sub: '魔攻 +12', value: 'scroll_ice' },
              { label: '⚡ 雷之卷轴', sub: '魔攻 +20', value: 'scroll_thunder' }
            ]);
            if (!code) return;   // 取消
            try {
              const r = await call(() => API.enchantItem(API.state.slot, code, idx));
              const res = r.result || {};
              toast('🔥 附魔成功：' + (res.enchanted || '') + '（魔攻+' + (res.matkBonus || 0) + '）', 'ok');
              applyView(r);
            } catch (e) { toast(e.message, 'err'); }
            return;
          }
          const fn = act === 'equip' ? () => API.equip(API.state.slot, idx)
            : act === 'equipExtra' ? () => API.equipExtra(API.state.slot, idx)
            : act === 'enhance' ? () => API.enhanceItem(API.state.slot, idx)
              : act === 'reroll' ? () => API.rerollItem(API.state.slot, idx, lockIdx)
              : act === 'discard' ? () => API.discardItem(API.state.slot, idx)
              : () => API.sellItem(API.state.slot, idx);
          sfx(act === 'sell' ? 'sell' : 'click');
          const r = await call(fn, act === 'equip' || act === 'equipExtra' ? '已装备' : act === 'enhance' ? '强化成功'
            : act === 'reroll' ? '词条已重刷' : '已卖出');
          applyView(r);
        });
      });
      bag.appendChild(d);
    });
  }

  // ================= 药剂 / 被动 =================
  function renderPotions(view) {
    const box = $('potionList');
    // —— 挂机管家配置区：只构建一次 ——
    // 旧实现每秒 innerHTML 重建，输入框每秒被销毁，玩家根本没法填数值
    let cfg = $('autoCfgBox');
    // 用户正在操作管家表单时暂停轮询回填:focus 判定对触屏点选/键盘切换不可靠
    // (漏判会把玩家刚勾选的项下一秒用服务端旧值复位),叠加"最近 input/change 时间戳"兜底
    const touching = Date.now() - (S._cfgTouchedAt || 0) < 3000;
    const ae = document.activeElement;
    const editing = touching || !!(ae && ((ae.classList && ae.classList.contains('auto-line-input')) || ae.id === 'autoCureChk' || ae.id === 'autoBurnChk' || ae.id === 'autoSkillChk' || ae.id === 'autoRebirthChk' || ae.id === 'autoEquipChk'));
    if (!cfg) {
      cfg = document.createElement('div');
      cfg.className = 'potion-item auto-cfg';
      cfg.id = 'autoCfgBox';
      cfg.innerHTML = `
        <div class="pi-info"><b>🤖 挂机管家（自动策略）</b>
          <div class="pi-desc">血量低于 <input id="autoHpInput" class="auto-line-input" type="number" min="0" max="90" step="5"
            value="${Math.round((view.autoHpLine || 0) * 100)}" style="width:44px">% 嗑血药 ·
            蓝量低于 <input id="autoMpInput" class="auto-line-input" type="number" min="0" max="90" step="5"
            value="${Math.round((view.autoMpLine || 0) * 100)}" style="width:44px">% 嗑蓝药<br>
            血量低于 <input id="autoFleeInput" class="auto-line-input" type="number" min="0" max="50" step="5"
            value="${Math.round((view.autoFleeLine || 0) * 100)}" style="width:44px">% 自动逃回村口（0=关）·
            <label><input id="autoCureChk" type="checkbox" ${view.autoCure ? 'checked' : ''}> 中毒/灼烧自动解毒（没药自动买）</label><br>
            <label><input id="autoSkillChk" type="checkbox" ${view.autoSkill ? 'checked' : ''}> ⚔️ 技能常驻（急速/涌动/蓄力自动补，血危补格挡/怒吼）</label>
            <label><input id="autoBurnChk" type="checkbox" ${view.autoBurn ? 'checked' : ''}> 🔥 燃烧常驻（血量&gt;60% 才补，有风险）</label><br>
            <label><input id="autoRebirthChk" type="checkbox" ${view.autoRebirth ? 'checked' : ''}> 🌟 自动飞升（打到 1 载自动转生，新手保底每世重来）</label> ·
            <label><input id="autoEquipChk" type="checkbox" ${view.autoEquipBest ? 'checked' : ''}> 🧲 自动管护（掉落自动卖白装+吞噬同名+穿最强，背包不再堆积）</label> ·
            <span title="挂机打到的装备按档位直接折现金币，不进背包">⚖️ 自动卖装备</span> <select id="autoSellSel" class="auto-line-input" style="width:104px" title="挂机打到的装备按档位直接折现金币，不进背包">
              <option value="0" ${!view.autoSellKeep ? 'selected' : ''}>关闭</option>
              <option value="1" ${view.autoSellKeep === 1 ? 'selected' : ''}>白色及以下</option>
              <option value="2" ${view.autoSellKeep === 2 ? 'selected' : ''}>绿色及以下</option>
              <option value="3" ${view.autoSellKeep === 3 ? 'selected' : ''}>蓝色及以下</option>
              <option value="4" ${view.autoSellKeep === 4 ? 'selected' : ''}>紫色及以下（全折现）</option>
            </select></div>
        </div>
        <div class="pi-btns"><button id="btnAutoCfg">保存</button></div>`;
      box.appendChild(cfg);
      // input/change 捕获阶段打时间戳:勾选/下拉/输入后 3s 内轮询不回填
      cfg.addEventListener('input', () => { S._cfgTouchedAt = Date.now(); }, true);
      cfg.addEventListener('change', () => { S._cfgTouchedAt = Date.now(); }, true);
      $('btnAutoCfg').addEventListener('click', async () => {
        const hp = Math.max(0, Math.min(0.9, (Number($('autoHpInput').value) || 0) / 100));
        const mp = Math.max(0, Math.min(0.9, (Number($('autoMpInput').value) || 0) / 100));
        const fl = Math.max(0, Math.min(0.5, (Number($('autoFleeInput').value) || 0) / 100));
        const cu = $('autoCureChk').checked;
        const ab = $('autoBurnChk').checked;
        const as2 = $('autoSkillChk').checked;
        const ar = $('autoRebirthChk').checked;
        const aeq = $('autoEquipChk').checked;
        const ask = Number($('autoSellSel').value) || 0;
        const r = await call(() => API.autoConfig(API.state.slot, hp, mp, cu, fl, ab, as2, ar, ask, aeq), '挂机策略已保存');
        S._cfgTouchedAt = 0;   // 保存成功,解除抑制让服务端真值即时回填
        applyView(r);
      });
    } else if (!editing) {
      // 未在编辑时才用服务端值回填;正在输入的 tick 跳过同步
      const setLine = (id, v) => {
        const el = $(id);
        const nv = Math.round((v || 0) * 100);
        if (el && String(el.value) !== String(nv)) el.value = nv;
      };
      setLine('autoHpInput', view.autoHpLine);
      setLine('autoMpInput', view.autoMpLine);
      setLine('autoFleeInput', view.autoFleeLine);
      $('autoCureChk').checked = !!view.autoCure;
      $('autoBurnChk').checked = !!view.autoBurn;
      $('autoSkillChk').checked = !!view.autoSkill;
      const arb = $('autoRebirthChk');
      if (arb) arb.checked = !!view.autoRebirth;
      const aeqEl = $('autoEquipChk');
      if (aeqEl) aeqEl.checked = !!view.autoEquipBest;
      const asl = $('autoSellSel');
      if (asl && String(asl.value) !== String(view.autoSellKeep || 0)) asl.value = String(view.autoSellKeep || 0);
    }
    // —— 药剂列表：库存签名未变化就不重建(按钮监听器不丢,DOM 不闪) ——
    const sig = JSON.stringify(view.potions || {});
    if (sig === renderPotions._sig) return;
    renderPotions._sig = sig;
    box.querySelectorAll('.potion-item:not(.auto-cfg)').forEach(el => el.remove());
    (S.meta ? S.meta.potions : []).forEach(p => {
      const have = (view.potions && view.potions[p.id]) || 0;
      const d = document.createElement('div');
      d.className = 'potion-item';
      d.innerHTML = `
        <div class="pi-info"><b>${p.emoji} ${p.name}</b> <span class="pi-own">×${have}</span>
          <div class="pi-desc">${p.desc}</div></div>
        <div class="pi-btns">
          <button data-act="use" ${have > 0 ? '' : 'disabled'}>服用</button>
          <button data-act="buy">买（${p.price}金）</button>
        </div>`;
      d.querySelector('[data-act=use]').addEventListener('click', async () => {
        const r = await call(() => API.usePotion(API.state.slot, p.id));
        applyView(r);
      });
      d.querySelector('[data-act=buy]').addEventListener('click', async () => {
        sfx('buy');
        const r = await call(() => API.buyPotion(API.state.slot, p.id, 1), '购买成功');
        applyView(r);
      });
      const b10 = document.createElement('button');
      b10.textContent = '买×10';
      b10.addEventListener('click', async () => {
        const r = await call(() => API.buyPotion(API.state.slot, p.id, 10), '购买成功');
        applyView(r);
      });
      d.querySelector('.pi-btns').appendChild(b10);
      box.appendChild(d);
    });
  }

  function renderPassives(view) {
    const sig = JSON.stringify(view.passives || []);   // 签名守卫:被动未变化不重建
    if (sig === renderPassives._sig) return;
    renderPassives._sig = sig;
    const box = $('passiveList');
    box.innerHTML = '';
    (S.meta ? S.meta.passives : []).forEach(p => {
      const owned = (view.passives || []).includes(p.id);
      const d = document.createElement('div');
      d.className = 'potion-item' + (owned ? ' owned' : '');
      d.innerHTML = `
        <div class="pi-info"><b>${p.emoji} ${p.name}</b>${owned ? ' <span class="pi-own">已掌握</span>' : ''}
          <div class="pi-desc">${p.desc}</div></div>
        <div class="pi-btns">${owned ? '' : `<button data-act="learn">学习（${p.price}金）</button>`}</div>`;
      const btn = d.querySelector('[data-act=learn]');
      if (btn) {
        btn.addEventListener('click', async () => {
          const r = await call(() => API.learnPassive(API.state.slot, p.id), '已掌握 ' + p.name);
          applyView(r);
        });
      }
      box.appendChild(d);
    });
  }

  // ================= 死亡 =================
  function renderDeath(view) {
    S.wasDead = true;
    stopLoop();
    $('deathKiller').textContent = view.deathCause || '未知';
    $('deathHP').textContent = fmt(view.panel.maxHP);
    $('deathPunches').textContent = fmt(view.stats.punches);
    $('deathLived').textContent = fmtDur(view.stats.aliveSeconds);
    $('deathSave').textContent = '第 ' + view.gen + ' 世 · 死亡 ' + view.stats.deaths + ' 次';

    // 香火余额（决定第一个按钮可用性）
    API.socialMe().then(card => {
      const inc = card.incense || 0;
      $('deathIncenseInfo').textContent =
        '🕯️ 你的香火：' + inc + ' 炷' + (inc > 0 ? '（好友祭奠你的墓碑所得）' : '（还没有人祭奠过你）');
      const btn = $('btnIncense');
      btn.disabled = inc <= 0;
      btn.title = inc > 0 ? '花 1 炷香火，满血满蓝原地复活，面板装备全保留' : '香火不足：去江湖页祭奠别人，别人也会来祭奠你';
    }).catch(() => {
      $('deathIncenseInfo').textContent = '';
      $('btnIncense').disabled = true;
    });

    const p = view.panel;
    const mob = view.mob || {};
    const rows = [];
    rows.push(`📋 尸检报告`);
    rows.push(`· 定格血量：${fmt(p.curHP)} / ${fmt(p.maxHP)}`);
    rows.push(`· 终局物防 ${fmt(p.PDEF)} · 魔防 ${fmt(p.MDEF)} · 回血 ${fmt(p.REGEN)}/s`);
    rows.push(`· 本世出拳 ${fmt(view.stats.punches)} 次 · 击杀 ${fmt(view.stats.kills)}`);
    if (mob.name && mob.PATK !== undefined) {
      rows.push(`· 致命对手：${mob.name}（物攻 ${fmt(mob.PATK)} / 魔攻 ${fmt(mob.MATK)}）`);
      // 复活建议(legacy analyzeDeath 语义):按致命对手量化缺口
      const needPdef = Math.ceil(Number(mob.PATK) / 0.8);
      rows.push(`· 复活建议：物防 ≥ ${fmt(needPdef)} 可免疫它的物伤（当前 ${fmt(p.PDEF)}）`);
      if (Number(mob.MATK) > 0) {
        rows.push(`           魔防 ≥ ${fmt(Math.ceil(Number(mob.MATK) / 0.8))} 可免疫魔伤（当前 ${fmt(p.MDEF)}）`);
      }
      const mobAspd = Number(mob.ASPD) || 1;
      rows.push(`           回血 ≥ ${fmt(Number(mob.PATK) * mobAspd)}/s 可站撸（当前 ${fmt(p.REGEN)}/s），或拉射程风筝`);
    }
    // 挂机策略快照(设计 §3.3 死亡复盘):让玩家知道当时管家是怎么配的
    rows.push(`· 挂机策略：嗑药线 ${Math.round((view.autoHpLine || 0) * 100)}% · 逃跑线 ${Math.round((view.autoFleeLine || 0) * 100)}%`
      + ` · 技能常驻 ${view.autoSkill ? '开' : '关'} · 燃烧常驻 ${view.autoBurn ? '开' : '关'}`
      + ` · 自动售卖 ${view.autoSellKeep ? (view.autoSellKeep >= 4 ? '全部折现' : '留' + ['白装', '精良+', '稀有+', '史诗+'][view.autoSellKeep]) : '关'}`);
    // 击杀序列(最后 10 条)
    if (view.recentKills && view.recentKills.length) {
      rows.push(`· 最后的战斗：${view.recentKills.join('、')}`);
    }
    $('deathAutopsy').textContent = rows.join('\n');
    sfx('death');
    $('deathMask').style.display = 'flex';

    $('btnIncense').onclick = async () => {
      try {
        sfx('levelup');
        await API.incenseRevive(API.state.slot);
        toast('🕯️ 香火燃起，你原地满血复活！', 'ok');
        $('deathMask').style.display = 'none';
        S.wasDead = false;
        // 缓存里的死亡态已被复活接口改写，直接拉最新状态
        const v = await call(() => API.gameState(API.state.slot));
        applyView(v);
        startLoop();
      } catch (e) {
        toast(e.message, 'err');
      }
    };
    $('btnLoadLast').onclick = async () => {
      const epitaph = prompt('给这一世留一句墓志铭（可留空，最多30字）', '') || '';
      const r = await call(() => API.deathAction(API.state.slot, 'revive',
        { epitaph: epitaph.substring(0, 30) }), '已回档到最近存档');
      $('deathMask').style.display = 'none';
      S.wasDead = false;
      applyView(r);
      startLoop();
    };
    $('btnNewGame').onclick = async () => {
      if (!confirm('重新开档：本世面板清零，但万世传承（赋能与世代）保留。继续？')) return;
      const r = await call(() => API.deathAction(API.state.slot, 'restart'), '尘归尘，新的一世');
      $('deathMask').style.display = 'none';
      S.wasDead = false;
      applyView(r);
      startLoop();
    };
  }

  // ================= 天赋觉醒 =================
  async function showTalentRitual() {
    stopLoop();
    let data;
    try {
      data = await API.talentView(API.state.slot);
    } catch (e) {
      // 接口异常不挡游戏
      startLoop();
      return;
    }
    if (data.drawn) {
      startLoop();
      return;
    }
    const box = $('talentChoices');
    box.innerHTML = '';
    // 全靠抽卡：单一抽取入口（命运在点击瞬间定格）
    const drawBtn = document.createElement('button');
    drawBtn.className = 'talent-btn gold';
    drawBtn.style.cssText = 'display:block;margin:0 auto 14px;padding:12px 40px;font-size:16px;';
    drawBtn.innerHTML = '🔮 触摸命运水晶';
    drawBtn.addEventListener('click', async () => {
      drawBtn.disabled = true;
      const r = await call(() => API.drawTalent(API.state.slot, null), '觉醒完成');
      showTalentResult(r.result || r);
    });
    box.appendChild(drawBtn);
    // 奖池字幕：所有天赋从左到右滚动展示（跑马灯，顶级机缘带概率金标）
    const pool = data.pool || [];
    const strip = document.createElement('div');
    strip.className = 'talent-marquee';
    // 注意:不显示任何真实概率——"万中无一"是玩家看到的叙事,真实数值只活在服务端
    const items = pool.map(t =>
      '<span class="tm-item' + (t.topTier ? ' tm-top' : '') + '"><span class="tm-emoji">' + t.emoji + '</span>' + esc(t.name) +
      '<i>' + esc(t.desc) + '</i></span>'
    ).join('<span class="tm-sep">◆</span>');
    // 复制两份拼接，保证滚动无缝循环
    strip.innerHTML = '<div class="tm-track">' + items + '<span class="tm-sep">◆</span>' + items + '<span class="tm-sep">◆</span></div>';
    box.appendChild(strip);
    // 抽取规则说明
    const tip = document.createElement('p');
    tip.className = 'talent-tip';
    tip.textContent = '♾️ 被动永续 · 🧳 无限装备栏 · 💠 无限属性点 藏在命运之中 · 万中无一的机缘';
    box.appendChild(tip);
    $('talentMask').style.display = 'flex';
  }

  // 抽卡结果仪式：顶级机缘(eternal/baglord)= 金色暴雨 + 天选剧本（万中无一仪式感）
  function showTalentResult(result) {
    if (!result || !result.talent) {
      finishTalent(result);
      return;
    }
    const isEternal = result.talent === 'eternal';
    const isBaglord = result.talent === 'baglord';
    const isStatlord = result.talent === 'statlord';
    const isTop = result.topTier || isEternal || isBaglord || isStatlord;
    const box = $('talentChoices');
    box.innerHTML = '';
    const card = document.createElement('div');
    card.className = 'talent-result ' + (isTop ? 'eternal' : '');
    if (isTop) {
      // 天选剧本：金色暴雨 + 幸运女神 + 万中无一（勿外泄实际概率）
      // 金雨容器在卡片内部（absolute 定位），不能写遮罩——否则会覆盖结果卡和按钮
      const body = isStatlord
        ? '<div class="lucky-line">💠 命运的点数池，向你倾斜了！</div>' +
          '<h2 class="chosen-one">你就是那 1/1,000,000 的天选之人</h2>' +
          '<span class="talent-badge">✨ 万中无一 · 概率 0.0001% ✨</span>' +
          '<div class="talent-card gold">' +
            '<div class="tc-emoji">' + esc(result.emoji) + '</div>' +
            '<div class="tc-name">' + esc(result.name) + '</div>' +
            '<div class="tc-row">每杀一怪 <b class="tc-inf">+1</b> 自由属性点</div>' +
            '<div class="tc-row">属性面板点击属性行 · <b>自由加点</b></div>' +
            '<div class="tc-note">凡人的属性天生注定<br>你的每一次杀伐，都在重塑肉身</div>' +
          '</div>'
        : isBaglord
        ? '<div class="lucky-line">🎒 命运的行囊，向你敞开了！</div>' +
          '<h2 class="chosen-one">你就是那 1/1,000,000 的天选之人</h2>' +
          '<span class="talent-badge">✨ 万中无一 · 概率 0.0001% ✨</span>' +
          '<div class="talent-card gold">' +
            '<div class="tc-emoji">' + esc(result.emoji) + '</div>' +
            '<div class="tc-name">' + esc(result.name) + '</div>' +
            '<div class="tc-row">无相位装备栏 <b class="tc-inf">无限</b></div>' +
            '<div class="tc-row">任何装备都能穿上 · <b>属性全部叠加</b></div>' +
            '<div class="tc-note">凡人一件武器走天涯<br>你的行囊，装得下整个江湖</div>' +
          '</div>'
        : '<div class="lucky-line">🍀 幸运女神眷顾！</div>' +
          '<h2 class="chosen-one">你就是那 1/1,000,000 的天选之人</h2>' +
          '<span class="talent-badge">✨ 万中无一 · 概率 0.0001% ✨</span>' +
          '<div class="talent-card gold">' +
            '<div class="tc-emoji">' + esc(result.emoji) + '</div>' +
            '<div class="tc-name">' + esc(result.name) + '</div>' +
            '<div class="tc-row">所有增益被动 <b class="tc-inf">永久存续</b></div>' +
            '<div class="tc-row">燃烧 · 格挡 · 急速 · 涌动… <b>一旦生效，永不消逝</b></div>' +
            '<div class="tc-note">凡人的增益如朝露，日出即散<br>你的增益，将与灵魂同在</div>' +
          '</div>';
      card.style.position = 'relative';
      card.innerHTML =
        '<div id="talentRain" style="position:absolute;inset:0;overflow:hidden;pointer-events:none;"></div>' +
        '<div style="position:relative;z-index:2">' + body +
        '<button id="btnAwakeGo" class="talent-btn gold">⚡ 觉醒天赋</button>' +
        '</div>';
      box.appendChild(card);
      // 金雨写入卡片内专用容器（不会覆盖结果卡与按钮）
      goldRain($('talentRain'), 40);
    } else {
      card.innerHTML =
        '<div class="lucky-line dim">命运之轮缓缓停下了</div>' +
        '<h2 class="talent-title small">获得天赋</h2>' +
        '<div class="talent-card">' +
          '<div class="tc-emoji">' + esc(result.emoji) + '</div>' +
          '<div class="tc-name">' + esc(result.name) + '</div>' +
          '<div class="tc-row">' + esc(result.desc) + '</div>' +
        '</div>' +
        '<button id="btnAwakeGo" class="talent-btn">⚡ 觉醒天赋</button>';
      box.appendChild(card);
    }
    $('btnAwakeGo').addEventListener('click', () => {
      // 清金雨节点
      document.querySelectorAll('.grain').forEach(el => el.remove());
      finishTalent(result);
    });
  }

  function finishTalent(result) {
    $('talentMask').style.display = 'none';
    if (result && result.emoji && result.name) {
      toast(result.emoji + ' 觉醒天赋：' + result.name + '（' + result.desc + '）', 'ok');
    }
    tick();
    startLoop();
    // 觉醒仪式完成后启动新手引导（首次），避免与仪式冲突
    if (window.__tutorial) {
      setTimeout(() => window.__tutorial.start(), 1200);
    }
  }

  // ================= 转生 =================
  // 转生全屏特效：满屏 +1 汇成光柱（DESIGN.md §12.1）
  function playAscendFx(done) {
    const fx = document.createElement('div');
    fx.id = 'ascendFx';
    fx.innerHTML = '<div class="ascend-beam"></div>';
    document.body.appendChild(fx);
    // 满屏 +1：40 个随机位置上浮汇入中央光柱
    for (let i = 0; i < 40; i++) {
      const el = document.createElement('span');
      el.className = 'ascend-plus';
      el.textContent = '+1';
      el.style.left = (5 + Math.random() * 90) + '%';
      el.style.top = (10 + Math.random() * 80) + '%';
      el.style.animationDelay = (Math.random() * 0.9).toFixed(2) + 's';
      el.style.fontSize = (11 + Math.random() * 16).toFixed(0) + 'px';
      fx.appendChild(el);
    }
    // 仪式文案
    const line = document.createElement('div');
    line.className = 'ascend-line';
    line.textContent = '⚡ 拳至一载，肉身成圣 ⚡';
    fx.appendChild(line);
    setTimeout(() => { fx.remove(); if (done) done(); }, 1700);
  }

  async function showRebirth(view) {
    S.rebirthShown = true;
    stopLoop();
    try {
      // 先播全屏光柱特效，再出赋能选择
      playAscendFx(null);
      await new Promise(r => setTimeout(r, 1700));
      const data = await call(() => API.blessOptions(API.state.slot));
      const all = view.allBlessed;
      $('rebirthTip').textContent = all
        ? '赋能已集齐全部 13 条属性。进入强化纪元：自选一条已有赋能，让步长永久提升。'
        : '拳至一载，肉身成圣。选择下一世普攻永久成长的新属性：';
      const box = $('rebirthChoices');
      box.innerHTML = '';
      data.options.forEach(o => {
        const d = document.createElement('button');
        d.className = 'rebirth-choice' + (o.disabled ? ' disabled' : '');
        d.disabled = o.disabled;
        d.innerHTML = `<span class="rc-emoji">${o.emoji}</span>
          <span class="rc-name">${o.name}</span>
          <span class="rc-step">+${trimNum(o.step)}/拳${o.lv ? ' · 强化 ' + o.lv : ''}</span>`;
        d.addEventListener('click', async () => {
          if (!confirm('本世飞升，选择「' + o.name + '」带往来生？')) return;
          const r = await call(() => API.rebirth(API.state.slot, o.key), '🌟 第 ' + (data.gen + 1) + ' 世，开始');
          $('rebirthMask').style.display = 'none';
          S.rebirthShown = false;
          applyView(r);
          startLoop();
        });
        box.appendChild(d);
      });
      $('rebirthMask').style.display = 'flex';
    } catch (e) {
      // 赋能选项拉取失败:复位标记并恢复轮询,否则仪式卡死、轮询停摆
      S.rebirthShown = false;
      startLoop();
    }
  }

  // ================= 世界公告 =================
  let bulletinLast = 0;
  async function loadBulletin(force) {
    const now = Date.now();
    if (!force && now - bulletinLast < 30000) return;
    bulletinLast = now;
    try {
      const r = await API.bulletin();
      const rows = r.rows || [];
      const bar = $('bulletinBar');
      if (!rows.length) {
        bar.style.display = 'none';
        return;
      }
      bar.style.display = 'block';
      // 最新一条在前；公开接口无需鉴权，玩家名已转义
      bar.innerHTML = rows.slice(0, 5).map(x =>
        '<span class="bb-item">📣 <b>' + esc(x.player) + '</b> 在 ' + esc(x.zone) +
        ' 打出了 <span class="bb-star">★' + esc(x.stars) + '</span> ' + esc(x.item) + '</span>'
      ).join('');
    } catch (e) {
      // 公告失败静默
    }
  }

  // ================= 称号系统（本地推导，无后端请求） =================
  function computeTitles(view) {
    const st = view.stats || {};
    const out = [];
    if (Number(view.gen) >= 1) out.push(['🌟', '转世者', '完成一次飞升']);
    if (Number(view.gen) >= 5) out.push(['🧬', '轮回行者', '第 5 世']);
    if (Number(view.gen) >= 12) out.push(['♾️', '万世圆满', '集齐 13 条赋能']);
    const hp = num(st.bestMaxHP);
    if (hp >= 1e6) out.push(['🫀', '百万雄师', '历史生命上限 ≥ 100 万']);
    const kills = Number(st.kills) || 0;
    if (kills >= 1000) out.push(['⚔️', '千人斩', '累计击杀 ≥ 1000']);
    const deaths = Number(st.deaths) || 0;
    if (deaths >= 5) out.push(['⚰️', '不死鸟', '死亡 ≥ 5 次仍在修行']);
    if ((view.bossKills || []).length >= 6) out.push(['👑', '屠龙者', '全区域 Boss 首杀']);
    if (view.talent) out.push(['✨', '觉醒者', '觉醒天赋']);
    if ((view.blessings && Object.keys(view.blessings).length) >= 5) out.push(['🧿', '多线修行', '5 条赋能同修']);
    return out;
  }

  function renderTitles(view) {
    const holder = $('titleArea');
    if (!holder) return;
    // 签名只取 computeTitles 实际依赖的字段——旧签名含整个 stats,
    // punches/aliveSeconds 每秒都变导致守卫永不命中,titleArea 每秒重建
    const st = view.stats || {};
    const sig = JSON.stringify([view.gen, st.bestMaxHP, st.kills, st.deaths,
      (view.bossKills || []).length, view.talent,
      view.blessings ? Object.keys(view.blessings).length : 0]);
    if (sig === renderTitles._sig) return;
    renderTitles._sig = sig;
    const list = computeTitles(view);
    if (!list.length) {
      holder.innerHTML = '<div class="effect-none">尚未获得称号</div>';
      return;
    }
    holder.innerHTML = list.map(t =>
      '<div class="effect-item"><span>' + t[0] + '</span><b>' + t[1] + '</b><i>' + t[2] + '</i></div>'
    ).join('');
  }

  // ================= 怪物图鉴（meta 驱动，无后端请求） =================
  function renderCodex(view) {
    const holder = $('codexArea');
    if (!holder || !S.meta) return;
    // 签名守卫:图鉴只在击杀新怪种时才变,挂机帧全部跳过
    const sig = JSON.stringify(view.codex || []);
    if (sig === renderCodex._sig) return;
    renderCodex._sig = sig;
    // 服务端 codex 是 Set<String>,JSON 序列化为数组;兼容旧对象形态
    const seen = Array.isArray(view.codex) ? new Set(view.codex) : (view.codex || {});
    const has = k => seen instanceof Set ? seen.has(k) : !!seen[k];
    let seenN = 0;
    const rows = [];
    S.meta.zones.forEach(z => {
      (z.mobs || []).forEach(m => {
        if (has(m.id)) seenN++;
        rows.push('<div class="codex-item' + (has(m.id) ? '' : ' unseen') + '">' +
          (has(m.id) ? m.emoji : '❓') + ' ' + (has(m.id) ? m.name : '未知怪物') +
          '<i>' + (has(m.id) ? (m.desc || '') : '击杀后解锁') + '</i></div>');
      });
      if (z.hasBoss) {
        const bossSeen = has('boss_' + z.id);
        if (bossSeen) seenN++;
        rows.push('<div class="codex-item boss' + (bossSeen ? '' : ' unseen') + '">' +
          (bossSeen ? '👑 Boss·' + z.name : '❓ ' + z.name + ' Boss') +
          '<i>' + (bossSeen ? '已击杀' : '首杀后解锁') + '</i></div>');
      }
    });
    holder.innerHTML = '<div class="effect-group-title">📖 已见识 ' + seenN + '/' + rows.length + '</div>' + rows.join('');
  }

  function num(x) {
    try { return new Decimal(x).toNumber(); } catch (e) { return Number(x) || 0; }
  }

  // ================= 榜单 / 墓园 =================
  // 三榜切换:生命上限/财富/世代(gongpu:lb:* ZSET,服务端 60s 节流上报)
  let rankBoard = 'maxhp';
  const RANK_TABS = [
    ['maxhp', '❤️ 生命榜'], ['gold', '💰 财富榜'], ['gen', '🌟 世代榜']
  ];
  async function loadRank() {
    const box = $('rankList');
    try {
      const r = await API.leaderboard(rankBoard);
      const tabs = RANK_TABS.map(([id, label]) =>
        `<button class="rank-tab${id === rankBoard ? ' active' : ''}" data-board="${id}">${label}</button>`).join('');
      if (!r.rows || !r.rows.length) {
        box.innerHTML = tabs + '<p class="empty-hint">还没有人上榜，第一个就是你了</p>';
      } else {
        box.innerHTML = tabs + r.rows.map(x => {
          const meta = rankBoard === 'gold' ? '身家 ' + esc(x.goldText)
            : rankBoard === 'gen' ? '已达 ' + Number(x.gen) + ' 世'
            : '第 ' + esc(x.gen) + ' 世 · 生命上限 ' + esc(x.maxHPText);
          return `<div class="grave-item">
            <b>#${x.rank} ${esc(x.username)}</b>
            <span class="gi-meta">${meta}</span>
          </div>`;
        }).join('');
      }
      box.querySelectorAll('.rank-tab').forEach(b => b.addEventListener('click', () => {
        rankBoard = b.dataset.board;
        loadRank();
      }));
    } catch (e) {
      box.innerHTML = '<p class="empty-hint">榜单暂时不可用</p>';
    }
  }

  async function loadGraveyard() {
    const box = $('graveList');
    try {
      const rows = await API.graveyardMine();
      if (!rows || !rows.length) {
        box.innerHTML = '<p class="empty-hint">还没有碑。阵亡立黑碑，飞升立金碑。</p>';
        return;
      }
      box.innerHTML = rows.map(g => {
        const gold = g.kind === 'ascend';
        return `<div class="grave-item">
          <b>${gold ? '🟡' : '⚫'} 第 ${g.gen} 世${gold ? ' · 飞升' : ''}</b>
          <span class="gi-meta">${gold ? esc(g.killer || '飞升') : ('亡于 ' + esc(g.killer || '未知'))} · ${esc(g.finalHpText || '0')}血 · ${fmtDur(g.livedSec || 0)}${g.epitaph ? ' ·「' + esc(g.epitaph) + '」' : ''}</span>
        </div>`;
      }).join('');
    } catch (e) {
      box.innerHTML = '<p class="empty-hint">墓园加载失败</p>';
    }
  }

  // ================= 冒险公会（委托） =================
  const ITEM_NAME = { hp_s: '小血药', hp_l: '大血药', mp: '蓝药', food: '兔肉', cure: '解毒剂', spd: '速度药剂', growth: '修行药剂', ironpot: '铁壁药剂' };

  async function loadQuests() {
    const box = $('questList');
    if (!box) return;
    try {
      const r = await API.quests(API.state.slot);
      const rows = r.rows || [];
      if (!rows.length) {
        box.innerHTML = '<p class="empty-hint">公会暂时没有委托</p>';
        return;
      }
      box.innerHTML = rows.map(q => {
        const canAccept = q.eligible && !q.active;
        const btns = q.active
          ? (q.ready
            ? '<button class="sc-btn" data-act="deliver" data-code="' + esc(q.code) + '">✅ 交付领赏</button>'
            : '<button class="sc-btn" data-act="abandon" data-code="' + esc(q.code) + '">放弃</button>')
          : (canAccept ? '<button class="sc-btn" data-act="accept" data-code="' + esc(q.code) + '">接受委托</button>' : '');
        const itemTxt = q.rewardItem ? ' + ' + (ITEM_NAME[q.rewardItem] || q.rewardItem) + '×' + q.rewardItemCount : '';
        return '<div class="grave-item quest-item">'
          + '<b>' + esc(q.emoji) + ' ' + esc(q.name) + (q.active ? ' <span style="color:var(--gold)">[进行中]</span>' : '') + (q.done && !q.repeatable ? ' <span style="color:var(--green)">[已完成]</span>' : '') + '</b>'
          + '<span class="gi-meta">' + esc(q.desc || '') + ' · 目标：击杀 ' + q.targetKills + ' 只 · 奖励 ' + fmt(q.rewardGold) + ' 金币' + esc(itemTxt) + '</span>'
          + (q.active ? '<span class="gi-meta" style="color:var(--gold-bright)">进度：' + q.progress + '/' + q.targetKills + (q.ready ? ' ✅ 可交付' : '') + '</span>' : '')
          + (q.rejectReason && !q.active ? '<span class="gi-meta" style="color:var(--txt-faint)">' + esc(q.rejectReason) + '</span>' : '')
          + '<span class="sc-actions">' + btns + '</span>'
          + '</div>';
      }).join('');
      box.querySelectorAll('button[data-act]').forEach(b => {
        b.addEventListener('click', async () => {
          const act = b.dataset.act, code = b.dataset.code;
          if (act === 'abandon' && !confirm('放弃该委托？进度作废。')) return;
          try {
            const fn = act === 'accept' ? API.questAccept : act === 'deliver' ? API.questDeliver : API.questAbandon;
            const r = await fn(API.state.slot, code);
            if (act === 'deliver' && r.result && r.result.reward) {
              const w = r.result.reward;
              toast('🎉 委托完成：+' + fmt(w.gold) + ' 金币' + (w.item ? ' + ' + (ITEM_NAME[w.item] || w.item) + '×' + w.count : ''), 'ok');
            } else if (act === 'accept') {
              toast('📜 已接受委托', 'ok');
            }
            loadQuests();
          } catch (e) { toast(e.message, 'err'); }
        });
      });
    } catch (e) {
      box.innerHTML = '<p class="empty-hint">委托加载失败：' + esc(e.message) + '</p>';
    }
  }

  // ================= 图纸合成 =================
  // ================= 江湖（社交） =================
  async function loadSocial() {
    loadDuel();
    loadWorldGrave();
  }

  async function loadDuel() {
    const box = $('duelBox');
    // 玩家正在输入对手名时跳过重绘(数据页 30s 周期刷新曾周期性清空输入框)
    if (box.contains(document.activeElement)) return;
    let card = {};
    try { card = await API.socialMe(); } catch (e) { /* 未登录态忽略 */ }
    // 重绘保留对手名与上局战报:refreshActiveDataTabs 周期刷新不再吃掉它们
    const prevLog = $('duelLog') ? $('duelLog').textContent : '';
    const prevTarget = $('duelTarget') ? $('duelTarget').value : '';
    const rec = (card.wins || 0) + '胜' + (card.losses || 0) + '负';
    const inc = card.incense || 0;
    box.innerHTML = `
      <div class="potion-item">
        <div class="pi-info"><b>⚔️ 我的战绩</b>
          <div class="pi-desc">${rec} · 香火 ${inc} 炷 · 切磋不伤身，只争胜负</div>
        </div>
      </div>
      <div class="potion-item">
        <div class="pi-info"><b>输入对手名号发起切磋</b>
          <div class="pi-desc">双方各取生命上限最高的存档面板，模拟 30 回合</div>
        </div>
        <div class="pi-btns"><button id="btnDuelGo">开打</button></div>
      </div>
      <input id="duelTarget" class="auth-input" placeholder="对手用户名" maxlength="16">
      <div id="duelLog" class="pi-desc" style="white-space:pre-line;margin-top:6px"></div>`;
    if (prevTarget) $('duelTarget').value = prevTarget;
    if (prevLog) $('duelLog').textContent = prevLog;
    $('btnDuelGo').addEventListener('click', async () => {
      const t = $('duelTarget').value.trim();
      if (!t) { toast('先输入对手名号', 'err'); return; }
      try {
        if (!API.state.slot) { toast('先选择一个存档槽再切磋', 'err'); return; }
        const r = await API.duel(t, API.state.slot);
        $('duelLog').textContent = (r.win ? '🏆 你赢了！' : '💨 你输了。') + '\n'
          + (r.log || []).join('\n') + '\n当前战绩：' + r.record;
        toast(r.win ? '切磋获胜' : '切磋落败', r.win ? 'ok' : 'err');
        loadDuel();
      } catch (e) {
        $('duelLog').textContent = e.message;
        toast(e.message, 'err');
      }
    });
  }

  async function loadWorldGrave() {
    const box = $('worldGraveBox');
    try {
      const r = await API.worldGraveyard();
      const rows = r.rows || [];
      if (!rows.length) {
        box.innerHTML = '<p class="empty-hint">全服还没有碑。第一个立碑的留名千古。</p>';
        return;
      }
      box.innerHTML = rows.map(g => {
        const gold = g.kind === 'ascend';
        return `<div class="grave-item${g.perfect ? ' perfect-grave' : ''}">
          <b>${g.perfect ? '🌕' : (gold ? '🟡' : '⚫')} ${esc(g.owner)} · 第 ${g.gen} 世${gold ? ' 飞升' : ''}${g.perfect ? ' · 圆满金碑' : ''}</b>
          <span class="gi-meta">${gold ? esc(g.killer || '飞升') : ('亡于 ' + esc(g.killer || '未知'))} · ${esc(g.finalHpText || '0')}血 · 🕯️${g.tributes || 0}${g.epitaph ? ' ·「' + esc(g.epitaph) + '」' : ''}</span>
          ${g.own ? '' : '<button class="sc-btn tribute-btn" data-grave="' + g.id + '">🕯️ 祭奠（200金）</button>'}
        </div>`;
      }).join('');
      box.querySelectorAll('.tribute-btn').forEach(b => {
        b.addEventListener('click', async () => {
          try {
            if (!API.state.slot) { toast('先选择一个存档槽再祭奠', 'err'); return; }
            const r = await API.tribute(Number(b.dataset.grave), API.state.slot);
            toast('🕯️ 已为 ' + r.owner + ' 点上一炷香火', 'ok');
            loadWorldGrave();
          } catch (e) {
            toast(e.message, 'err');
          }
        });
      });
    } catch (e) {
      box.innerHTML = '<p class="empty-hint">世界墓园加载失败</p>';
    }
  }

  // ================= 属性悬浮卡 =================
  // 左上角头像方块:桌面悬浮展开详细属性卡,触屏点击开合;点击外部 / Esc 关闭
  function initStatCard() {
    const badge = $('charBadge');
    const card = $('statCard');
    if (!badge || !card) return;
    let hideTimer = null;
    const open = () => { clearTimeout(hideTimer); card.classList.add('open'); };
    const close = () => { clearTimeout(hideTimer); card.classList.remove('open'); };
    const closeSoon = () => { clearTimeout(hideTimer); hideTimer = setTimeout(close, 180); };
    // 悬浮仅限真指针设备:触屏 tap 会先触发 mouseenter 再 click,悬浮+点击同挂会互相打架
    if (window.matchMedia && window.matchMedia('(hover: hover)').matches) {
      badge.addEventListener('mouseenter', open);
      badge.addEventListener('mouseleave', closeSoon);
      card.addEventListener('mouseenter', open);
      card.addEventListener('mouseleave', closeSoon);
    }
    badge.addEventListener('click', (e) => { e.stopPropagation(); card.classList.toggle('open'); });
    card.addEventListener('click', (e) => e.stopPropagation());
    // statlord:点击属性行 → 选数量加点(金币/统计行无 data-stat,不响应)
    card.addEventListener('click', async (e) => {
      const tr = e.target.closest('tr[data-stat]');
      if (!tr || !card.classList.contains('statlord')) return;
      const stat = tr.dataset.stat;
      const label = tr.querySelector('td').textContent.trim();
      const v = window.__game ? window.__game.view() : null;
      const choices = [1, 10, 100, 1000, 10000].map(n => ({ label: '+' + n + ' 点', value: n }));
      const n = await pickModal('💠 加点 · ' + label,
        '自由属性点:' + (v ? fmt(v.freePoints) : '?') + ' · 每点增量随属性不同(攻/防+1,攻速+0.05,暴击+0.5%…)',
        choices);
      if (!n) return;
      const r = await call(() => API.statPoint(API.state.slot, stat, n), label + ' 加点 +' + n);
      applyView(r);
    });
    document.addEventListener('click', close);
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); });
  }

  // ---- 永续天赋悬浮卡:悬浮/点击顶栏「♾️ 被动永续」徽章,展示当前全部永久生效的增益 ----
  // (这些增益不再挤在主画面 buff 条里,信息收进徽章;其他天赋沿用原生 title 提示)
  function initTalentPop() {
    const badge = $('talentBadge');
    if (!badge) return;
    const ETERNAL_META = {
      burn: ['🔥', '燃烧生命', '攻击 ×2'], block: ['🛡️', '格挡反击', '受伤减半'],
      rapid: ['⚡', '急速射击', '攻速 ×3'], surge: ['✨', '魔力涌动', '技能不耗蓝'],
      regen_food: ['🍖', '兔肉补给', '回血 +20'], spd_pot: ['💨', '速度药剂', '攻速 +20%'],
      growth_pot: ['🍵', '修行药剂', '成长 ×2'], iron_pot: ['🛡️', '铁壁药剂', '双防 ×1.5']
    };
    let hideTimer = null;
    const pop = document.createElement('div');
    pop.id = 'talentPop';
    pop.className = 'talent-pop';
    document.querySelector('.topbar').appendChild(pop);
    const build = () => {
      const view = S.view || {};
      const rows = Object.entries(view.buffs || {})
        .filter(([k, v]) => v > 0 && ETERNAL_KEYS.includes(k))
        .map(([k]) => ETERNAL_META[k])
        .filter(Boolean);
      pop.innerHTML = '<div class="tp-title">♾️ 被动永续 <i>所有增益永不消逝</i></div>' +
        (rows.length
          ? rows.map(r => '<div class="tp-row"><span>' + r[0] + '</span><b>' + r[1] + '</b><i>' + r[2] + ' · 永久生效</i></div>').join('')
          : '<div class="tp-empty">暂无生效中的增益——放技能 / 嗑药剂后自动永久常驻</div>');
    };
    const open = () => {
      if ((S.view || {}).talent !== 'eternal') return;
      clearTimeout(hideTimer);
      build();
      pop.classList.add('open');
    };
    const close = () => pop.classList.remove('open');
    const closeSoon = () => { clearTimeout(hideTimer); hideTimer = setTimeout(close, 180); };
    if (window.matchMedia && window.matchMedia('(hover: hover)').matches) {
      badge.addEventListener('mouseenter', open);
      badge.addEventListener('mouseleave', closeSoon);
      pop.addEventListener('mouseenter', open);
      pop.addEventListener('mouseleave', closeSoon);
    }
    badge.addEventListener('click', (e) => {
      e.stopPropagation();
      if ((S.view || {}).talent !== 'eternal') return;
      build();
      pop.classList.toggle('open');
    });
    pop.addEventListener('click', (e) => e.stopPropagation());
    document.addEventListener('click', close);
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); });
  }

  // ================= 启动 =================
  // 设计 §10.3/§14 预留:自动管家脚本接口(window.__game)——挂机脚本/面板从这里读写
  window.__game = {
    view: () => S.view,                                                   // 完整服务端视图
    panel: () => (S.view && S.view.panel) || null,                        // 面板快照
    isGameOver: () => !!(S.view && S.view.dead),
    checkRebirthReady: () => !!(S.view && S.view.clearReady),
    gotoZone: (id) => API.setZone(API.state.slot, id),
    flee: () => API.flee(API.state.slot),
    castSkill: (id) => API.castSkill(API.state.slot, id),
    usePotion: (id) => API.usePotion(API.state.slot, id),
    buyPotion: (id, n) => API.buyPotion(API.state.slot, id, n || 1),
    autoConfig: (cfg) => API.autoConfig(API.state.slot, cfg.hpLine, cfg.mpLine, cfg.autoCure, cfg.fleeLine, cfg.autoBurn, cfg.autoSkill, cfg.autoRebirth, cfg.autoSellKeep, cfg.autoEquipBest),
    autoSell: (keep) => API.autoSell(API.state.slot, keep),
    rebirth: (key) => API.rebirth(API.state.slot, key)
  };
  function boot() {
    initAuth();
    initStatCard();
    initTalentPop();
    // 先挂 401 监听再触发请求:否则启动时 token 已过期,事件发出时无人接收,静默跳回登录页无提示
    window.addEventListener('gongpu:unauthorized', () => {
      stopLoop();
      $('gameRoot').classList.remove('on');
      $('slotMask').style.display = 'none';
      $('authMask').style.display = 'flex';
      toast('登录已过期，请重新登录', 'err');
    });
    if (API.state.token) {
      $('authMask').style.display = 'none';
      showSlots().catch(() => {
        $('authMask').style.display = 'flex';
      });
    }
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible' && S.view && !S.view.dead) {
        S._nextPollAt = 0;   // 回到前台立即补一帧，不等退避到期
        tick();
      }
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
