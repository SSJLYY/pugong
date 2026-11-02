/* ============================================================
 * 《普攻永久增加生命上限》 audio.js
 * 音效系统：WebAudio API 纯振荡器合成，零外部素材
 * IIFE 独立文件，ES5 兼容，暴露 window.__audio
 *
 * ------------------------------------------------------------
 * 【index.html 接入方式】（在 autobot.js 之前加一行即可）
 *   <script src="js/audio.js?v=20261029-audio1"></script>
 * 本文件自初始化：音量按钮 / 样式 / AudioContext 惰性创建，
 * 无需额外调用。修改 game.js 之外的任何文件均非本文件所为。
 *
 * ------------------------------------------------------------
 * 【主线埋点建议】（在 game.js / ui.js 对应函数体内加一行
 *   `if (window.__audio) __audio.play('xxx');` 即可，均已判空安全）
 *
 *  game.js:
 *   - 出拳   settlePunches(n)         → play('punch')   高频，内部已节流
 *   - 击杀   damageMob() 内击杀分支    → play('kill')    （target.curHP.lte(0) 处）
 *   - 掉落   tryDropEquip()            → play('drop')    （装备实际入背包的三个分支）
 *   - 死亡   onPlayerDeath()           → play('death')
 *   - 飞升   rebirth()                 → play('levelup')
 *   - 买药   buyPotion()               → play('buy')
 *   - 卖装   sellItem() / autoSellBag() → play('sell')
 *
 *  ui.js（由 UI 层调用，game.js 不感知）:
 *   - 觉醒抽卡仪式完成写入 S.talent 处 → play('levelup')
 *   - 主动技能施放成功 castSkill 返回 true 处 → play('click')
 *   - 各按钮点击处（或给 document 做 click 委托）→ play('click')
 * ============================================================ */
(function () {
  'use strict';

  var LS_VOLUME = 'gongpu_audio_volume';
  var LS_MUTED = 'gongpu_audio_muted';

  // ---------------- 状态（localStorage 持久化） ----------------
  function readNum(key, dflt) {
    try {
      var v = parseFloat(localStorage.getItem(key));
      return isNaN(v) ? dflt : Math.min(1, Math.max(0, v));
    } catch (e) { return dflt; }
  }
  function readBool(key, dflt) {
    try {
      var v = localStorage.getItem(key);
      return v === null ? dflt : (v === '1');
    } catch (e) { return dflt; }
  }
  function writeLS(key, val) {
    try { localStorage.setItem(key, val); } catch (e) { /* 隐私模式等场景忽略 */ }
  }

  var volume = readNum(LS_VOLUME, 0.5);
  var muted = readBool(LS_MUTED, false);

  // ---------------- AudioContext（首次用户交互后创建） ----------------
  var ctx = null;
  var master = null;

  function ensureCtx() {
    if (ctx) {
      if (ctx.state === 'suspended' && ctx.resume) ctx.resume();
      return !!master;
    }
    var AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return false;                       // 老浏览器无 WebAudio
    ctx = new AC();
    master = ctx.createGain();
    master.gain.value = muted ? 0 : volume;
    master.connect(ctx.destination);
    return true;
  }

  // 自动播放策略：挂全局手势监听，触发一次即创建并解绑
  function armOnce() {
    if (!ensureCtx()) return;
    for (var i = 0; i < GESTURES.length; i++) {
      document.removeEventListener(GESTURES[i], armOnce, true);
    }
  }
  var GESTURES = ['pointerdown', 'touchstart', 'keydown'];
  for (var gi = 0; gi < GESTURES.length; gi++) {
    document.addEventListener(GESTURES[gi], armOnce, true);
  }

  // ---------------- 合成器原语 ----------------
  // 单音：type/freq 或 [freqFrom,freqTo] 滑音；t0 相对偏移；dur/gain
  function tone(out, t0, type, freq, dur, gain, slideTo) {
    var t = ctx.currentTime + t0;
    var o = ctx.createOscillator();
    var g = ctx.createGain();
    o.type = type;
    o.frequency.setValueAtTime(freq, t);
    if (slideTo) o.frequency.exponentialRampToValueAtTime(slideTo, t + dur);
    g.gain.setValueAtTime(gain, t);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g); g.connect(out);
    o.start(t); o.stop(t + dur + 0.05);
  }

  // ---------------- 音效清单（全部振荡器合成） ----------------
  var SFX = {
    // 出拳：短促低频敲击（随机 ±10% 音高，避免机械感）
    punch: function (out) {
      var f = 130 + Math.random() * 25;
      tone(out, 0, 'sine', f, 0.09, 0.5, f * 0.45);
      tone(out, 0, 'triangle', f * 2.02, 0.05, 0.22, f * 0.9);
    },
    // 击杀：上扬琶音 C5 E5 G5
    kill: function (out) {
      tone(out, 0,    'square', 523.25, 0.10, 0.20);
      tone(out, 0.07, 'square', 659.25, 0.10, 0.20);
      tone(out, 0.14, 'square', 783.99, 0.22, 0.24);
    },
    // 装备掉落：清脆叮声 G6 + C7 泛音
    drop: function (out) {
      tone(out, 0,    'sine', 1567.98, 0.16, 0.28);
      tone(out, 0.02, 'sine', 2093.00, 0.24, 0.16);
    },
    // 天赋觉醒/飞升：华丽上行音阶 C5 E5 G5 C6 E6 G6
    levelup: function (out) {
      var seq = [523.25, 659.25, 783.99, 1046.50, 1318.51, 1567.98];
      for (var i = 0; i < seq.length; i++) {
        tone(out, i * 0.09, 'triangle', seq[i], 0.26, 0.22);
        tone(out, i * 0.09, 'sine', seq[i] * 2, 0.18, 0.08);
      }
    },
    // 死亡：低沉下行音 A3 → E3 → A2
    death: function (out) {
      tone(out, 0,    'sawtooth', 220.00, 0.30, 0.20, 164.81);
      tone(out, 0.28, 'sawtooth', 164.81, 0.30, 0.18, 110.00);
      tone(out, 0.56, 'sawtooth', 110.00, 0.70, 0.16, 55.00);
      tone(out, 0,    'sine', 55, 1.2, 0.22, 38);
    },
    // 买：金币叮当 B5 → E6
    buy: function (out) {
      tone(out, 0,    'sine', 987.77, 0.10, 0.26);
      tone(out, 0.07, 'sine', 1318.51, 0.18, 0.26);
    },
    // 卖：金币叮当（低八度回弹）
    sell: function (out) {
      tone(out, 0,    'sine', 1318.51, 0.10, 0.26);
      tone(out, 0.07, 'sine', 987.77, 0.18, 0.26);
    },
    // UI 点击：极短 tick
    click: function (out) {
      tone(out, 0, 'square', 2400, 0.03, 0.12);
    }
  };

  // 高频音效节流（毫秒）：出拳随攻速成长会爆炸，必须限频
  var THROTTLE = { punch: 220, click: 60 };
  var lastPlay = {};

  // ---------------- 对外接口 ----------------
  function play(name) {
    var fn = SFX[name];
    if (!fn || muted) return;
    if (!ensureCtx()) return;
    var gap = THROTTLE[name] || 0;
    if (gap > 0) {
      var now = Date.now();
      if (lastPlay[name] && now - lastPlay[name] < gap) return;
      lastPlay[name] = now;
    }
    try { fn(master); } catch (e) { /* 单音失败不影响游戏 */ }
  }

  function setVolume(v) {
    volume = Math.min(1, Math.max(0, Number(v) || 0));
    writeLS(LS_VOLUME, String(volume));
    if (master && !muted) master.gain.value = volume;
  }

  function setMuted(m) {
    muted = !!m;
    writeLS(LS_MUTED, muted ? '1' : '0');
    if (master) master.gain.value = muted ? 0 : volume;
    updateBtn();
  }

  function toggle() { setMuted(!muted); return !muted; }

  // ---------------- 右下角音量按钮（管家面板上方，暗黑主题） ----------------
  var btn = null;

  function updateBtn() {
    if (!btn) return;
    btn.textContent = muted ? '🔇' : '🔊';
    btn.title = muted ? '已静音（点击开声音）' : '声音开（点击静音）';
    btn.setAttribute('aria-pressed', muted ? 'true' : 'false');
  }

  function injectStyle() {
    if (document.getElementById('gp-audio-style')) return;
    var st = document.createElement('style');
    st.id = 'gp-audio-style';
    st.textContent =
      '#gp-audio-btn{position:fixed;right:12px;bottom:56px;z-index:2147483646;' +
      'width:36px;height:36px;border-radius:50%;border:1px solid #666;' +
      'background:rgba(20,20,20,.92);color:#ddd;font-size:17px;line-height:1;' +
      'cursor:pointer;display:flex;align-items:center;justify-content:center;' +
      'box-shadow:0 4px 16px rgba(0,0,0,.5);user-select:none;padding:0;' +
      'transition:transform .12s ease,border-color .12s ease}' +
      '#gp-audio-btn:hover{border-color:#43e97b;transform:scale(1.08)}' +
      '#gp-audio-btn:active{transform:scale(.94)}';
    (document.head || document.documentElement).appendChild(st);
  }

  function addBtn() {
    if (document.getElementById('gp-audio-btn')) return;
    injectStyle();
    btn = document.createElement('button');
    btn.id = 'gp-audio-btn';
    btn.type = 'button';
    btn.setAttribute('aria-label', '切换游戏音效');
    btn.addEventListener('click', function () {
      toggle();
      if (!muted) play('click');
    });
    (document.body || document.documentElement).appendChild(btn);
    updateBtn();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', addBtn);
  } else {
    addBtn();
  }

  // ---------------- 导出 ----------------
  window.__audio = {
    play: play,
    setVolume: setVolume,
    getVolume: function () { return volume; },
    setMuted: setMuted,
    isMuted: function () { return muted; },
    toggle: toggle
  };
})();
