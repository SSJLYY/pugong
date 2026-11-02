/* ============================================================
 * 《普攻永久增加生命上限》 config.js
 * 常量表：赋能 / 中文大数单位 / 区域怪物 / 初始属性 / 装备 / 技能 / 药剂
 * 依据 DESIGN.md v0.4（M2 战斗与地图扩展）
 * ============================================================ */

// ---------- 赋能体系（13 条，HP 先天自带） ----------
// step: 基础步长/拳；强化纪元中步长 = step × (1 + 强化次数)
const BLESS_ORDER = ['HP','PATK','MATK','PDEF','MDEF','ASPD','RNG','SPD','CRIT','CRITD','ACC','EVA','REGEN'];
const BLESS_STEP = {
  HP:    1,      // +1 生命上限/拳
  PATK:  1, MATK: 1, PDEF: 1, MDEF: 1,
  ASPD:  0.01,   // +0.01 攻速/拳（自我繁殖奇点）
  RNG:   0.001,  // +0.001 攻击距离/拳
  SPD:   1,      // +1 移速/拳
  CRIT:  0.0001, // +0.01% 暴击率/拳
  CRITD: 0.001,  // +0.1% 暴击伤害/拳
  ACC:   0.0001, // +0.01% 命中/拳
  EVA:   0.0001, // +0.01% 闪避/拳
  REGEN: 1       // +1 生命回复/拳
};
// 百分比属性封顶（强化到顶后转生界面置灰）
const BLESS_CAP = { CRIT: 1, ACC: 1, EVA: 0.95 }; // EVA 封顶 95%

// ---------- 中文大数单位 ----------
const CN_UNITS = [
  ['',    1e0],
  ['万',  1e4], ['亿',  1e8], ['兆',  1e12], ['京',  1e16],
  ['垓',  1e20], ['秭',  1e24], ['穰',  1e28], ['沟',  1e32],
  ['涧',  1e36], ['正',  1e40], ['载',  1e44]
];

// ---------- 初始属性（第 0 世出生面板） ----------
const INIT_LIFE = { maxHP: 100, curHP: 100, MP: 80, curMP: 80, PATK: 2, MATK: 0, PDEF: 2, MDEF: 0, REGEN: 2, MREGEN: 2 };
const INIT_FIXED = { ASPD: 2.0, RNG: 1, SPD: 100, CRIT: 0.05, CRITD: 1.5, ACC: 0.95, EVA: 0 };

// ---------- 技能 ----------
const SKILLS = {
  burn: { name: '燃烧生命', emoji: '🔥', mp: 0, cd: 300, dur: 300,
          desc: '燃烧当前生命 25%（不伤生命上限），300 秒内攻击翻倍（CD 一到可续）' }
};

// ---------- 区域准入门槛（属性说话，不用等级） ----------
const ZONE_REQ = {
  village: null,
  plain:   { maxHP: 500 },
  forest:  { maxHP: 3000, PDEF: 5 },
  mine:    { maxHP: 12000 },
  swamp:   { maxHP: 40000, REGEN: 50 },
  snow:    { maxHP: 100000, MDEF: 30 },
  volcano: { maxHP: 300000, PDEF: 80, MDEF: 80 }
};

// ---------- 区域与怪物（无等级，只有属性表） ----------
const ZONES = {
  village: {
    name: '村口', emoji: '🏡', safe: true,
    desc: '安全区 · 木桩区 · 自动存档点',
    mobs: [
      { id:'dummy', name:'木桩', emoji:'🪵', maxHP: Infinity, PATK:0, MATK:0, PDEF:0, MDEF:0, ASPD:0, RNG:0, SPD:0, gold:0,
        desc:'无限血挨打桩，出拳就是修行', tags:[] }
    ]
  },
  plain: {
    name: '平原', emoji: '🌾', safe: false,
    desc: '建议生命 ≥ 500',
    mobs: [
      { id:'rabbit', name:'野兔', emoji:'🐇', maxHP:120, PATK:1, MATK:0, PDEF:0, MDEF:0, ASPD:1.0, RNG:1, SPD:130, gold:3,
        desc:'攻速快移速快，小心被风筝', tags:[] },
      { id:'boar', name:'野猪', emoji:'🐗', maxHP:500, PATK:6, MATK:0, PDEF:2, MDEF:0, ASPD:0.8, RNG:1, SPD:80, gold:12,
        desc:'皮糙肉厚，新手大敌', tags:[] }
    ]
  },
  forest: {
    name: '森林', emoji: '🌲', safe: false,
    desc: '建议生命 ≥ 3000 / 物防 ≥ 5',
    mobs: [
      { id:'wolf', name:'野狼', emoji:'🐺', maxHP:2000, PATK:15, MATK:0, PDEF:3, MDEF:0, ASPD:1.5, RNG:1, SPD:140, gold:25,
        desc:'攻速快，移速快，逃跑考验', tags:[] },
      { id:'bandit_bow', name:'山贼弓手', emoji:'🏹', maxHP:4000, PATK:25, MATK:0, PDEF:5, MDEF:0, ASPD:1.0, RNG:6, SPD:90, gold:40,
        desc:'手长 6 步，考验你的移速和射程', tags:[] },
      { id:'bandit', name:'山贼', emoji:'🗡️', maxHP:5000, PATK:30, MATK:0, PDEF:8, MDEF:0, ASPD:1.0, RNG:1, SPD:100, gold:50,
        desc:'硬茬子，建议堆防御', tags:[] }
    ]
  },
  mine: {
    name: '矿洞', emoji: '⛏️', safe: false,
    desc: '建议生命 ≥ 12000 / 有魔伤手段更佳',
    mobs: [
      { id:'golem', name:'石魔像', emoji:'🗿', maxHP:15000, PATK:25, MATK:0, PDEF:50, MDEF:0, ASPD:0.5, RNG:1, SPD:40, gold:80,
        desc:'物防 50 魔防 0，逼你搞附魔/魔攻', tags:[] },
      { id:'bats', name:'蝙蝠群', emoji:'🦇', maxHP:3000, PATK:12, MATK:0, PDEF:2, MDEF:0, ASPD:2.5, RNG:2, SPD:160, gold:35,
        desc:'攻速极快，检验命中', tags:[] }
    ]
  },
  swamp: {
    name: '沼泽', emoji: '🐸', safe: false,
    desc: '建议生命 ≥ 40000 / 回血 ≥ 50',
    mobs: [
      { id:'toad', name:'毒蟾', emoji:'🐸', maxHP:30000, PATK:40, MATK:20, PDEF:10, MDEF:10, ASPD:0.8, RNG:3, SPD:70, gold:120,
        desc:'双伤混合 + 毒伤，吃你的 REGEN', tags:['poison'] }
    ]
  },
  snow: {
    name: '雪山', emoji: '🏔️', safe: false,
    desc: '建议生命 ≥ 100000 / 魔防 ≥ 30',
    mobs: [
      { id:'snowman', name:'雪人', emoji:'⛄', maxHP:80000, PATK:120, MATK:0, PDEF:30, MDEF:20, ASPD:0.6, RNG:2, SPD:60, gold:200,
        desc:'物攻凶猛，掉落冰晶和稀有装备', tags:[] },
      { id:'icemage', name:'冰法', emoji:'🧙', maxHP:60000, PATK:20, MATK:180, PDEF:10, MDEF:80, ASPD:0.8, RNG:7, SPD:80, gold:180,
        desc:'魔伤 180 射程 7 步，检验你的魔防和射程', tags:[] }
    ]
  },
  volcano: {
    name: '火山', emoji: '🌋', safe: false,
    desc: '建议生命 ≥ 300000 / 双防成型（第0世毕业区）',
    mobs: [
      { id:'lava', name:'熔岩兽', emoji:'🔥', maxHP:200000, PATK:250, MATK:80, PDEF:50, MDEF:50, ASPD:1.0, RNG:2, SPD:90, gold:300,
        desc:'双伤混合，掉落火晶和顶级图纸', tags:['burn'] },
      { id:'inferno', name:'炎魔', emoji:'😈', maxHP:500000, PATK:300, MATK:400, PDEF:80, MDEF:80, ASPD:1.2, RNG:5, SPD:110, gold:500,
        desc:'第0世天花板，魔伤 400 射程 5', tags:['burn'] }
    ]
  }
};

// ============================================================
// Boss 挑战（每区域一只，高血高攻 + 沉默克制被动永续）
// ============================================================
// silenceCd: 沉默技能冷却（秒），silenceDur: 沉默持续（秒）
// 掉落：必掉 quality 2 紫装，25% 概率升级 quality 3 史诗
const ZONE_BOSSES = {
  plain:   { id:'boss_boar',   name:'野猪王',   emoji:'🐗', maxHP:5000,    PATK:15,  MATK:0,   PDEF:5,  MDEF:0,  ASPD:0.7, RNG:2, SPD:60,  gold:100,  silenceCd:14, silenceDur:30, desc:'平原之王，咆哮令你的被动失效', tags:[] },
  forest:  { id:'boss_wolf',   name:'狼王',     emoji:'🐺', maxHP:30000,   PATK:50,  MATK:0,   PDEF:12, MDEF:0,  ASPD:1.2, RNG:1, SPD:150, gold:400,  silenceCd:16, silenceDur:30, desc:'狼群之首，啸声穿透灵魂', tags:[] },
  mine:    { id:'boss_golem',  name:'熔核魔像', emoji:'🗿', maxHP:120000,  PATK:80,  MATK:40,  PDEF:60, MDEF:20, ASPD:0.5, RNG:1, SPD:35,  gold:1200, silenceCd:18, silenceDur:30, desc:'岩浆核心，沉默领域常开', tags:['burn'] },
  swamp:   { id:'boss_toad',   name:'毒蟾女王', emoji:'🐸', maxHP:250000,  PATK:110, MATK:60,  PDEF:20, MDEF:20, ASPD:0.9, RNG:3, SPD:65,  gold:2500, silenceCd:15, silenceDur:30, desc:'剧毒之源，沉默毒雾弥漫', tags:['poison'] },
  snow:    { id:'boss_yeti',   name:'冰霜巨魔', emoji:'🧊', maxHP:600000,  PATK:250, MATK:200, PDEF:50, MDEF:60, ASPD:0.8, RNG:3, SPD:55,  gold:6000, silenceCd:17, silenceDur:30, desc:'极寒之主，冻结一切增益', tags:[] },
  volcano: { id:'boss_ifrit',  name:'炎狱魔王', emoji:'👹', maxHP:2000000, PATK:500, MATK:600, PDEF:100,MDEF:100,ASPD:1.1, RNG:4, SPD:100, gold:15000,silenceCd:14, silenceDur:30, desc:'炎狱之主，沉默之焰焚尽增益', tags:['burn'] },
};

// ---------- 伤害公式参数 ----------
const DMG = { variance: 0.10, defFactor: 0.8 };

// ---------- 逃跑（移速差挂钩，M2 升级） ----------
const FLEE = {
  // 逃跑成功率 = min(0.95, 0.5 + (玩家SPD - 怪SPD) / 200)；失败扣 30% 血强制回村
  base: 0.5, spdFactor: 200, cap: 0.95, failCost: 0.30
};

// ---------- 常量 ----------
const TICK_MS = 100;
const SAVE_KEYS = { slots: ['gongpu_slot_1','gongpu_slot_2','gongpu_slot_3'], meta: 'gongpu_meta', graveyard: 'gongpu_graveyard' };
const MAX_OFFLINE_HOURS = 8;     // M5 离线结算用
const REBIRTH_ASPD = '1e44';     // 通关线：一秒 1 载次

// ============================================================
// 装备系统（M3：底子 + 随机词条，暗黑式）
// ============================================================

// 部位（10 槽，武器与弓互斥）
const EQUIP_SLOTS = ['weapon','offhand','bow','head','chest','legs','boots','ring1','ring2','necklace'];

// 武器谱系（含弓箭）
const WEAPONS = [
  { name:'新手木棍', emoji:'🥢', slot:'weapon', base:{PATK:2,RNG:1}, tier:0, desc:'白给' },
  { name:'兔骨匕首', emoji:'🦴', slot:'weapon', base:{PATK:2,ASPD:0.15,RNG:1}, tier:0, desc:'攻速启蒙：拳越快，命越长' },
  { name:'铁剑',     emoji:'🗡️', slot:'weapon', base:{PATK:8,RNG:1}, tier:1, desc:'标准' },
  { name:'匕首',     emoji:'🔪', slot:'weapon', base:{PATK:4,ASPD:0.3,RNG:1}, tier:1, desc:'攻速流' },
  { name:'轻弩',     emoji:'🎯', slot:'bow',    base:{PATK:4,ASPD:-0.05,RNG:4}, tier:1, desc:'新手远程，手长一寸' },
  { name:'长枪',     emoji:'🔱', slot:'weapon', base:{PATK:12,RNG:3}, tier:2, desc:'近战手长者' },
  { name:'双手大剑', emoji:'⚔️', slot:'weapon', base:{PATK:30,ASPD:-0.3,RNG:2}, tier:3, desc:'输出暴增成长变慢' },
  { name:'法杖',     emoji:'🪄', slot:'weapon', base:{MATK:20,RNG:6}, tier:2, desc:'纯魔路线' },
  { name:'猎弓',     emoji:'🏹', slot:'bow', base:{PATK:10,RNG:5,ASPD:-0.1}, tier:1, desc:'远程入门' },
  { name:'长弓',     emoji:'🏹', slot:'bow', base:{PATK:18,RNG:7,ASPD:-0.2}, tier:2, desc:'风筝核心' },
  { name:'附魔弓',   emoji:'🏹', slot:'bow', base:{PATK:12,MATK:12,RNG:8,ASPD:-0.2}, tier:3, desc:'物魔双修，魔像天敌' },
];

// 防具底子（按部位分组）
const ARMOR_BASE = {
  offhand:  [{ name:'木盾', emoji:'🛡️', base:{PDEF:4,SPD:-5}, desc:'皇上点名款' },
             { name:'塔盾', emoji:'🛡️', base:{PDEF:15,SPD:-20}, desc:'乌龟流' }],
  head:     [{ name:'皮帽', emoji:'🎩', base:{PDEF:2}, desc:'' },
             { name:'铁盔', emoji:'⛑️', base:{PDEF:5,MDEF:2}, desc:'' }],
  chest:    [{ name:'皮甲', emoji:'🥋', base:{PDEF:3,SPD:3}, desc:'风筝流' },
             { name:'锁子甲',emoji:'👕', base:{PDEF:10,SPD:-5}, desc:'' }],
  legs:     [{ name:'皮裤', emoji:'👖', base:{PDEF:2,SPD:1}, desc:'' },
             { name:'铁腿甲',emoji:'🦵', base:{PDEF:6,SPD:-3}, desc:'' }],
  boots:    [{ name:'草鞋', emoji:'👟', base:{SPD:5}, desc:'' },
             { name:'疾风靴',emoji:'🥾', base:{SPD:15}, desc:'跑图逃跑' }],
  ring:     [{ name:'铜戒', emoji:'💍', base:{PATK:1}, desc:'' },
             { name:'蜂鸟戒指',emoji:'💍', base:{ASPD:0.1}, desc:'最稀有掉落' }],
  necklace: [{ name:'骨链', emoji:'📿', base:{PDEF:1,MDEF:1}, desc:'' },
             { name:'魔力项链',emoji:'📿', base:{MATK:5,MREGEN:2}, desc:'' }],
};

// 词条池（随机词缀）
const AFFIXES = [
  { name:'锋利',     key:'PATK',   type:'pct',  min:0.05, max:0.20, rarity:'common' },
  { name:'咒能',     key:'MATK',   type:'pct',  min:0.05, max:0.20, rarity:'common' },
  { name:'坚韧',     key:'REGEN',  type:'pct',  min:0.05, max:0.20, rarity:'common' },
  { name:'灵巧',     key:'SPD',    type:'pct',  min:0.05, max:0.15, rarity:'common' },
  { name:'重甲',     key:'PDEF',   type:'pct',  min:0.10, max:0.25, rarity:'common', side:{key:'SPD',val:-0.03} },
  { name:'魔纹',     key:'MDEF',   type:'pct',  min:0.10, max:0.25, rarity:'common' },
  { name:'鹰眼',     key:'ACC',    type:'flat', min:0.03, max:0.08, rarity:'uncommon' },
  { name:'蓝泉',     key:'MREGEN', type:'pct',  min:0.10, max:0.30, rarity:'uncommon' },
  { name:'致命',     key:'CRIT',   type:'flat', min:0.03, max:0.15, rarity:'rare' },
  { name:'残忍',     key:'CRITD',  type:'flat', min:0.10, max:0.50, rarity:'rare' },
  { name:'吸血回春', key:'LIFESTEAL', type:'flat', min:0.005, max:0.03, rarity:'rare' },
  { name:'迅捷',     key:'ASPD',   type:'flat', min:0.02, max:0.10, rarity:'epic' },
  { name:'延伸',     key:'RNG',    type:'flat', min:1, max:2, rarity:'epic' },
];

// 品质（影响词条数量）
const QUALITY = [
  { name:'普通', color:'#aaa',   affixCount:1 },
  { name:'精良', color:'#5cb85c', affixCount:2 },
  { name:'稀有', color:'#4a9eff', affixCount:3 },
  { name:'史诗', color:'#b366ff', affixCount:4 },
];

// 掉落概率（按区域）——2026-10-27 玩法调优：整体翻倍，改善"打半天不掉装"体验
const DROP_RATE = { village:0, plain:0.30, forest:0.35, mine:0.45, swamp:0.55, snow:0.65, volcano:0.75 };

// 强化（每级+5%底子，费用递增）
const ENHANCE = { costBase: 100, costMul: 1.5, statBonus: 0.05, maxLv: 10 };

// 背包上限
const BAG_MAX = 30;

// ============================================================
// 天赋系统（新档觉醒：每个灵魂降生时触摸一次命运）
// ============================================================
// 被动永续：所有"有存续时间"的增益被动永不衰减——燃烧/涌动/急速/格挡/
// 药剂增益，一旦生效 ♾️ 永久存续（如小药瓶类：每秒回血10，原仅10秒/共100）。
// 克制位（已预埋）：未来 Boss 的「沉默」被动可封印增益 30 秒（S.buffs.silence）。
// 注意：它是新手引导保底（实际概率 90%，见 NEWBIE_ETERNAL_RATE），仪式按万中无一展示。
const TALENTS = {
  eternal:  { name:'被动永续',   emoji:'♾️', rarity:'million', desc:'所有增益被动永不消逝：一旦生效，永久存续', effect:{ eternal: true } },
  might:    { name:'天生神力',   emoji:'💪', rarity:'common', desc:'物攻 +10%',                    effect:{ mul:{ PATK: 0.10 } } },
  shell:    { name:'铜皮铁骨',   emoji:'🐢', rarity:'common', desc:'物防 +10%、魔防 +10%',          effect:{ mul:{ PDEF: 0.10, MDEF: 0.10 } } },
  wind:     { name:'疾风之躯',   emoji:'💨', rarity:'common', desc:'移速 +15%',                    effect:{ mul:{ SPD: 0.15 } } },
  goldhand: { name:'点金手',     emoji:'🪙', rarity:'common', desc:'击杀金币 +20%',                effect:{ gold: 0.20 } },
  master:   { name:'武学奇才',   emoji:'📕', rarity:'common', desc:'主动技能冷却 -15%',            effect:{ cd: 0.15 } },
};
const NEWBIE_ETERNAL_RATE = 0.90;   // 被动永续实际命中概率（新手福利，内部值勿外泄）

// 增益/减益状态元数据（生效效果区显示用）
// group: passive = 主动技能/药剂带来的持续被动（归入「被动」组）
//        status  = 战斗持续状态/怪物给的毒/沉默（归入「状态」组）
const BUFF_META = {
  burn:      { name:'燃烧生命', emoji:'🔥', good:true,  group:'passive' },
  block:     { name:'格挡反击', emoji:'🛡️', good:true,  group:'passive' },
  rapid:     { name:'急速射击', emoji:'⚡', good:true,  group:'passive' },
  surge:     { name:'魔力涌动', emoji:'✨', good:true,  group:'passive' },
  regen_food:{ name:'兔肉补给', emoji:'🍖', good:true,  group:'passive' },
  spd_pot:   { name:'速度药剂', emoji:'💨', good:true,  group:'passive' },
  silence:   { name:'沉默',     emoji:'🤐', good:false, group:'status'  },  // Boss 减益：30s 内被动失效（含永续）
  poison:    { name:'中毒',     emoji:'☠️', good:false, group:'status'  },  // 怪物毒伤：持续掉血，解毒剂可清
  burn_dot:  { name:'灼烧',     emoji:'🌋', good:false, group:'status'  },  // 怪物灼烧：持续掉血，解毒剂可清
};

// ============================================================
// 技能系统（M4：7 被动 + 6 主动，耗蓝/CD/熟练度）
// ============================================================

// 被动技能（常驻面板加成）
const PASSIVE_SKILLS = {
  combo:    { name:'连击',     emoji:'👊', desc:'15% 概率一拳变两拳',          effect:'combo',    chance:0.15 },
  vitality: { name:'健体',     emoji:'💪', desc:'每 100 次普攻额外 +50 生命上限', effect:'vitality', interval:100, bonus:50 },
  iron:     { name:'铁布衫',   emoji:'🛡️', desc:'PDEF ×1.2',                   effect:'mul',      key:'PDEF', mul:1.2 },
  resist:   { name:'魔抗皮肤', emoji:'🔮', desc:'MDEF ×1.2',                   effect:'mul',      key:'MDEF', mul:1.2 },
  swift:    { name:'疾风步',   emoji:'💨', desc:'+20% 移速',                    effect:'mul',      key:'SPD',  mul:1.2 },
  hawkeye:  { name:'鹰眼',     emoji:'🦅', desc:'+5% 命中',                     effect:'add',      key:'ACC',  val:0.05 },
  brute:    { name:'蛮力',     emoji:'💢', desc:'PATK ×1.15，-3% 命中',        effect:'mul_side', key:'PATK', mul:1.15, sideKey:'ACC', sideVal:-0.03 },
  element:  { name:'元素亲和', emoji:'⚡', desc:'附魔魔伤 +25%',                effect:'matk_bonus', val:0.25 },
};

// 主动技能（6 个，含燃烧生命）
const ACTIVE_SKILLS = {
  heavy: { name:'蓄力重击', emoji:'💥', mp:20, cd:30,  dur:0,  desc:'下一击 ×5 伤害' },
  block: { name:'格挡反击', emoji:'🛡️', mp:25, cd:20,  dur:3,  desc:'3s 内受击减半并反弹 50%' },
  roar:  { name:'怒吼',     emoji:'😤', mp:15, cd:60,  dur:0,  desc:'吓退当前怪物（移速判定）' },
  rapid: { name:'急速射击', emoji:'⚡', mp:40, cd:90,  dur:5,  desc:'5s 内 ASPD ×3' },
  burn:  { name:'燃烧生命', emoji:'🔥', mp:0,  cd:300, dur:300, desc:'烧当前血 25%，300s 攻击 ×2（可常驻）' },
  surge: { name:'魔力涌动', emoji:'✨', mp:50, cd:180, dur:10, desc:'10s 内技能不耗蓝' },
};

// ============================================================
// 药剂系统（M4）
// ============================================================
const POTIONS = {
  hp_s: { name:'小血药', emoji:'🧪', desc:'瞬回 20% 生命上限',  healHP:0.20, price:50 },
  hp_l: { name:'大血药', emoji:'🧪', desc:'瞬回 50% 生命上限',  healHP:0.50, price:200 },
  mp:   { name:'蓝药',   emoji:'🔵', desc:'瞬回 40% MP',        healMP:0.40, price:40 },
  food: { name:'兔肉',   emoji:'🍖', desc:'10s 内 REGEN +20',   buff:'regen_food', val:20, dur:10, price:10 },
  cure: { name:'解毒剂', emoji:'💊', desc:'清除毒/灼烧',         price:30 },
  spd:  { name:'速度药剂',emoji:'💨', desc:'30s 内 ASPD +20%',   buff:'spd_pot', val:0.20, dur:30, price:100 },
};
