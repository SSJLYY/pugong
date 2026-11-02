/* 《普攻永久增加生命上限》自动管家 - 服务器端注入版 v1.3
 * 硬核死亡制定制：保命优先级最高（血低自动逃跑回村）
 * 挂机策略：自动解毒 → 自动嗑药保血 → 血低逃跑回村 → 进村自动存档 → 自动施放主动技能 → 自动售卖低品装备 → 血足自动燃烧 → 达标自动飞升
 * v1.3: 新增 autoCure 自动解毒；中毒/灼烧期间逃跑血线自动上浮 +15%（上限 50%）；沉默状态提示；状态行显示毒/灼烧倒计时
 * 右下角悬浮面板，可折叠为小圆钮，状态持久化 localStorage
 * 通过 window.__game API 驱动（game.js 已全量暴露）
 */
(function () {
    'use strict';

    console.log('[普攻管家] v1.3 脚本已注入, 当前URL:', location.href);

    // 等待游戏 API 就绪（game.js 加载后有 window.__game）
    var G = null;  // window.__game 引用

    // 配置项
    var DEFAULTS = {
        autoPotion:  true,    // 自动嗑药（保命核心）
        autoCure:    true,    // 自动解毒（中毒/灼烧时买解毒剂立即使用，30 金币/瓶）
        autoFlee:    true,    // 血低自动逃跑回村（硬核死亡制保命）
        autoSave:    true,    // 进村自动存档
        autoBurn:    false,   // 自动燃烧生命（默认关，有风险）
        autoRebirth: false,   // 自动飞升转生（默认关，重大决策）
        autoSkill:   true,    // 自动施放主动技能（增益类，buff 未激活才放）
        autoSell:    true,    // 自动售卖低品阶装备（默认开，保底戒指/高品质按阈值保留）
        sellKeepQ:   1,       // 保留品阶：0全留 1留精良+ 2留稀有+ 3留史诗（低于此品阶卖出）
        fleeHpPct:   0.20,    // 血量低于此比例逃跑（20%）
        potionHpPct: 0.50,    // 血量低于此比例嗑药（50%）
        potionMpPct: 0.30,    // 蓝量低于此比例嗑蓝药（30%）
        burnHpPct:   0.60,    // 血量高于此比例才开燃烧（60%）
        skillDefHpPct: 0.60,  // 血量低于此比例才放格挡反击（保命向）
        saveInterval: 5000    // 进村自动存档冷却（ms）
    };

    var CONFIG_VER = 2;
    var config = {};
    try {
        if (localStorage.getItem('gp-autobot-ver') !== String(CONFIG_VER)) {
            localStorage.removeItem('gp-autobot-config');
            localStorage.removeItem('gp-autobot-collapsed');
            localStorage.setItem('gp-autobot-ver', String(CONFIG_VER));
        }
        var saved = JSON.parse(localStorage.getItem('gp-autobot-config') || '{}');
        for (var k in DEFAULTS) config[k] = (k in saved) ? saved[k] : DEFAULTS[k];
    } catch (e) { for (var k2 in DEFAULTS) config[k2] = DEFAULTS[k2]; }

    function saveConfig() {
        try { localStorage.setItem('gp-autobot-config', JSON.stringify(config)); } catch (e) {}
    }

    var timer = null, panel = null, panelBody = null, statusEl = null;
    var lastSaveTime = 0;
    var lastCureWarn = 0;  // 金币不足解毒提醒节流

    // 折叠状态
    var collapsed = false;
    try { var cv = localStorage.getItem('gp-autobot-collapsed'); if (cv !== null) collapsed = (cv === '1'); } catch (e) {}
    function saveCollapsed() {
        try { localStorage.setItem('gp-autobot-collapsed', collapsed ? '1' : '0'); } catch (e) {}
    }

    // ====== 工具：Decimal 安全比较 ======
    function dNum(x) {
        try { return (x && typeof x.toNumber === 'function') ? x.toNumber() : Number(x); }
        catch (e) { return 0; }
    }
    function ratio(cur, max) {
        var c = dNum(cur), m = dNum(max);
        return m > 0 ? c / m : 0;
    }

    // ====== 主循环：每 500ms 检查一轮 ======
    function tick() {
        if (!G || !G.state) return;
        if (G.isGameOver()) return;  // 死亡画面，全停

        var st = G.state;
        var p = G.panel();
        var hpRatio = ratio(st.life.curHP, st.life.maxHP);
        var mpRatio = ratio(st.life.curMP, st.life.MP);

        // 中毒/灼烧期间逃跑血线自动上浮 +15%（上限 50%），状态消失自动恢复
        var dotted = (st.buffs.poison || 0) > 0 || (st.buffs.burn_dot || 0) > 0;
        var effFleePct = dotted ? Math.min(config.fleeHpPct + 0.15, 0.5) : config.fleeHpPct;

        // 1. 保命第一：血低自动逃跑回村
        if (config.autoFlee && st.zone !== 'village' && hpRatio < effFleePct) {
            try { G.flee(); } catch (e) {}
            return;  // 逃跑后等下一轮
        }

        // 2'. 自动解毒：中毒/灼烧时优先用库存解毒剂，无库存且金币够则买一瓶立即用；金币不足节流提醒
        if (config.autoCure && dotted) {
            if ((st.potions.cure || 0) > 0) {
                try { G.usePotion('cure'); } catch(e){}
            } else if (dNum(st.gold) >= 30) {
                try {
                    var rc = G.buyPotion('cure');
                    if (rc && rc.ok && (st.potions.cure || 0) > 0) G.usePotion('cure');
                } catch(e){}
            } else if (Date.now() - lastCureWarn > 10000) {
                lastCureWarn = Date.now();
                if (typeof toast === 'function') toast('⚠️ 中毒/灼烧中但金币不足买解毒剂（需 30 金币），注意血量！');
                console.warn('[普攻管家] 金币不足，无法购买解毒剂');
            }
        }

        // 3. 自动嗑药
        if (config.autoPotion) {
            // 血药：优先大血药，没有则小血药，没有则买大血药
            if (hpRatio < config.potionHpPct) {
                if ((st.potions.hp_l || 0) > 0) { try { G.usePotion('hp_l'); } catch(e){} }
                else if ((st.potions.hp_s || 0) > 0) { try { G.usePotion('hp_s'); } catch(e){} }
                else { try { G.buyPotion('hp_l'); } catch(e){} }  // 买一个大血药备用
            }
            // 蓝药
            if (mpRatio < config.potionMpPct) {
                if ((st.potions.mp || 0) > 0) { try { G.usePotion('mp'); } catch(e){} }
                else { try { G.buyPotion('mp'); } catch(e){} }
            }
        }

        // 4. 进村自动存档（冷却 5s，避免频繁写）
        if (config.autoSave && st.zone === 'village' && Date.now() - lastSaveTime > config.saveInterval) {
            if (typeof saveSlot === 'function') {
                var r = saveSlot(st.lastSaveSlot || 1);
                if (r && r.ok) lastSaveTime = Date.now();
            }
        }

        // 5. 自动施放主动技能（燃烧生命除外，由 autoBurn 单独控制）
        // castSkill 自带 CD/蓝耗校验，返回 false 无副作用，直接尝试即可
        if (config.autoSkill && st.zone !== 'village') {
            var cast = function (id) { try { G.castSkill(id); } catch (e) {} };
            // buff 未激活才放，避免浪费；涌动先行，后续技能 10s 内免蓝
            if (!(st.buffs.surge > 0) && mpRatio > 0.5) cast('surge');
            if (!(st.buffs.rapid > 0)) cast('rapid');
            if (!(st.buffs.heavy > 0)) cast('heavy');
            if (hpRatio < (config.skillDefHpPct || 0.6) && !(st.buffs.block > 0)) cast('block');
            if (hpRatio < 0.30) cast('roar');  // 怒吼吓退怪物，血危时多一层保命
        }

        // 6. 自动燃烧生命（血足才开，烧当前血不烧上限）
        if (config.autoBurn && hpRatio > config.burnHpPct) {
            if (!(st.cds.burn > 0)) {  // CD 好了
                try { G.castSkill('burn'); } catch(e){}
            }
        }

        // 7. 自动售卖低品阶装备（有卖出才提示，无操作零开销）
        if (config.autoSell && st.equipBag && st.equipBag.length > 0 && typeof G.autoSellBag === 'function') {
            try {
                var sold = G.autoSellBag(config.sellKeepQ);
                if (sold && sold.count > 0 && typeof toast === 'function') {
                    toast('💰 管家自动售出 ' + sold.count + ' 件低品装备，+' + sold.gold + ' 金币');
                }
            } catch (e) {}
        }

        // 8. 自动飞升转生（达 1 载次/秒）
        if (config.autoRebirth) {
            try {
                if (G.checkRebirthReady()) {
                    // 安全选项：永远先强化 HP 赋能（+1 → +2 → ...）
                    G.rebirth('maxHP');
                    lastSaveTime = 0;  // 重置存档冷却，新世要重新存
                }
            } catch(e){}
        }
    }

    // ====== 状态行更新（每 1s，单独定时器减轻负担） ======
    function updateStatus() {
        if (!G || !G.state || !statusEl) return;
        var st = G.state;
        var p = G.panel();
        var hpR = ratio(st.life.curHP, st.life.maxHP);
        var zoneName = (typeof ZONES !== 'undefined' && ZONES[st.zone]) ? ZONES[st.zone].name : st.zone;
        var punches = dNum(st.stats.punches);
        // 大数格式化
        var punchStr = (typeof fmt === 'function') ? fmt(st.stats.punches) : Math.floor(punches);
        // 主动技能 buff 指示（可见自动施放在工作）
        var BUFFS = { burn:'🔥', rapid:'⚡', surge:'✨', block:'🛡️', heavy:'💥' };
        var buffStr = '';
        for (var bk in BUFFS) if (st.buffs[bk] > 0) buffStr += BUFFS[bk];
        var hpStr = (typeof fmt === 'function') ? fmt(st.life.curHP) + '/' + fmt(st.life.maxHP) : Math.floor(dNum(st.life.curHP)) + '/' + Math.floor(dNum(st.life.maxHP));
        var hpColor = hpR < 0.2 ? '#ff4444' : hpR < 0.5 ? '#ffa500' : '#43e97b';
        // 毒/灼烧倒计时（用本机秒表平滑显示，基于秒级快照推算）
        var dotStr = '';
        if ((st.buffs.poison || 0) > 0)   dotStr += '☠️毒~' + Math.ceil(st.buffs.poison) + 's ';
        if ((st.buffs.burn_dot || 0) > 0) dotStr += '🌋灼~' + Math.ceil(st.buffs.burn_dot) + 's';
        var silenced = (st.buffs.silence || 0) > 0;
        statusEl.innerHTML =
            '<div style="color:#888">第 ' + st.gen + ' 世 · ' + zoneName + '</div>' +
            '<div style="color:' + hpColor + '">❤️ ' + hpStr + ' (' + Math.round(hpR*100) + '%)</div>' +
            '<div style="color:#888">👊 ' + punchStr + ' 拳</div>' +
            (buffStr ? '<div style="color:#7fd4ff">技能: ' + buffStr + '</div>' : '') +
            (silenced ? '<div style="color:#c9a0ff">🤐 沉默中，被动失效</div>' : '') +
            (dotStr ? '<div style="color:#ff9a5a">DoT: ' + dotStr + '</div>' : '') +
            (G.checkRebirthReady() ? '<div style="color:#ffd700">🌟 可飞升！</div>' : '');
    }

    // ====== 面板渲染 ======
    function checkbox(k, label) {
        return '<label style="cursor:pointer;display:block;color:#ddd">' +
            '<input type="checkbox" data-k="' + k + '"' + (config[k] ? ' checked' : '') + '> ' +
            label + '</label>';
    }

    function renderBody() {
        var html = '<div id="gp-bot-status" style="margin-bottom:6px;padding:4px 8px;background:rgba(0,0,0,.3);border-radius:4px;font-size:11px"></div>';
        html += '<div style="color:#888;font-size:10px;margin-bottom:2px">— 挂机策略 —</div>';
        html += checkbox('autoPotion',  '🧪 自动嗑药（血<50%/蓝<30%）');
        html += checkbox('autoCure',    '💊 自动解毒（中毒/灼烧时自动买解毒剂使用，30 金币/瓶）');
        html += checkbox('autoFlee',    '🏃 血低自动逃跑回村（<20%，保命核心）');
        html += checkbox('autoSave',    '💾 进村自动存档（5s 冷却）');
        html += checkbox('autoSkill',   '⚔️ 自动施放技能（急速/涌动/重击常驻，格挡血危自动，怒吼<30%保命）');
        html += checkbox('autoSell',    '💰 自动售卖低品装备（保底戒指/高品质保留）');
        html += '<label style="color:#ddd;font-size:11px;display:block;margin:1px 0 2px 4px">售卖保留: <select data-k="sellKeepQ" style="background:#222;color:#ddd;border:1px solid #555;border-radius:3px;font-size:11px">'
            + '<option value="1"' + (config.sellKeepQ === 1 ? ' selected' : '') + '>精良及以上</option>'
            + '<option value="2"' + (config.sellKeepQ === 2 ? ' selected' : '') + '>稀有及以上</option>'
            + '<option value="3"' + (config.sellKeepQ === 3 ? ' selected' : '') + '>仅留史诗</option>'
            + '</select>（低于此品阶自动卖出）</label>';
        html += checkbox('autoBurn',    '🔥 自动燃烧生命（血>60%才开）');
        html += checkbox('autoRebirth', '🌟 自动飞升转生（强化HP赋能）');
        html += '<div style="color:#888;font-size:10px;margin-top:4px;margin-bottom:2px">— 阈值微调 —</div>';
        html += '<label style="color:#ddd;font-size:11px">逃跑血线: <input type="number" data-k="fleeHpPct" value="' + config.fleeHpPct + '" step="0.05" min="0.05" max="0.5" style="width:50px;background:#222;color:#ddd;border:1px solid #555;border-radius:3px"></label>';
        html += '<label style="color:#ddd;font-size:11px;display:block;margin-top:2px">嗑药血线: <input type="number" data-k="potionHpPct" value="' + config.potionHpPct + '" step="0.05" min="0.1" max="0.9" style="width:50px;background:#222;color:#ddd;border:1px solid #555;border-radius:3px"></label>';
        html += '<label style="color:#ddd;font-size:11px;display:block;margin-top:2px">格挡血线: <input type="number" data-k="skillDefHpPct" value="' + config.skillDefHpPct + '" step="0.05" min="0.1" max="0.9" style="width:50px;background:#222;color:#ddd;border:1px solid #555;border-radius:3px"></label>';
        panelBody.innerHTML = html;
        statusEl = panelBody.querySelector('#gp-bot-status');
        // 事件绑定
        Array.prototype.forEach.call(panelBody.querySelectorAll('input[type=checkbox]'), function (cb) {
            cb.addEventListener('change', function (e) {
                var k = e.target.getAttribute('data-k');
                config[k] = e.target.checked;
                saveConfig();
            });
        });
        Array.prototype.forEach.call(panelBody.querySelectorAll('input[type=number]'), function (inp) {
            inp.addEventListener('change', function (e) {
                var k = e.target.getAttribute('data-k');
                var v = parseFloat(e.target.value);
                if (!isNaN(v)) { config[k] = v; saveConfig(); }
            });
        });
        Array.prototype.forEach.call(panelBody.querySelectorAll('select[data-k]'), function (sel) {
            sel.addEventListener('change', function (e) {
                config[e.target.getAttribute('data-k')] = parseInt(e.target.value, 10);
                saveConfig();
            });
        });
    }

    function applyCollapsed() {
        if (collapsed) {
            panel.style.cssText = 'position:fixed;right:12px;bottom:12px;z-index:2147483647;' +
                'width:36px;height:36px;border-radius:50%;cursor:pointer;' +
                'background:rgba(20,20,20,.92);border:1px solid #888;' +
                'display:flex;align-items:center;justify-content:center;' +
                'font-size:18px;box-shadow:0 4px 16px rgba(0,0,0,.5);user-select:none';
            panel.innerHTML = '🥊';
            panel.title = '普攻管家（点击展开）';
        } else {
            panel.style.cssText = 'position:fixed;right:12px;bottom:12px;z-index:2147483647;' +
                'background:rgba(20,20,20,.92);color:#ddd;font:12px/1.7 monospace;' +
                'padding:8px 12px;border:1px solid #888;border-radius:8px;' +
                'user-select:none;box-shadow:0 4px 16px rgba(0,0,0,.5);' +
                'max-height:70vh;overflow-y:auto;min-width:240px';
            panel.title = '';
            panel.innerHTML =
                '<div style="display:flex;justify-content:space-between;align-items:center;gap:12px">' +
                '<b>🥊 普攻管家 <span style="color:#43e97b;font-size:10px">v1.3</span></b>' +
                '<span id="gp-bot-fold" style="cursor:pointer;color:#888;padding:0 4px" title="折叠">▾</span>' +
                '</div><div id="gp-bot-body"></div>';
            panelBody = panel.querySelector('#gp-bot-body');
            renderBody();
            updateStatus();
        }
    }

    // 事件委托：panel 一次性绑定
    function onPanelClick(e) {
        var t = e.target;
        if (t && t.id === 'gp-bot-fold' || (t && t.closest && t.closest('#gp-bot-fold'))) {
            collapsed = true; saveCollapsed(); applyCollapsed();
        } else if (collapsed) {
            collapsed = false; saveCollapsed(); applyCollapsed();
        }
    }

    // ====== 启动：等 body + 等 window.__game 就绪 ======
    var tries = 0;
    var boot = setInterval(function () {
        tries++;
        if (document.body && window.__game) {
            clearInterval(boot);
            G = window.__game;
            panel = document.createElement('div');
            panel.id = 'gp-autobot-panel';
            panel.addEventListener('click', onPanelClick);
            document.body.appendChild(panel);
            applyCollapsed();
            timer = setInterval(tick, 500);               // 挂机策略 500ms
            setInterval(updateStatus, 1000);               // 状态行 1s
            console.log('[普攻管家] v1.3 已启动, 保命=' + config.autoFlee + ' 嗑药=' + config.autoPotion + ' 解毒=' + config.autoCure + ' 存档=' + config.autoSave + ' 技能=' + config.autoSkill + ' 售卖=' + config.autoSell + '(保留≥' + config.sellKeepQ + ') 燃烧=' + config.autoBurn + ' 飞升=' + config.autoRebirth);
        } else if (tries > 60) {  // 30s 超时
            clearInterval(boot);
            console.warn('[普攻管家] 等待 window.__game 超时, 未启动（游戏未加载？）');
        }
    }, 500);

    window.__gpBotStop = function () {
        if (timer) clearInterval(timer);
        if (panel) panel.remove();
        console.log('[普攻管家] 已停止');
    };

    // 暴露配置接口（测试/外部动态改配置用）
    window.__gpBot = {
        get config() { return config; },
        setConfig: function (k, v) { config[k] = v; saveConfig(); },
        setConfigAll: function (obj) { for (var k in obj) config[k] = obj[k]; saveConfig(); },
        reloadConfig: function () {
            try {
                var saved = JSON.parse(localStorage.getItem('gp-autobot-config') || '{}');
                for (var k in DEFAULTS) config[k] = (k in saved) ? saved[k] : DEFAULTS[k];
            } catch (e) {}
        }
    };
})();
