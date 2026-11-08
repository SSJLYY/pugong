/* 双窗工作台抽屉控制：与游戏结算解耦，仅管理导航、焦点和响应式呈现。 */
(function () {
  'use strict';

  const TAB_META = {
    equip: ['🎒', '装备与背包'],
    skills: ['✋', '技能与药剂'],
    quest: ['📜', '委托与伙伴'],
    craft: ['⚒️', '锻造与合成'],
    role: ['👤', '角色与成就'],
    save: ['💾', '存档与墓园'],
    social: ['🏮', '江湖与世界墓园'],
    rank: ['🏆', '排行榜']
  };
  const DRAWERS = { nav: 'navDrawer', map: 'mapDrawer', settings: 'settingsDrawer' };
  const FOCUSABLE = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
  const mobileQuery = window.matchMedia('(max-width: 900px)');
  let activeDrawer = null;
  let drawerReturnFocus = null;
  let utilityReturnFocus = null;
  let initialized = false;

  const $ = id => document.getElementById(id);
  const isMobile = () => mobileQuery.matches;
  const utility = () => $('utilityPanel');

  function setInert(el, value) {
    if (!el) return;
    if (value) el.setAttribute('inert', '');
    else el.removeAttribute('inert');
  }

  function syncBackdrop() {
    const show = !!activeDrawer || (isMobile() && document.body.classList.contains('utility-open'));
    $('drawerBackdrop').classList.toggle('show', show);
    document.body.classList.toggle('drawer-open', show);
  }

  function syncExpanded() {
    document.querySelectorAll('[data-open-drawer]').forEach(btn => {
      btn.setAttribute('aria-expanded', String(activeDrawer === btn.dataset.openDrawer));
    });
  }

  function closePopovers() {
    const stat = $('statCard');
    const talent = $('talentPop');
    if (stat) stat.classList.remove('open');
    if (talent) talent.classList.remove('open');
  }

  /* 第十五批·轮2：打开瞬间挂 drawer-skeleton，300ms 后移除；.drawer-scroll 缺失时落到抽屉根。 */
  function showDrawerSkeleton(el) {
    if (!el) return;
    const target = el.querySelector('.drawer-scroll') || el;
    target.classList.add('drawer-skeleton');
    window.setTimeout(() => {
      /* 只有仍是同一节点且未被移除时才摘类，避免跨抽屉误摘。 */
      if (target.isConnected) target.classList.remove('drawer-skeleton');
    }, 300);
  }

  function focusFirst(container) {
    if (!container) return;
    const target = container.querySelector('.drawer-close, .workspace-close, ' + FOCUSABLE);
    if (target) target.focus({ preventScroll: true });
  }

  function safeFocus(el) {
    if (!el || !el.isConnected || typeof el.focus !== 'function') return;
    if (el.getClientRects().length) el.focus({ preventScroll: true });
  }

  function openDrawer(name) {
    const id = DRAWERS[name];
    const drawer = $(id);
    if (!drawer) return;
    if (activeDrawer === name) { closeDrawer(); return; }

    closePopovers();
    if (isMobile() && document.body.classList.contains('utility-open')) closeUtility(false);
    if (activeDrawer) closeDrawer(false);
    drawerReturnFocus = document.activeElement;
    activeDrawer = name;

    /* 第十五批·轮2：抽屉打开瞬间挂 drawer-skeleton，300ms 后移除（样式类由 drawer.css 提供）。 */
    showDrawerSkeleton(drawer);

    Object.entries(DRAWERS).forEach(([key, drawerId]) => {
      const el = $(drawerId);
      const open = key === name;
      el.classList.toggle('open', open);
      el.setAttribute('aria-hidden', String(!open));
      el.setAttribute('role', 'dialog');
      el.setAttribute('aria-modal', 'true');
      setInert(el, !open);
    });
    syncExpanded();
    syncBackdrop();
    // 第十七批：打开功能目录时刷新最近使用并直达搜索框（N 键效率路径）
    if (name === 'nav') renderNavRecent();
    window.setTimeout(() => {
      if (activeDrawer !== name) return;
      if (name === 'nav') {
        const search = $('navSearch');
        if (search) { search.focus({ preventScroll: true }); return; }
      }
      focusFirst(drawer);
    }, 30);
  }

  function closeDrawer(restoreFocus = true) {
    if (!activeDrawer) return;
    const previous = activeDrawer;
    const drawer = $(DRAWERS[previous]);
    if (drawer) {
      drawer.classList.remove('open');
      drawer.setAttribute('aria-hidden', 'true');
      setInert(drawer, true);
    }
    activeDrawer = null;
    syncExpanded();
    syncBackdrop();
    if (restoreFocus) safeFocus(drawerReturnFocus);
    drawerReturnFocus = null;
  }

  function configureUtility() {
    const panel = utility();
    if (!panel) return;
    if (isMobile()) {
      const open = document.body.classList.contains('utility-open');
      panel.setAttribute('role', 'dialog');
      panel.setAttribute('aria-modal', 'true');
      panel.setAttribute('aria-hidden', String(!open));
      setInert(panel, !open);
    } else {
      document.body.classList.remove('utility-open');
      panel.removeAttribute('role');
      panel.removeAttribute('aria-modal');
      panel.setAttribute('aria-hidden', 'false');
      setInert(panel, false);
    }
    syncBackdrop();
  }

  function openUtility() {
    if (!isMobile()) { closeDrawer(false); return; }
    closePopovers();
    if (activeDrawer) closeDrawer(false);
    utilityReturnFocus = document.activeElement;
    document.body.classList.add('utility-open');
    configureUtility();
    syncDock();
    window.setTimeout(() => {
      if (document.body.classList.contains('utility-open')) focusFirst(utility());
    }, 30);
  }

  function closeUtility(restoreFocus = true) {
    document.body.classList.remove('utility-open');
    configureUtility();
    syncDock();
    if (restoreFocus) safeFocus(utilityReturnFocus);
    utilityReturnFocus = null;
  }

  function closeAll() {
    if (activeDrawer) closeDrawer(false);
    if (document.body.classList.contains('utility-open')) closeUtility(false);
    syncDock();
  }

  function currentTab() {
    const active = document.querySelector('.game-tab.active');
    return active && TAB_META[active.dataset.tab] ? active.dataset.tab : 'equip';
  }

  function syncDock() {
    const tab = currentTab();
    const utilityOpen = isMobile() && document.body.classList.contains('utility-open');
    document.querySelectorAll('[data-dock-tab]').forEach(btn => {
      const active = utilityOpen && btn.dataset.dockTab === tab;
      btn.classList.toggle('active', active);
      if (active) btn.setAttribute('aria-current', 'page');
      else btn.removeAttribute('aria-current');
    });
    document.querySelectorAll('[data-dock-action="combat"]').forEach(btn => {
      const active = !utilityOpen && !activeDrawer;
      btn.classList.toggle('active', active);
      if (active) btn.setAttribute('aria-current', 'page');
      else btn.removeAttribute('aria-current');
    });
  }

  /* 第十五批·轮1：监听 gongpu:workspacechange，同步 .mobile-dock 内按钮高亮。 */
  function syncMobileDock(tab) {
    const dock = document.querySelector('.mobile-dock');
    if (!dock) return;
    const utilityOpen = isMobile() && document.body.classList.contains('utility-open');
    dock.querySelectorAll('[data-dock-tab]').forEach(btn => {
      const active = utilityOpen && btn.dataset.dockTab === tab;
      btn.classList.toggle('active', active);
      if (active) btn.setAttribute('aria-current', 'page');
      else btn.removeAttribute('aria-current');
    });
    dock.querySelectorAll('[data-dock-action="combat"]').forEach(btn => {
      const active = !utilityOpen && !activeDrawer;
      btn.classList.toggle('active', active);
      if (active) btn.setAttribute('aria-current', 'page');
      else btn.removeAttribute('aria-current');
    });
  }

  function onWorkspaceChange(e) {
    const tab = (e && e.detail && e.detail.tab) || currentTab();
    /* 延后一拍执行，等事件派发方（如 openUtility）落定后再按最终状态高亮。 */
    window.setTimeout(() => syncMobileDock(tab), 0);
  }

  function applyTab(tab, options) {
    if (!TAB_META[tab]) return;
    const opts = options || {};
    document.querySelectorAll('.game-tab').forEach(btn => {
      const active = btn.dataset.tab === tab;
      btn.classList.toggle('active', active);
      btn.setAttribute('aria-selected', String(active));
      btn.tabIndex = active ? 0 : -1;
    });
    document.querySelectorAll('.tab-pane[data-pane]').forEach(pane => {
      const active = pane.dataset.pane === tab;
      pane.hidden = !active;
      pane.classList.toggle('active', active);
      pane.setAttribute('role', 'tabpanel');
      pane.setAttribute('aria-hidden', String(!active));
      if (active) pane.scrollTop = 0;
    });
    const meta = TAB_META[tab];
    $('workspaceIcon').textContent = meta[0];
    $('workspaceName').textContent = meta[1];
    utility().setAttribute('aria-label', meta[1]);
    if (opts.remember !== false) {
      try { localStorage.setItem('gongpu_workspace', tab); } catch (e) { /* 隐私模式不影响切换 */ }
    }
    syncDock();
    document.dispatchEvent(new CustomEvent('gongpu:workspacechange', { detail: { tab } }));
    if (opts.open) openUtility();
  }

  function selectTab(tab) {
    applyTab(tab, { open: true });
    recordRecent(tab);
    if (activeDrawer === 'nav') closeDrawer(false);
    if (!isMobile()) safeFocus($('btnWorkspaceNav'));
  }

  /* ---------------- 第十七批：功能目录——搜索过滤 / 最近使用 ---------------- */
  const RECENT_KEY = 'gongpu_recent_tabs';

  function readRecent() {
    try {
      const v = JSON.parse(localStorage.getItem(RECENT_KEY) || '[]');
      return Array.isArray(v) ? v.filter(t => TAB_META[t]) : [];
    } catch (e) { return []; }
  }

  function recordRecent(tab) {
    if (!TAB_META[tab]) return;
    const list = [tab].concat(readRecent().filter(t => t !== tab)).slice(0, 3);
    try { localStorage.setItem(RECENT_KEY, JSON.stringify(list)); } catch (e) { /* 隐私模式仅本次会话 */ }
    renderNavRecent();
  }

  function renderNavRecent() {
    const box = $('navRecent');
    if (!box) return;
    const current = currentTab();
    const items = readRecent().filter(t => t !== current);
    if (!items.length) { box.hidden = true; box.innerHTML = ''; return; }
    box.hidden = false;
    box.innerHTML = '<span class="nav-recent-label">🕘 最近</span>' + items.map(t =>
      '<button type="button" class="nav-recent-chip" data-recent-tab="' + t + '">' +
      TAB_META[t][0] + ' ' + TAB_META[t][1] + '</button>').join('');
  }

  /* 目录搜索：按按钮文本 + 功能全称过滤；分组标题随组内可见性联动；无结果给提示。 */
  function filterNav(query) {
    const drawer = $('navDrawer');
    if (!drawer) return;
    const q = (query || '').trim().toLowerCase();
    let anyVisible = false;
    drawer.querySelectorAll('.game-tab[data-tab]').forEach(btn => {
      const meta = TAB_META[btn.dataset.tab];
      const text = (btn.textContent + ' ' + (meta ? meta[1] : '')).toLowerCase();
      const show = !q || text.indexOf(q) >= 0;
      btn.hidden = !show;
      if (show) anyVisible = true;
    });
    drawer.querySelectorAll('.nav-group-title').forEach(title => {
      let el = title.nextElementSibling;
      let has = false;
      while (el && !el.classList.contains('nav-group-title')) {
        if (el.classList.contains('game-tab') && !el.hidden) has = true;
        el = el.nextElementSibling;
      }
      title.hidden = q ? !has : false;
    });
    let empty = $('navEmpty');
    if (!empty) {
      empty = document.createElement('p');
      empty.id = 'navEmpty';
      empty.className = 'nav-empty';
      const nav = drawer.querySelector('.drawer-tab-list');
      if (nav) nav.appendChild(empty);
    }
    empty.textContent = '没有匹配「' + (query || '').trim() + '」的功能';
    empty.hidden = anyVisible || !q;
  }

  function initNavFilter() {
    const input = $('navSearch');
    if (!input || input._boundNavFilter) return;
    input._boundNavFilter = true;
    input.addEventListener('input', () => filterNav(input.value));
    // Esc 在搜索框里优先清空而不是关抽屉（由 onKeydown 的 input 早退保证不冲突）
    input.addEventListener('keydown', e => {
      if (e.key === 'Escape' && input.value) {
        e.preventDefault();
        e.stopPropagation();
        input.value = '';
        filterNav('');
      }
    });
    renderNavRecent();
  }


  function trapFocus(e) {
    const container = activeDrawer ? $(DRAWERS[activeDrawer]) :
      (isMobile() && document.body.classList.contains('utility-open') ? utility() : null);
    if (!container || e.key !== 'Tab') return;
    const items = Array.from(container.querySelectorAll(FOCUSABLE)).filter(el =>
      !el.hidden && el.getClientRects().length && !el.closest('[hidden]'));
    if (!items.length) { e.preventDefault(); container.focus(); return; }
    const first = items[0];
    const last = items[items.length - 1];
    if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
    else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
  }

  function modalVisible() {
    return Array.from(document.querySelectorAll('.death-mask')).some(el =>
      window.getComputedStyle(el).display !== 'none');
  }

  function onKeydown(e) {
    if (e.key === 'Tab' && modalVisible()) return;
    trapFocus(e);
    if (e.key === 'Escape') {
      if (modalVisible()) return;
      if (activeDrawer) { e.preventDefault(); closeDrawer(); }
      else if (isMobile() && document.body.classList.contains('utility-open')) {
        e.preventDefault();
        closeUtility();
      }
      return;
    }
    if (e.repeat || e.ctrlKey || e.metaKey || e.altKey || modalVisible()) return;
    const target = e.target;
    if (target && (target.matches('input, textarea, select') || target.isContentEditable)) return;
    const currentTabButton = target && target.closest ? target.closest('.game-tab[data-tab]') : null;
    if (currentTabButton && ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'].includes(e.key)) {
      e.preventDefault();
      const tabs = Array.from(document.querySelectorAll('.game-tab[data-tab]'));
      const index = tabs.indexOf(currentTabButton);
      const backwards = e.key === 'ArrowLeft' || e.key === 'ArrowUp';
      const nextIndex = e.key === 'Home' ? 0 : e.key === 'End' ? tabs.length - 1 :
        (index + (backwards ? -1 : 1) + tabs.length) % tabs.length;
      tabs[nextIndex].click();
      return;
    }
    if (!$('gameRoot').classList.contains('on')) return;
    if (e.code === 'KeyM') { e.preventDefault(); openDrawer('map'); }
    if (e.code === 'KeyN') { e.preventDefault(); openDrawer('nav'); }
  }

  function bindGestures() {
    const header = document.querySelector('.workspace-bar');
    if (!header) return;
    let startY = null;
    header.addEventListener('pointerdown', e => {
      if (e.pointerType === 'touch' && isMobile()) startY = e.clientY;
    });
    header.addEventListener('pointerup', e => {
      if (startY !== null && e.clientY - startY > 72) closeUtility();
      startY = null;
    });
    header.addEventListener('pointercancel', () => { startY = null; });

    Object.entries(DRAWERS).forEach(([name, id]) => {
      const drawer = $(id);
      const grip = drawer && drawer.querySelector('.drawer-header');
      if (!grip) return;
      let startX = null;
      grip.addEventListener('pointerdown', e => {
        if (e.pointerType === 'touch') startX = e.clientX;
      });
      grip.addEventListener('pointerup', e => {
        if (startX !== null && activeDrawer === name) {
          const delta = e.clientX - startX;
          if ((name === 'nav' && delta < -72) || (name !== 'nav' && delta > 72)) closeDrawer();
        }
        startX = null;
      });
      grip.addEventListener('pointercancel', () => { startX = null; });
    });
  }

  function onDocumentClick(e) {
    const opener = e.target.closest('[data-open-drawer]');
    if (opener) { e.preventDefault(); openDrawer(opener.dataset.openDrawer); return; }

    if (e.target.closest('[data-close-drawer]')) { e.preventDefault(); closeDrawer(); return; }
    if (e.target.closest('#drawerBackdrop')) {
      e.preventDefault();
      if (activeDrawer) closeDrawer();
      else closeUtility();
      return;
    }
    if (e.target.closest('#btnWorkspaceClose')) { e.preventDefault(); closeUtility(); return; }

    const recentChip = e.target.closest('[data-recent-tab]');
    if (recentChip) {
      e.preventDefault();
      selectTab(recentChip.dataset.recentTab);
      return;
    }

    const tabButton = e.target.closest('.game-tab[data-tab]');
    if (tabButton) {
      window.setTimeout(() => selectTab(tabButton.dataset.tab), 0);
      return;
    }

    const dockTab = e.target.closest('[data-dock-tab]');
    if (dockTab) {
      e.preventDefault();
      const tab = document.querySelector('.game-tab[data-tab="' + dockTab.dataset.dockTab + '"]');
      if (tab) tab.click();
      return;
    }

    if (e.target.closest('[data-dock-action="combat"]')) {
      e.preventDefault();
      const combatButton = e.target.closest('[data-dock-action="combat"]');
      closeAll();
      safeFocus(combatButton);
      window.scrollTo({ top: 0, behavior: document.body.classList.contains('motion-off') ? 'auto' : 'smooth' });
      return;
    }

    if (e.target.closest('#mapDrawer .zone-btn:not([disabled]), #mapDrawer #btnDeepestZone, #mapDrawer #btnBoss')) {
      window.setTimeout(() => { if (activeDrawer === 'map') closeDrawer(); }, 80);
    }
  }

  /* 第十五批·轮3：settings 抽屉分组折叠——注入折叠按钮、切换 .folded、状态存 gongpu_settings_fold。 */
  function readSettingsFold() {
    try { return JSON.parse(localStorage.getItem('gongpu_settings_fold') || '{}') || {}; }
    catch (e) { return {}; }
  }

  function writeSettingsFold(state) {
    try { localStorage.setItem('gongpu_settings_fold', JSON.stringify(state || {})); }
    catch (e) { /* 隐私模式下折叠状态仅本次会话有效 */ }
  }

  function initSettingsFold() {
    const groups = document.querySelectorAll('section.settings-group');
    if (!groups.length) return;
    const saved = readSettingsFold();
    groups.forEach(group => {
      const heading = group.querySelector('h3');
      if (!heading || heading.querySelector('.settings-fold')) return;
      const title = (heading.textContent || '').trim();
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'settings-fold';
      button.setAttribute('aria-expanded', 'true');
      button.textContent = '▾';
      button.addEventListener('click', () => {
        const folded = group.classList.toggle('folded');
        button.setAttribute('aria-expanded', String(!folded));
        button.textContent = folded ? '▸' : '▾';
        const state = readSettingsFold();
        if (folded) state[title] = true;
        else delete state[title];
        writeSettingsFold(state);
      });
      heading.appendChild(button);
      if (saved[title]) {
        group.classList.add('folded');
        button.setAttribute('aria-expanded', 'false');
        button.textContent = '▸';
      }
    });
  }

  /* 第十五批·轮4：主题切换防闪白——body class 变化时禁过渡一帧，下个 rAF 还原。 */
  function initThemeFlashGuard() {
    const body = document.body;
    const docEl = document.documentElement;
    let lastThemes = Array.from(body.classList).filter(cls => /^theme-/.test(cls)).sort().join(' ');
    const observer = new MutationObserver(records => {
      let changed = false;
      records.forEach(record => {
        if (record.attributeName === 'class') changed = true;
      });
      if (!changed) return;
      const themes = Array.from(body.classList).filter(cls => /^theme-/.test(cls)).sort().join(' ');
      if (themes === lastThemes) return;
      lastThemes = themes;
      /* 禁止过渡并让底色立即落到当前主题，避免主题类增删瞬间闪白。 */
      docEl.style.transition = 'none';
      body.style.transition = 'none';
      const background = window.getComputedStyle(body).backgroundColor;
      if (background && background !== 'rgba(0, 0, 0, 0)') docEl.style.backgroundColor = background;
      window.requestAnimationFrame(() => {
        window.requestAnimationFrame(() => {
          docEl.style.transition = '';
          body.style.transition = '';
          docEl.style.backgroundColor = '';
        });
      });
    });
    observer.observe(body, { attributes: true, attributeFilter: ['class'] });
  }

  /* 第十五批·轮5：字号超大档启动兜底——gongpu_font==='xl' 时挂 font-xl。 */
  function initFontXlBootstrap() {
    try {
      if (localStorage.getItem('gongpu_font') === 'xl') document.body.classList.add('font-xl');
    } catch (e) { /* 隐私模式下跳过启动挂类 */ }
  }

  function restoreWorkspace() {
    let tab = 'equip';
    try {
      const saved = localStorage.getItem('gongpu_workspace');
      if (TAB_META[saved]) tab = saved;
    } catch (e) { /* 使用默认装备页 */ }
    applyTab(tab, { remember: false, open: false });
  }

  function init() {
    if (initialized || !$('utilityPanel')) return;
    initialized = true;
    const audioButton = $('gp-audio-btn');
    const audioSlot = $('audioSettingsSlot');
    if (audioButton && audioSlot) audioSlot.appendChild(audioButton);
    /* 第十五批·轮1：先注册事件再恢复工作区，启动期切换同样同步 dock。 */
    document.addEventListener('gongpu:workspacechange', onWorkspaceChange);
    /* 第十五批·轮3：启动时初始化 settings 分组折叠按钮并恢复持久化状态。 */
    initSettingsFold();
    /* 第十五批·轮4：主题切换防闪白监听。 */
    initThemeFlashGuard();
    /* 第十五批·轮5：字号超大档启动兜底挂类。 */
    initFontXlBootstrap();
    /* 第十七批：目录搜索过滤 + 最近使用初始化。 */
    initNavFilter();
    restoreWorkspace();
    configureUtility();
    syncDock();
    document.addEventListener('click', onDocumentClick);
    document.addEventListener('keydown', onKeydown);
    bindGestures();
    const onViewportChange = () => {
      closeAll();
      configureUtility();
      syncDock();
    };
    if (mobileQuery.addEventListener) mobileQuery.addEventListener('change', onViewportChange);
    else mobileQuery.addListener(onViewportChange);

    const root = $('gameRoot');
    new MutationObserver(() => {
      if (!root.classList.contains('on')) closeAll();
    }).observe(root, { attributes: true, attributeFilter: ['class'] });
  }

  window.GP_DRAWERS = {
    open: openDrawer,
    close: closeDrawer,
    closeAll,
    select: selectTab,
    current: currentTab,
    isMobile
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
