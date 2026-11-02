/* ============================================================
 * 普攻永久增加生命上限 v2.0 · tutorial.js
 * 新手引导：5 步高亮遮罩（移植自 legacy tutorial.js，适配新版 DOM）
 * - localStorage 记录完成，仅新档首进触发；ESC/跳过可关闭
 * - 调试：控制台 window.__tutorial.restart() 重看
 * ============================================================ */
(function () {
  'use strict';

  var STORE_KEY = 'gongpu_tutorial_done_v2';
  var TIP_WIDTH = 300;

  // 步骤（目标为 v2 版 DOM）
  var STEPS = [
    { sel: '.combat', text: '看到这只木桩了吗？出拳就是修行——每拳永久 +1 生命上限' },
    { sel: '#charBadge', text: '悬浮或点击左上角头像，可随时展开你的详细属性面板' },
    { sel: '.zones', text: '生命上限够了就去新地图：怪物更强，掉落更好' },
    { sel: '#tabBtnQuest', text: '冒险公会委托：接任务杀怪领赏，前期金币主要来源' },
    { sel: '#tabBtnSave', text: '记得存档！死了档就没了——回村口点「立即存档」' }
  ];

  var overlay = null, hl = null, tip = null, idx = 0, active = false, pollTimer = null;

  var CSS =
    '.gt-overlay{position:fixed;top:0;left:0;width:100%;height:100%;' +
    'background:rgba(0,0,0,.75);z-index:99998;}' +
    '.gt-hl{position:fixed;border:2px solid #e8c568;border-radius:6px;' +
    'box-shadow:0 0 14px rgba(232,197,104,.85),inset 0 0 10px rgba(232,197,104,.35);' +
    'z-index:99999;pointer-events:none;' +
    'animation:gt-pulse 1.6s ease-in-out infinite;}' +
    '@keyframes gt-pulse{0%,100%{box-shadow:0 0 8px rgba(232,197,104,.55),inset 0 0 8px rgba(232,197,104,.3);}' +
    '50%{box-shadow:0 0 18px rgba(232,197,104,.95),inset 0 0 14px rgba(232,197,104,.5);}}' +
    '.gt-tip{position:fixed;z-index:100000;width:' + TIP_WIDTH + 'px;' +
    'background:#1c1812;border:1px solid #e8c568;border-radius:8px;' +
    'color:#e8c568;padding:14px 16px;font-size:14px;line-height:1.6;' +
    'box-shadow:0 6px 24px rgba(0,0,0,.65);}' +
    '.gt-tip .gt-step{font-size:12px;opacity:.65;margin-bottom:6px;}' +
    '.gt-tip .gt-text{word-break:break-all;}' +
    '.gt-tip .gt-btns{margin-top:12px;display:flex;justify-content:space-between;align-items:center;}' +
    '.gt-btn{background:#e8c568;color:#1c1812;border:none;border-radius:4px;' +
    'padding:6px 18px;cursor:pointer;font-size:14px;font-weight:bold;}' +
    '.gt-btn:hover{background:#f2d488;}' +
    '.gt-btn:active{transform:translateY(1px);}' +
    '.gt-skip{background:none;border:none;color:#e8c568;opacity:.55;cursor:pointer;' +
    'font-size:12px;text-decoration:underline;padding:0;}' +
    '.gt-skip:hover{opacity:.9;}';

  function injectCss() {
    if (document.getElementById('gt-style')) return;
    var st = document.createElement('style');
    st.id = 'gt-style';
    st.textContent = CSS;
    document.head.appendChild(st);
  }

  function buildDom() {
    if (overlay) return;
    overlay = document.createElement('div');
    overlay.className = 'gt-overlay';
    // 点击遮罩空白不再跳过:新手误触一下就会永久失去全部引导(localStorage 记录完成)
    // 关闭途径保留「跳过」按钮与 ESC
    hl = document.createElement('div');
    hl.className = 'gt-hl';
    hl.style.display = 'none';
    tip = document.createElement('div');
    tip.className = 'gt-tip';
    tip.innerHTML =
      '<div class="gt-step"></div>' +
      '<div class="gt-text"></div>' +
      '<div class="gt-btns">' +
      '<button class="gt-skip" type="button">跳过 (ESC)</button>' +
      '<button class="gt-btn" type="button"></button>' +
      '</div>';
    tip.querySelector('.gt-skip').addEventListener('click', function () { finish(true); });
    tip.querySelector('.gt-btn').addEventListener('click', function () { next(); });
    document.body.appendChild(overlay);
    document.body.appendChild(hl);
    document.body.appendChild(tip);
  }

  function render() {
    if (!active) return;
    if (pollTimer) { clearTimeout(pollTimer); pollTimer = null; }

    // 遮罩类弹窗（天赋/转生/死亡）开启期间先隐藏，等其关闭再继续
    var blocking = ['talentMask', 'rebirthMask', 'deathMask', 'authMask', 'slotMask'];
    for (var i = 0; i < blocking.length; i++) {
      var el = document.getElementById(blocking[i]);
      if (el && el.style.display !== 'none' && el.offsetParent !== null) {
        hl.style.display = 'none';
        tip.style.display = 'none';
        pollTimer = setTimeout(render, 500);
        return;
      }
    }

    if (idx >= STEPS.length) { finish(true); return; }
    var step = STEPS[idx];
    var target = document.querySelector(step.sel);

    // 目标不存在 → 有限次数轮询等待（8 次 ≈3.2s），超限跳过该步
    // （避免 DOM 结构异常时引导死循环卡住玩家）
    if (!target) {
      if (!render._wait) render._wait = 0;
      render._wait++;
      if (render._wait > 8) {
        render._wait = 0;
        idx++;
        render();
        return;
      }
      hl.style.display = 'none';
      tip.style.display = 'none';
      pollTimer = setTimeout(render, 400);
      return;
    }
    render._wait = 0;

    var r = target.getBoundingClientRect();

    // 目标在视口外(移动端单栏布局下属性面板/页面底部元素):
    // 先把它滚进视野中央,滚动后重测定位——直接定位会渲染出屏
    if (r.top < 0 || r.bottom > window.innerHeight || (r.bottom - r.top) === 0) {
      try { target.scrollIntoView({ block: 'center', behavior: 'smooth' }); } catch (e) { /* 旧浏览器忽略 */ }
      pollTimer = setTimeout(render, 450);
      return;
    }

    var PAD = 8;
    hl.style.display = 'block';
    hl.style.left = (r.left - PAD) + 'px';
    hl.style.top = (r.top - PAD) + 'px';
    hl.style.width = (r.width + PAD * 2) + 'px';
    hl.style.height = (r.height + PAD * 2) + 'px';

    tip.style.display = 'block';
    tip.querySelector('.gt-step').textContent = '第 ' + (idx + 1) + ' / ' + STEPS.length + ' 步';
    tip.querySelector('.gt-text').textContent = step.text;
    tip.querySelector('.gt-btn').textContent = (idx === STEPS.length - 1) ? '开始修行' : '下一步';

    // 提示框定位：优先放目标下方，放不下放上方;双向 clamp 到视口内
    var tipH = tip.offsetHeight || 130;
    var top = r.bottom + 12;
    if (top + tipH > window.innerHeight - 10) {
      top = Math.max(10, r.top - tipH - 12);
    }
    top = Math.min(Math.max(10, top), Math.max(10, window.innerHeight - tipH - 10));
    var left = Math.min(
      Math.max(10, r.left + r.width / 2 - TIP_WIDTH / 2),
      Math.max(10, window.innerWidth - TIP_WIDTH - 10)
    );
    tip.style.left = left + 'px';
    tip.style.top = top + 'px';
  }

  function next() {
    idx++;
    if (idx >= STEPS.length) {
      finish(true);
    } else {
      render();
    }
  }

  function finish(save) {
    active = false;
    if (pollTimer) { clearTimeout(pollTimer); pollTimer = null; }
    if (overlay) {
      overlay.remove(); hl.remove(); tip.remove();
      overlay = hl = tip = null;
    }
    if (save) {
      try { localStorage.setItem(STORE_KEY, '1'); } catch (e) { /* 忽略 */ }
    }
    document.removeEventListener('keydown', onKey);
    window.removeEventListener('scroll', onScroll);
  }

  function onScroll() {
    // 页面滚动后目标位置变了:高频重渲(高亮框/提示框跟随),滚动结束由下一帧自然收敛
    if (!active) return;
    clearTimeout(pollTimer);
    pollTimer = setTimeout(render, 120);
  }

  function onKey(e) {
    if (e.key === 'Escape') finish(true);
  }

  function start() {
    if (active) return;
    try {
      if (localStorage.getItem(STORE_KEY)) return;
    } catch (e) { return; }
    active = true;
    idx = 0;
    injectCss();
    buildDom();
    render();
    document.addEventListener('keydown', onKey);
    // 移动端单栏布局:滚动会改变目标位置,高亮/提示跟随重渲(120ms 去抖)
    window.addEventListener('scroll', onScroll, { passive: true });
  }

  window.__tutorial = {
    start: start,
    restart: function () {
      try { localStorage.removeItem(STORE_KEY); } catch (e) { /* 忽略 */ }
      start();
    }
  };
})();
