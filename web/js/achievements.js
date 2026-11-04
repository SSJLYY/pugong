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
    { id: 'potion_15', name: '神农后人', emoji: '🌿', desc: '使用过 15 种不同的药剂' },
    { id: 'kill_10k', name: '万怪斩', emoji: '🗡️', desc: '终身击杀 1 万只怪物' },
    { id: 'gold_1m', name: '富甲一方', emoji: '🏦', desc: '持有 100 万金币' },
    { id: 'boss_10', name: '屠龙者', emoji: '🐉', desc: '累计击败 Boss 10 次' },
    { id: 'best_hit_1m', name: '开山一击', emoji: '💥', desc: '单拳伤害达 100 万' },
    { id: 'dismantle_100', name: '分解达人', emoji: '🔨', desc: '累计分解 100 件装备' },
    { id: 'craft_25', name: '锻造大师', emoji: '🏭', desc: '累计合成 25 次' },
    { id: 'boss_50', name: '屠龙宗师', emoji: '🐲', desc: '累计击败 Boss 50 次' },
    { id: 'potion_25', name: '神农尝百草', emoji: '🧉', desc: '使用过 25 种不同的药剂' },
    { id: 'boss_100', name: '屠龙神话', emoji: '🐉', desc: '累计击败 Boss 100 次' },
    { id: 'craft_50', name: '锻造宗师', emoji: '🏯', desc: '累计合成 50 次' },
    { id: 'set_4', name: '整装待发', emoji: '🧩', desc: '集齐任意一套装的 4 件' },
    { id: 'set_dual', name: '双套加身', emoji: '🎭', desc: '同时激活两套套装（各 ≥2 件）' },
    { id: 'temper_5', name: '淬火成钢', emoji: '🔨', desc: '任意一件装备淬炼满级' },
    { id: 'reroll_50', name: '洗炼大师', emoji: '🎲', desc: '累计洗炼 50 次' },
    { id: 'abyss_first', name: '渊瞳初开', emoji: '🕳️', desc: '首次击杀深渊裂隙的怪物' },
    { id: 'abyss_lord', name: '深渊主宰', emoji: '👑', desc: '击杀渊底之主·无光君王' },
    { id: 'daily_1', name: '每日事每日毕', emoji: '📅', desc: '首次领取每日任务奖励' },
    { id: 'daily_30', name: '勤勉不辍', emoji: '🗓️', desc: '累计领取 30 次每日任务奖励' },
    { id: 'phase_breaker', name: '破相者', emoji: '🔥', desc: '击破 1 只二阶段 Boss' },
    { id: 'weak_100', name: '弱点猎手', emoji: '🎯', desc: '累计触发 100 次弱点打击' },
    { id: 'market_1', name: '黑市客', emoji: '🌑', desc: '首次在每日黑市购买药剂' },
    { id: 'merchant', name: '商贾之道', emoji: '🧮', desc: '卖材料累计收入 50,000 金币' },
    { id: 'slow_20', name: '迟缓大师', emoji: '🐌', desc: '迟缓咒熟练度达到 20' },
    { id: 'epic_affix', name: '史诗词条', emoji: '🟣', desc: '拥有 3 条史诗词条（已装备与背包合计）' },
    { id: 'gem_first', name: '镶嵌初体验', emoji: '💠', desc: '首次把宝石镶嵌到装备上' },
    { id: 'gem_10', name: '珠光宝气', emoji: '💎', desc: '累计镶嵌 10 颗宝石' },
    { id: 'pierce_learn', name: '破甲之道', emoji: '🏹', desc: '学会被动「破甲专精」' },
    { id: 'flurry_500', name: '连环如雨', emoji: '🤲', desc: '连环掌累计追加命中 500 次' },
    { id: 'star_first', name: '星瞳初开', emoji: '🌌', desc: '首次击杀星界的怪物' },
    { id: 'star_lord', name: '群星主宰', emoji: '👑', desc: '击杀星核主宰·噬光之心' },
    { id: 'weekly_1', name: '周常初成', emoji: '🗓️', desc: '领取 3 个周常任务奖励（整整一周全清）' },
    { id: 'weekly_8', name: '周常老兵', emoji: '🎖️', desc: '累计领取 24 个周常任务奖励（八周全清）' }
  ];

  var byId = {};
  ACH.forEach(function (a) { byId[a.id] = a; });

  // 服务端下发的解锁集合(最近一次 view)
  var unlocked = {};
  // 已领取奖励集合与奖励金额表(服务端 view 下发,金额以服务端 Achievements.rewardOf 为准)
  var claimed = {};
  var rewards = {};
  // 服务端下发的未解锁成就进度（id -> [当前, 目标]）
  var progress = {};
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
    claimed = {};
    (Array.isArray(view.achClaimed) ? view.achClaimed : []).forEach(function (id) { claimed[id] = true; });
    if (view.achRewards) rewards = view.achRewards;
    if (view.achProgress) progress = view.achProgress;

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
  // 筛选（第七批）：all 全部 / claim 可领取 / doing 进行中 / done 已完成，localStorage 持久
  var achFilter = 'all';
  try { achFilter = localStorage.getItem('gongpu_achfilter') || 'all'; } catch (e) { /* 忽略 */ }

  function render(container) {
    // 筛选切换的委托监听只挂一次
    if (!container._achFilterBound) {
      container._achFilterBound = true;
      container.addEventListener('click', function (e) {
        var chip = e.target.closest ? e.target.closest('[data-ach-filter]') : null;
        if (!chip) return;
        achFilter = chip.getAttribute('data-ach-filter');
        try { localStorage.setItem('gongpu_achfilter', achFilter); } catch (err) { /* 忽略 */ }
        render._sig = null;
        render(container);
      });
    }
    // 签名守卫:解锁/已领取状态位不变就不重建(轮询帧每秒都进这里,重建 DOM 纯浪费)
    var sig = 'f=' + achFilter + '|';
    ACH.forEach(function (a) {
      sig += (unlocked[a.id] ? '1' : '0') + (claimed[a.id] ? '1' : '0');
      // 进度按 5% 分桶进签名：进度条会动，又不会每帧重建
      var pr = progress[a.id];
      if (pr) sig += ':' + Math.floor(Math.min(1, pr[0] / Math.max(1, pr[1])) * 20);
    });
    if (sig === render._sig) return;
    render._sig = sig;
    var n = 0;
    for (var k in unlocked) { if (unlocked[k]) n++; }
    var html = ['<div class="effect-group"><div class="effect-group-title">🏅 成就（' +
      n + '/' + ACH.length + '）</div>'];
    var claimable = ACH.filter(function (a) { return unlocked[a.id] && !claimed[a.id]; });
    if (claimable.length) {
      var sum = claimable.reduce(function (t, a) { return t + (rewards[a.id] || 0); }, 0);
      html.push('<div class="effect-item"><span>🎁</span><b>待领奖励 ' + claimable.length + ' 项</b>' +
        '<i><button class="ach-claim" data-ach-claim-all="1">一键全领 +' + sum + ' 金</button></i></div>');
    }
    // 排序与筛选（第七批）：可领取置顶 → 进行中（进度高优先）→ 未解锁 → 已领奖垫底；筛选只影响列表子集
    function achState(a) {
      if (unlocked[a.id] && !claimed[a.id]) return 0;
      if (!unlocked[a.id] && progress[a.id]) return 1;
      if (!unlocked[a.id]) return 2;
      return 3;
    }
    var doingN = ACH.filter(function (a) { return !unlocked[a.id] && progress[a.id]; }).length;
    var doneN = ACH.filter(function (a) { return unlocked[a.id] && claimed[a.id]; }).length;
    var chips = [['all', '全部 ' + ACH.length], ['claim', '🎁 可领取 ' + claimable.length],
      ['doing', '🔄 进行中 ' + doingN], ['done', '✅ 已完成 ' + doneN]];
    html.push('<div class="ach-filters">' + chips.map(function (c) {
      return '<button class="ach-chip' + (achFilter === c[0] ? ' on' : '') + '" data-ach-filter="' + c[0] + '">' + c[1] + '</button>';
    }).join('') + '</div>');
    var ordered = ACH.slice().sort(function (x, y) {
      var d = achState(x) - achState(y);
      if (d !== 0) return d;
      if (achState(x) === 1) {
        var px = progress[x.id] ? progress[x.id][0] / Math.max(1, progress[x.id][1]) : 0;
        var py = progress[y.id] ? progress[y.id][0] / Math.max(1, progress[y.id][1]) : 0;
        return py - px;
      }
      return 0;
    });
    var shown = ordered.filter(function (a) {
      if (achFilter === 'claim') return achState(a) === 0;
      if (achFilter === 'doing') return achState(a) === 1 || achState(a) === 2;
      if (achFilter === 'done') return achState(a) === 3;
      return true;
    });
    if (!shown.length) {
      html.push('<div class="effect-item"><span>·</span><b>暂无该类成就</b><i></i></div>');
    }
    shown.forEach(function (a) {
      var on = !!unlocked[a.id];
      var tail = '';
      if (on && !claimed[a.id]) {
        // 领取按钮由 app.js 的委托监听处理（data-ach-claim）
        tail = ' · <button class="ach-claim" data-ach-claim="' + a.id + '">领取 +' +
          (rewards[a.id] || 0) + ' 金</button>';
      } else if (on) {
        tail = ' · ✅ 已领奖';
      }
      if (!on && progress[a.id]) {
        var pr = progress[a.id];
        var pct = Math.min(100, Math.floor(pr[0] * 100 / Math.max(1, pr[1])));
        tail += ' <span class="ach-prog"><span class="ach-prog-fill" style="width:' + pct + '%"></span></span> ' +
          pr[0] + '/' + pr[1];
      }
      html.push('<div class="effect-item" style="opacity:' + (on ? 1 : .45) + '">' +
        '<span>' + (on ? a.emoji : '🔒') + '</span><b>' + a.name + '</b>' +
        '<i>' + a.desc + tail + '</i></div>');
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
