/**
 * gongpu 新手引导覆盖层（tutorial.js）
 *
 * ══════════════════════════════════════════════════════
 * 【index.html 接入方式】在 autobot.js 之后加一行即可：
 *
 *   <script src="js/tutorial.js"></script>
 *
 * ══════════════════════════════════════════════════════
 *
 * 行为说明：
 * - 首次进入（localStorage 无 gongpu_tutorial_done）弹出 5 步引导；
 * - 每步高亮一个元素并附提示文字，点「下一步」推进，
 *   最后一步按钮为「开始修行」；
 * - 任意时刻按 ESC，或点击高亮区之外（半透明遮罩上）立即结束全部；
 * - 结束（含跳过）写入 localStorage.gongpu_tutorial_done='1'，刷新不再显示；
 * - 调试：控制台执行 window.__tutorial.restart() 清除记录并重看引导。
 *
 * 本文件 IIFE 独立加载，样式通过注入 <style> 实现，不修改 style.css。
 * 全代码 ES5 兼容（无箭头函数 / let / const / 模板字符串）。
 */
(function () {
  'use strict';

  var STORE_KEY = 'gongpu_tutorial_done';
  var PAD = 8;
  var TIP_WIDTH = 300;

  var STEPS = [
    {
      sel: '.combat',
      text: '看到这只木桩了吗？点它出拳，一拳加一点生命上限'
    },
    {
      sel: '.stats-panel',
      text: '这里是你的属性，生命上限越大能去的地方越远'
    },
    {
      sel: '.zones',
      text: '生命上限够了就去新地图，怪物更强掉落更好'
    },
    {
      sel: '#gp-autobot-panel',
      text: '开启自动管家，解放双手挂机'
    },
    {
      sel: '#tabBtnSave',
      text: '记得存档！死了就重开，硬核游戏'
    }
  ];

  var overlay = null;
  var hl = null;
  var tip = null;
  var idx = 0;
  var active = false;
  var pollTimer = null;

  /* ---------- CSS 注入 ---------- */
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
    if (document.getElementById('gt-style')) { return; }
    var st = document.createElement('style');
    st.id = 'gt-style';
    st.textContent = CSS;
    document.head.appendChild(st);
  }

  /* ---------- DOM 构建 ---------- */
  function buildDom() {
    if (overlay) { return; }
    overlay = document.createElement('div');
    overlay.className = 'gt-overlay';
    overlay.addEventListener('click', function (e) {
      if (e.target === overlay) { finish(true); }
    });

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

  /* ---------- 步骤渲染 ---------- */
  function render() {
    if (!active) { return; }
    if (pollTimer) { clearTimeout(pollTimer); pollTimer = null; }

    // 天赋觉醒仪式（z-index 远高于引导）开启期间先隐藏，等其关闭再继续
    if (document.getElementById('talentMask')) {
      hl.style.display = 'none';
      tip.style.display = 'none';
      pollTimer = setTimeout(render, 500);
      return;
    }
    tip.style.display = 'block';

    var step = STEPS[idx];
    var el = step.sel === '#gp-autobot-panel' ? findAutobotPanel() : document.querySelector(step.sel);

    if (!el) {
      pollTimer = setTimeout(render, 250);
      return;
    }

    tip.querySelector('.gt-step').textContent = '第 ' + (idx + 1) + ' / ' + STEPS.length + ' 步';
    tip.querySelector('.gt-text').textContent = step.text;
    tip.querySelector('.gt-btn').textContent = (idx === STEPS.length - 1) ? '开始修行' : '下一步';

    position(el);
  }

  // 管家面板由 autobot.js 异步创建（等 window.__game），这里做一次有限等待
  function findAutobotPanel() {
    return document.getElementById('gp-autobot-panel') || null;
  }

  function position(el) {
    var r = el.getBoundingClientRect();
    hl.style.display = 'block';
    hl.style.left = (r.left - PAD) + 'px';
    hl.style.top = (r.top - PAD) + 'px';
    hl.style.width = (r.width + PAD * 2) + 'px';
    hl.style.height = (r.height + PAD * 2) + 'px';

    var vh = window.innerHeight || document.documentElement.clientHeight;
    var vw = window.innerWidth || document.documentElement.clientWidth;

    // 先摆下面测高度，放不下再摆上面
    tip.style.visibility = 'hidden';
    tip.style.display = 'block';
    var th = tip.offsetHeight;
    var top = r.bottom + 14;
    if (top + th > vh - 12 && r.top - th - 14 > 12) {
      top = r.top - th - 14;
    } else if (top + th > vh - 12) {
      top = Math.max(12, vh - th - 12);
    }

    var left = r.left + r.width / 2 - TIP_WIDTH / 2;
    if (left < 12) { left = 12; }
    if (left + TIP_WIDTH > vw - 12) { left = vw - 12 - TIP_WIDTH; }

    tip.style.top = top + 'px';
    tip.style.left = left + 'px';
    tip.style.visibility = 'visible';
  }

  /* ---------- 流程控制 ---------- */
  function next() {
    if (!active) { return; }
    idx += 1;
    if (idx >= STEPS.length) {
      finish(true);
    } else {
      render();
    }
  }

  function finish(markDone) {
    if (!active) { return; }
    active = false;
    if (pollTimer) { clearTimeout(pollTimer); pollTimer = null; }
    if (markDone) {
      try { localStorage.setItem(STORE_KEY, '1'); } catch (e) {}
    }
    document.removeEventListener('keydown', onKey, true);
    window.removeEventListener('resize', onRelayout, true);
    window.removeEventListener('scroll', onRelayout, true);
    if (overlay && overlay.parentNode) { overlay.parentNode.removeChild(overlay); }
    if (hl && hl.parentNode) { hl.parentNode.removeChild(hl); }
    if (tip && tip.parentNode) { tip.parentNode.removeChild(tip); }
    overlay = hl = tip = null;
  }

  function start() {
    if (active) {
      // 天赋觉醒仪式开启期间轮询等待，关闭后重新渲染（render 内部同样兜底）
      if (document.getElementById('talentMask')) {
        if (pollTimer) { clearTimeout(pollTimer); }
        pollTimer = setTimeout(function () { render(); }, 500);
      } else {
        render();
      }
      return;
    }
    idx = 0;
    active = true;
    injectCss();
    buildDom();
    document.addEventListener('keydown', onKey, true);
    window.addEventListener('resize', onRelayout, true);
    window.addEventListener('scroll', onRelayout, true);
    render();
  }

  function onKey(e) {
    if (e.keyCode === 27 || e.key === 'Escape') {
      e.stopPropagation();
      e.preventDefault();
      finish(true);
    }
  }

  function onRelayout() {
    if (!active) { return; }
    render();
  }

  function isDone() {
    try { return localStorage.getItem(STORE_KEY) === '1'; } catch (e) { return true; }
  }

  /* ---------- 入口 ---------- */
  window.__tutorial = {
    restart: function () {
      try { localStorage.removeItem(STORE_KEY); } catch (e) {}
      finish(false);
      start();
    },
    skip: function () { finish(true); },
    isDone: isDone
  };

  function boot() {
    if (isDone()) { return; }
    // 稍等片刻，让单屏布局与管家面板完成初始化
    setTimeout(start, 400);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
