/* ============================================================
 * 普攻永久增加生命上限 v2.0 · achievements.js
 * 成就系统（服务端权威渲染层）：
 *   - 解锁集合由服务端评估并持久化（state_json.achievements），换设备/清缓存不丢
 *   - 本文件只保留 id/名称/图标/描述表用于渲染,首帧把服务端已有解锁静默建档,
 *     之后每次轮询 diff 出新解锁推入 drain() 队列供 toast
 *   - id 必须与服务端 Achievements.java 一致(有单测锁死)
 * ============================================================ */
(function () {
  'use strict';

  var ACH = [
    { id: 'first_punch', name: '出拳', emoji: '👊', desc: '累计出拳 1 次' },
    { id: 'punch_10k', name: '万拳', emoji: '💯', desc: '累计出拳 1 万次' },
    { id: 'first_kill', name: '首杀', emoji: '💀', desc: '击杀 1 只怪物' },
    { id: 'kill_100', name: '百人斩', emoji: '⚔️', desc: '击杀 100 只怪物' },
    { id: 'hp_5000', name: '皮糙肉厚', emoji: '❤️', desc: '生命上限达 5000' },
    { id: 'hp_1m', name: '百万雄师', emoji: '🫀', desc: '生命上限达 100 万' },
    { id: 'gold_10k', name: '小有身家', emoji: '💰', desc: '持有 1 万金币' },
    { id: 'zone_volcano', name: '踏足火山', emoji: '🌋', desc: '到达火山' },
    { id: 'lived_1h', name: '熬过一时辰', emoji: '⏱️', desc: '单世存活 1 小时' },
    { id: 'first_death', name: '第一次死亡', emoji: '⚰️', desc: '死亡 1 次（墓园见）' },
    { id: 'equip_5', name: '收集家', emoji: '🎒', desc: '背包与装备合计 5 件' },
    { id: 'wear_epic', name: '史诗加身', emoji: '🟣', desc: '穿上一件史诗装备' },
    { id: 'boss_win', name: '以下克上', emoji: '👑', desc: '击败任意区域 Boss' },
    { id: 'gen_1', name: '转世者', emoji: '🌟', desc: '完成一次飞升转生' },
    { id: 'gen_12', name: '万世圆满', emoji: '🧬', desc: '达到第 12 世（集齐 13 条赋能）' },
    { id: 'talent', name: '觉醒者', emoji: '✨', desc: '觉醒一项天赋' },
    { id: 'bless_3', name: '三线成长', emoji: '🧬', desc: '同时拥有 3 条赋能' },
    { id: 'duelist', name: '切磋者', emoji: '⚔️', desc: '觉醒天赋并击杀 10 只怪物（切磋入口在江湖页）' },
    { id: 'codex_10', name: '图鉴·十种', emoji: '📖', desc: '图鉴记录 10 种怪物' },
    { id: 'codex_all', name: '万物图鉴', emoji: '📚', desc: '集齐全部怪物图鉴（含各区域 Boss）' },
    { id: 'first_craft', name: '初试锻造', emoji: '⚒️', desc: '完成第一次图纸合成' },
    { id: 'first_enchant', name: '初试附魔', emoji: '✨', desc: '完成第一次装备附魔' },
    { id: 'potion_5', name: '品鉴五味', emoji: '🧪', desc: '使用过 5 种不同的药剂' },
    { id: 'potion_15', name: '神农后人', emoji: '🌿', desc: '使用过 15 种不同的药剂' }
  ];

  var byId = {};
  ACH.forEach(function (a) { byId[a.id] = a; });

  // 服务端下发的解锁集合(最近一次 view)
  var unlocked = {};
  // 已 toast 过/已建档的 id(内存态,防重复弹)
  var known = {};
  var baselined = false;
  var newlyUnlocked = [];

  /** 每次轮询调用:同步服务端解锁集合,diff 出新解锁。首次调用静默建档。 */
  function check(view) {
    if (!view || !view.panel) return;
    var serverSet = Array.isArray(view.achievements) ? view.achievements : [];
    unlocked = {};
    serverSet.forEach(function (id) { unlocked[id] = true; });

    serverSet.forEach(function (id) {
      if (known[id]) return;
      known[id] = true;
      // 首帧把服务端已有解锁静默建档(不弹"新解锁");之后新增的才 toast
      if (baselined && byId[id]) {
        newlyUnlocked.push(byId[id]);
      }
    });
    baselined = true;
  }

  /** 渲染成就页（挂在角色页签，由 app.js 每轮询帧调用）。 */
  function render(container) {
    // 签名守卫:解锁状态位不变就不重建(轮询帧每秒都进这里,重建 18 行 DOM 纯浪费)
    var sig = '';
    ACH.forEach(function (a) { sig += unlocked[a.id] ? '1' : '0'; });
    if (sig === render._sig) return;
    render._sig = sig;
    var n = 0;
    for (var k in unlocked) { if (unlocked[k]) n++; }
    var html = ['<div class="effect-group"><div class="effect-group-title">🏅 成就（' +
      n + '/' + ACH.length + '）</div>'];
    ACH.forEach(function (a) {
      var on = !!unlocked[a.id];
      html.push('<div class="effect-item" style="opacity:' + (on ? 1 : .45) + '">' +
        '<span>' + (on ? a.emoji : '🔒') + '</span><b>' + a.name + '</b>' +
        '<i>' + a.desc + (on ? ' · ✅' : '') + '</i></div>');
    });
    html.push('</div>');
    container.innerHTML = html.join('');
  }

  /** 取本次新解锁（供 toast 弹窗）。 */
  function drain() {
    var out = newlyUnlocked.slice();
    newlyUnlocked.length = 0;
    return out;
  }

  window.__achievements = { check: check, render: render, drain: drain };
})();
