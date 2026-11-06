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
    { id: 'star_1', name: '初升之星', emoji: '⭐', desc: '任意一件装备升至 1 星' },
    { id: 'star_5', name: '五星神装', emoji: '🌟', desc: '任意一件装备升至满星（5 星）' },
    { id: 'awaken_1', name: '觉醒之光', emoji: '☀️', desc: '任意一件装备觉醒至 1 级' },
    { id: 'awaken_3', name: '完全觉醒', emoji: '🌞', desc: '任意一件装备觉醒至满级（3 级）' },
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
    { id: 'pet_first', name: '宠物伙伴', emoji: '🐾', desc: '首次领养一只宠物' },
    { id: 'pet_25', name: '宠物大师', emoji: '🐾', desc: '任一宠物达到 25 级' },
    { id: 'pet_50', name: '宠物宗师', emoji: '🐾', desc: '任一宠物达到 50 级（满级）' },
    { id: 'pet_all', name: '宠物收藏家', emoji: '🐾', desc: '集齐全部 10 只宠物' },
    { id: 'evolve_1', name: '进化之光', emoji: '🦋', desc: '首次让一只宠物进化' },
    { id: 'evolve_3', name: '进化大师', emoji: '🦋', desc: '累计让宠物进化 3 次' },
    { id: 'evolve_5', name: '万兽化境', emoji: '🦋', desc: '累计进化 5 只宠物' },
    { id: 'awaken_10', name: '觉醒大师', emoji: '☀️', desc: '累计装备觉醒 10 次' },
    { id: 'awaken_25', name: '觉醒宗师', emoji: '🌟', desc: '累计觉醒 25 次' },
    { id: 'inherit_1', name: '薪火相传', emoji: '🔥', desc: '首次完成装备传承' },
    { id: 'sweep_100', name: '横扫千军', emoji: '🧹', desc: '累计扫荡击杀 100 只' },
    { id: 'inherit_25', name: '传承宗师', emoji: '🔥', desc: '累计装备传承 25 次' },
    { id: 'sweep_1000', name: '扫荡千军', emoji: '🧹', desc: '累计扫荡击杀 1000 只' },
    { id: 'mastery_first', name: '地利初成', emoji: '🗺️', desc: '任一区域终身击杀达到 500（精通 +2%）' },
    { id: 'mastery_max', name: '一方地主', emoji: '🏯', desc: '任一区域终身击杀达到 5000（精通满级 +20%）' },
    { id: 'mastery_3', name: '纵横四海', emoji: '🧭', desc: '三个区域都达到精通满级（各 5000 杀）' },
    { id: 'tier3_5', name: '曜石成行', emoji: '💠', desc: '累计融合出 5 颗曜三阶宝石' },
    { id: 'bell_50', name: '碎钟五十', emoji: '🔔', desc: '击杀 50 只带「金钟」词条的怪物' },
    { id: 'guixu_1', name: '归墟初啜', emoji: '🌊', desc: '首次饮下归墟琼浆' },
    { id: 'taichu_1', name: '太初初啜', emoji: '🌠', desc: '首次饮下太初源液' },
    { id: 'phantom_50', name: '影缚者', emoji: '👻', desc: '击杀 50 只带「幻影」词条的怪物' },
    { id: 'thunder_10', name: '引雷者', emoji: '⛈️', desc: '击杀 10 只带「雷霆」词条的怪物' },
    { id: 'mastery_10k', name: '此间主人', emoji: '👑', desc: '任一区域终身击杀达到 10000' },
    { id: 'mastery_20k', name: '山河之主', emoji: '👑', desc: '任一区域终身击杀达到 20000' },
    { id: 'tier3_10', name: '曜石成河', emoji: '💠', desc: '累计融合出 10 颗曜三阶宝石' },
    { id: 'pet_bond_500', name: '万兽之主', emoji: '💞', desc: '全部宠物总等级达到 500' },
    { id: 'eater_50', name: '噬生克星', emoji: '🦟', desc: '击杀 50 只带「噬生」词条的怪物' },
    { id: 'sweep_5000', name: '扫荡无双', emoji: '🧹', desc: '累计扫荡击杀 5000 只' },
    { id: 'immortal_50', name: '不朽终结者', emoji: '💀', desc: '击杀 50 只带「不朽」词条的怪物' },
    { id: 'devour_50', name: '吞噬终结者', emoji: '👹', desc: '击杀 50 只带「吞噬」词条的怪物' },
    { id: 'taixu_1', name: '太虚初啜', emoji: '🕳️', desc: '首次饮下太虚神液' },
    { id: 'taixu_10', name: '太虚猎手', emoji: '🕳️', desc: '击杀 10 只带「太虚」词条的怪物' },
    { id: 'bell_10', name: '破钟者', emoji: '🔔', desc: '击杀 10 只带「金钟」词条的怪物' },
    { id: 'corrode_10', name: '除锈者', emoji: '🧪', desc: '击杀 10 只带「腐蚀」词条的怪物' },
    { id: 'tough_10', name: '碎山者', emoji: '🦏', desc: '击杀 10 只带「韧皮」词条的怪物' },
    { id: 'phantom_10', name: '捉影者', emoji: '👻', desc: '击杀 10 只带「幻影」词条的怪物' },
    { id: 'pet_feed_100', name: '喂食达人', emoji: '🐾', desc: '累计喂食宠物 100 次' },
    { id: 'pet_bond_100', name: '羁绊深厚', emoji: '💞', desc: '全部宠物总等级达到 100' },
    { id: 'pet_bond_250', name: '万兽羁绊', emoji: '💞', desc: '全部宠物总等级达到 250' },
    { id: 'pet_treat_50', name: '零食宗师', emoji: '🍬', desc: '累计喂宠物零食 50 次' },
    { id: 'star_25', name: '升星大师', emoji: '⭐', desc: '累计升星 25 次' },
    { id: 'star_100', name: '升星宗师', emoji: '🌟', desc: '累计升星 100 次' },
    { id: 'treat_200', name: '零食皇帝', emoji: '🍬', desc: '累计喂宠物零食 200 次' },
    { id: 'enchant_500', name: '附魔宗师', emoji: '🖋️', desc: '累计附魔 500 次' },
    { id: 'mirror_10', name: '镜面克星', emoji: '🪞', desc: '击杀 10 只带「镜像」词条的怪物' },
    { id: 'frenzy_10', name: '狂乱克星', emoji: '🌀', desc: '击杀 10 只带「狂乱」词条的怪物' },
    { id: 'immortal_10', name: '不朽克星', emoji: '💀', desc: '击杀 10 只带「不朽」词条的怪物' },
    { id: 'devour_10', name: '吞噬克星', emoji: '👹', desc: '击杀 10 只带「吞噬」词条的怪物' },
    { id: 'phoenix_1', name: '凤凰初啜', emoji: '🦅', desc: '首次饮下凤凰之血' },
    { id: 'weaken_10', name: '虚弱克星', emoji: '🥀', desc: '击杀 10 只带「虚弱」词条的怪物' },
    { id: 'chaos_first', name: '混沌初临', emoji: '🌪️', desc: '首次击杀混沌界的怪物' },
    { id: 'chaos_lord', name: '无序之主', emoji: '👑', desc: '击杀混沌魔君·无序之源' },
    { id: 'guixu_first', name: '归墟初临', emoji: '🌊', desc: '首次击杀归墟界的怪物' },
    { id: 'guixu_lord', name: '无量之主', emoji: '👑', desc: '击杀归墟之主·无量' },
    { id: 'taichu_first', name: '太初初临', emoji: '🌠', desc: '首次击杀太初界的怪物' },
    { id: 'taichu_lord', name: '源祖之主', emoji: '👑', desc: '击杀太初源祖' },
    { id: 'hongmeng_first', name: '鸿蒙初临', emoji: '🌌', desc: '首次击杀鸿蒙界的怪物' },
    { id: 'hongmeng_lord', name: '太初之主', emoji: '👑', desc: '击杀鸿蒙道祖·太初' },
    { id: 'merchant_1m', name: '材料巨鳄', emoji: '💰', desc: '卖材料累计收入 1,000,000 金币' },
    { id: 'feed_500', name: '喂食宗师', emoji: '🐾', desc: '累计喂食宠物 500 次' },
    { id: 'pet_treat_10', name: '零食大户', emoji: '🍬', desc: '累计喂宠物零食 10 次' },
    { id: 'punch_100m', name: '拳倾天下', emoji: '☄️', desc: '累计出拳 1 亿次' },
    { id: 'merchant_500k', name: '材料大亨', emoji: '💰', desc: '卖材料累计收入 500,000 金币' },
    { id: 'greed_learn', name: '招财之道', emoji: '🪙', desc: '学会被动「招财手」' },
    { id: 'burn_50', name: '燃血克星', emoji: '🩸', desc: '击杀 50 只带「燃血」词条的怪物' },
    { id: 'drain_10', name: '噬能克星', emoji: '🔋', desc: '击杀 10 只带「噬能」词条的怪物' },
    { id: 'rend_10', name: '裂空克星', emoji: '✴️', desc: '击杀 10 只带「裂空」词条的怪物' },
    { id: 'monthly_1', name: '月令初成', emoji: '📆', desc: '领满一整月月常任务（累计领取 3 个）' },
    { id: 'monthly_6', name: '半年月令', emoji: '📆', desc: '累计领取 18 个月常任务（六个月全清）' },
    { id: 'fuse_5', name: '融合大师', emoji: '💎', desc: '累计融合出 5 颗璀璨二阶宝石' },
    { id: 'tier3_1', name: '曜石初成', emoji: '💠', desc: '首次融合出曜三阶宝石' },
    { id: 'pierce_learn', name: '破甲之道', emoji: '🏹', desc: '学会被动「破甲专精」' },
    { id: 'flurry_500', name: '连环如雨', emoji: '🤲', desc: '连环掌累计追加命中 500 次' },
    { id: 'star_first', name: '星瞳初开', emoji: '🌌', desc: '首次击杀星界的怪物' },
    { id: 'star_lord', name: '群星主宰', emoji: '👑', desc: '击杀星核主宰·噬光之心' },
    { id: 'void_first', name: '虚空初临', emoji: '🌀', desc: '首次击杀虚空界的怪物' },
    { id: 'void_lord', name: '无相之主', emoji: '👑', desc: '击杀虚空之主·无相天魔' },
    { id: 'weekly_1', name: '周常初成', emoji: '🗓️', desc: '领取 3 个周常任务奖励（整整一周全清）' },
    { id: 'weekly_8', name: '周常老兵', emoji: '🎖️', desc: '累计领取 24 个周常任务奖励（八周全清）' },
    { id: 'gem_collector', name: '宝石收藏家', emoji: '💠', desc: '累计直购 10 颗宝石' },
    { id: 'set_wearer', name: '套装行者', emoji: '👕', desc: '同时穿戴任意一套装的全部 4 件' },
    { id: 'punch_1m', name: '百万连拳', emoji: '🥊', desc: '累计出拳 100 万次' },
    { id: 'kill_100k', name: '十万斩', emoji: '⚔️', desc: '终身击杀 10 万只怪物' },
    { id: 'gold_100m', name: '金山银海', emoji: '🏔️', desc: '持有 1 亿金币' },
    { id: 'craft_200', name: '锻造传说', emoji: '🔥', desc: '累计合成 200 次' },
    { id: 'dismantle_1000', name: '万物分解', emoji: '♻️', desc: '累计分解 1000 件装备' },
    { id: 'gen_30', name: '三十世轮回', emoji: '☸️', desc: '达到第 30 世' },
    { id: 'eclipse_hunter', name: '星蚀猎手', emoji: '🌑', desc: '击杀 10 只带「星蚀」词条的怪物' },
    { id: 'new_meds', name: '尝新者', emoji: '🧪', desc: '喝过鹰目药剂与生机药剂各至少一次' }
  ];

  var byId = {};
  ACH.forEach(function (a) { byId[a.id] = a; });

  /** 第十五批·轮2：进度统一百分比口径——cur/need → 0~100 整数百分比，防除零、防 >100%、负值钳 0。 */
  function pctOf(cur, need) {
    cur = Number(cur) || 0;
    need = Number(need) || 0;
    if (need <= 0 || cur <= 0) return 0;
    return Math.min(100, Math.floor(cur * 100 / need));
  }

  /** 第十五批·轮3：中文大数金额——≥1e8 显示 X.X亿、≥1e4 显示 X.X万，其余原样直显。 */
  function fmtReward(v) {
    v = Number(v) || 0;
    if (v >= 1e8) return (Math.round(v / 1e7) / 10) + '亿';
    if (v >= 1e4) return (Math.round(v / 1e3) / 10) + '万';
    return String(v);
  }

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
    // 搜索关键词（第十二批，app.js 的 #achSearch 写入 window.__achSearchKw）：与筛选筹码叠加
    var achKw = window.__achSearchKw || '';
    // 签名守卫:解锁/已领取状态位不变就不重建(轮询帧每秒都进这里,重建 DOM 纯浪费)
    var sig = 'f=' + achFilter + '|q=' + achKw + '|v15'; // 第十五批：签名带版本，升级后强制重建一次
    ACH.forEach(function (a) {
      sig += (unlocked[a.id] ? '1' : '0') + (claimed[a.id] ? '1' : '0');
      // 进度按 5% 分桶进签名：进度条会动，又不会每帧重建（第十五批·轮2：统一走 pctOf）
      var pr = progress[a.id];
      if (pr) sig += ':' + Math.floor(pctOf(pr[0], pr[1]) / 5);
    });
    if (sig === render._sig) return;
    render._sig = sig;
    var n = 0;
    for (var k in unlocked) { if (unlocked[k]) n++; }
    // 统计头（第十五批·轮6）：渲染区顶部一行「解锁 X/Y · 完成率 Z%」
    var ratePct = pctOf(n, ACH.length);
    var html = ['<div class="effect-group"><div class="effect-group-title">🏅 成就（' +
      n + '/' + ACH.length + '）</div>'];
    html.push('<div class="effect-item ach-stat-head"><span>📊</span><b>解锁 ' + n + '/' + ACH.length +
      ' · 完成率 ' + ratePct + '%</b><i>第十五批统计</i></div>');
    var claimable = ACH.filter(function (a) { return unlocked[a.id] && !claimed[a.id]; });
    if (claimable.length) {
      var sum = claimable.reduce(function (t, a) { return t + (rewards[a.id] || 0); }, 0);
      html.push('<div class="effect-item"><span>🎁</span><b>待领奖励 ' + claimable.length + ' 项</b>' +
        '<i><button class="ach-claim" data-ach-claim-all="1">一键全领 +' + fmtReward(sum) + ' 金</button></i></div>');
    }
    // 里程碑进度（第八批，第九批扩 75 档）：与服务端档位镜像——累计已领 15/30/45/60/75 项各发一次奖金
    var claimedN = ACH.filter(function (a) { return claimed[a.id]; }).length;
    var TIERS = [[15, 10000], [30, 30000], [45, 80000], [60, 200000], [75, 500000], [90, 1000000], [105, 3000000], [120, 5000000], [135, 10000000]];
    var nextTier = TIERS.filter(function (t) { return claimedN < t[0]; })[0];
    html.push('<div class="effect-item"><span>🏆</span><b>成就里程碑：已领 ' + claimedN + ' 项</b><i>' +
      (nextTier ? '下一档 ' + nextTier[0] + ' 项（奖金 +' + fmtReward(nextTier[1]) + ' 金）' : '全部里程碑奖金已达成 🎉') +
      '</i></div>');
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
      // 第十五批·轮5：已解锁整体优先于未解锁；两组内部各按第七批既有细则稳定排序
      var ux = unlocked[x.id] ? 0 : 1;
      var uy = unlocked[y.id] ? 0 : 1;
      if (ux !== uy) return ux - uy;
      // 已解锁组内：可领取(0) < 已领奖(3) 的既有顺序保持
      var d = achState(x) - achState(y);
      if (d !== 0) return d;
      // 未解锁组内：进度百分比降序（第十五批·轮2 统一口径 pctOf）
      if (!unlocked[x.id]) {
        var px = progress[x.id] ? pctOf(progress[x.id][0], progress[x.id][1]) : 0;
        var py = progress[y.id] ? pctOf(progress[y.id][0], progress[y.id][1]) : 0;
        return py - px;
      }
      return 0;
    });
    var shown = ordered.filter(function (a) {
      if (achKw && (a.name + ' ' + a.desc).indexOf(achKw) < 0) return false;
      if (achFilter === 'claim') return achState(a) === 0;
      if (achFilter === 'doing') return achState(a) === 1 || achState(a) === 2;
      if (achFilter === 'done') return achState(a) === 3;
      return true;
    });
    if (!shown.length) {
      // 第十五批·轮7：关键词过滤后为空 → 「无匹配成就」占位，class 沿用 empty-hint
      html.push('<div class="empty-hint">' +
        (achKw ? '无匹配成就「' + achKw + '」' : '暂无该类成就') + '</div>');
    }
    // 第十五批·轮1：分组渲染——shown 按解锁/未解锁拆两组，各带组标题行；条目 DOM 结构不变，仅外层加分组标题
    var unlockedShown = shown.filter(function (a) { return !!unlocked[a.id]; });
    var lockedShown = shown.filter(function (a) { return !unlocked[a.id]; });
    function pushGroup(title, list) {
      if (!list.length) return;
      html.push('<div class="ach-group-title">' + title + ' · ' + list.length + ' 项</div>');
      list.forEach(function (a) { pushEntry(a); });
    }
    function pushEntry(a) {
      var on = !!unlocked[a.id];
      var tail = '';
      if (on && !claimed[a.id]) {
        // 领取按钮由 app.js 的委托监听处理（data-ach-claim）；金额走 fmtReward（第十五批·轮3）
        tail = ' · <button class="ach-claim" data-ach-claim="' + a.id + '">领取 +' +
          fmtReward(rewards[a.id] || 0) + ' 金</button>';
      } else if (on) {
        tail = ' · ✅ 已领奖';
      }
      if (!on && progress[a.id]) {
        var pr = progress[a.id];
        var pct = pctOf(pr[0], pr[1]); // 第十五批·轮2：进度条宽度与文案百分比统一口径
        tail += ' <span class="ach-prog"><span class="ach-prog-fill" style="width:' + pct + '%"></span></span> ' +
          pct + '%（' + pr[0] + '/' + pr[1] + '）';
      }
      html.push('<div class="effect-item ach-entry" style="opacity:' + (on ? 1 : .45) + '">' +
        '<span>' + (on ? a.emoji : '🔒') + '</span><b>' + a.name + '</b>' +
        '<i>' + a.desc + tail + '</i></div>');
    }
    pushGroup('🏅 已解锁', unlockedShown);
    pushGroup('🧭 修行中', lockedShown);
    html.push('</div>');
    container.innerHTML = html.join('');
  }

  /** 取本次新解锁（供 toast 弹窗）。
   *  第十五批·轮4说明：toast 弹出逻辑在 app.js:1047（drain().forEach → toast），
   *  本文件不含弹出代码，且 known 哈希已天然防重（同一 id 只入队一次），
   *  故会话级 Set 去重在 achievements.js 侧无需新增——已跳过，弹层去重口径归 app.js。 */
  function drain() {
    var out = newlyUnlocked.slice();
    newlyUnlocked.length = 0;
    return out;
  }

  /** 第十五批·轮8：外部过滤入口——写入 window.__achSearchKw 并触发重渲染。
   *  与 app.js 的 #achSearch 输入监听共用同一全局变量口径（只写不读其内部状态）。 */
  function setAchFilter(kw) {
    kw = (kw == null) ? '' : String(kw).trim();
    window.__achSearchKw = kw;
    render._sig = null; // 强制下一次 render 重建（签名含关键词，置空即失效）
    var box = document.getElementById('achArea');
    if (box) render(box);
    return kw;
  }

  // 硬约束：check/render/drain 三键名与签名不可变不可删（app.js:1044-1058 调用）；setAchFilter 为新增第四键
  window.__achievements = { check: check, render: render, drain: drain, setAchFilter: setAchFilter };
})();
