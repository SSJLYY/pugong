'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../js/app.js'), 'utf8');
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
function harness() {
  const nodes = new Map(), events = new Map(), views = [], notices = [], intervals = new Set();
  const node = id => {
    if (!nodes.has(id)) nodes.set(id, { style: {}, value: '', dataset: {},
      classList: { add() {}, remove() {}, toggle() {}, contains() { return false; } },
      addEventListener(name, fn) { this[name] = fn; }, setAttribute() {} });
    return nodes.get(id);
  };
  const api = { state: { token: 'first', user: { id: 1 }, slot: 1 },
    setSlot(slot) { this.state.slot = slot; },
    clearAuth() { this.state.token = ''; this.state.user = null; } };
  const window = { GP_API: api, addEventListener(name, fn) { events.set(name, fn); } };
  const context = { window, document: { readyState: 'loading', hidden: false,
    getElementById: node, querySelectorAll: () => [], addEventListener() {} },
    navigator: {}, console, setTimeout, clearTimeout,
    setInterval(fn) { intervals.add(fn); return fn; }, clearInterval(fn) { intervals.delete(fn); } };
  const marker = "  if (document.readyState === 'loading') {";
  assert.equal(source.split(marker).length, 2, '测试入口必须唯一');
  const injection = `
    applyView = v => window.__views.push(v);
    toast = msg => window.__notices.push(msg);
    setNet = () => {};
    renderZoneButtons = renderSkillButtons = loadBulletin = () => {};
    initStatCard = initTalentPop = bindHotkeys = restoreSubTabs = () => {};
    window.__app = { S, call, tick, enterGame, showSlots, resetPage, initAuth, boot };
  `;
  window.__views = views;
  window.__notices = notices;
  vm.runInNewContext(source.replace(marker, injection + marker), context, { filename: 'app.js' });
  const app = window.__app;
  app.S.meta = {};
  app.S._metaAt = Date.now();
  return { ...app, api, node, events, views, notices, intervals };
}
const flush = () => new Promise(resolve => setImmediate(resolve));

test('连续不同操作均按顺序执行，不合并请求', async () => {
  const h = harness(), first = deferred(), sent = [];
  const a = h.call(() => { sent.push('buy'); return first.promise; });
  await flush();
  const b = h.call(() => { sent.push('flee'); return 'fled'; });
  await flush();
  assert.deepEqual(sent, ['buy']);
  first.resolve('bought');
  assert.deepEqual(await Promise.all([a, b]), ['bought', 'fled']);
  assert.deepEqual(sent, ['buy', 'flee']);
});

test('退出后旧轮询不能渲染或清除新世代busy', async () => {
  const h = harness(), old = deferred(), current = deferred();
  h.initAuth();
  h.api.gameState = () => old.promise;
  const pending = h.tick();
  h.node('btnLogout').click();
  h.api.state.token = 'second'; h.api.state.user = { id: 2 }; h.api.setSlot(2);
  h.api.gameState = () => current.promise;
  const fresh = h.tick();
  old.resolve({ talent: true });
  await pending;
  assert.equal(h.S.busy, true);
  assert.equal(h.views.length, 0);
  current.resolve({ talent: true });
  await fresh;
  assert.equal(h.S.busy, false);
  assert.equal(h.views.length, 1);
});

test('旧世代在途及排队操作取消，不能发给新会话', async () => {
  const h = harness(), first = deferred(), sent = [];
  const a = h.call(() => first.promise);
  const aResult = assert.rejects(a, { code: 'PAGE_CHANGED' });
  await flush();
  const b = h.call(() => sent.push('old queued'));
  const bResult = assert.rejects(b, { code: 'PAGE_CHANGED' });
  h.resetPage(); h.api.state.token = 'second'; h.api.state.user = { id: 2 };
  first.resolve('old');
  await Promise.all([aResult, bResult]);
  assert.deepEqual(sent, []);
  assert.equal(await h.call(() => 'new'), 'new');
  assert.deepEqual(h.notices, []);
});

test('新页面操作不等待旧页面悬挂的写请求', async () => {
  const h = harness(), old = deferred();
  const pending = h.call(() => old.promise);
  const oldResult = assert.rejects(pending, { code: 'PAGE_CHANGED' });
  await flush();
  h.resetPage();
  let freshCompleted = false;
  const fresh = h.call(() => 'new').then(value => { freshCompleted = true; return value; });
  await flush();
  assert.equal(freshCompleted, true);
  assert.equal(await fresh, 'new');
  old.resolve('old');
  await oldResult;
});

test('冲突重试在页面切换后取消，不重复发送旧动作', async () => {
  const h = harness();
  let sends = 0;
  const pending = h.call(() => {
    sends++;
    return Promise.reject(Object.assign(new Error('冲突'), { code: 'CONFLICT' }));
  });
  const result = assert.rejects(pending, { code: 'PAGE_CHANGED' });
  await flush();
  h.resetPage();
  await result;
  assert.equal(sends, 1);
  assert.deepEqual(h.notices, []);
});

test('槽位直接变化也会取消旧排队动作', async () => {
  const h = harness(), first = deferred();
  const a = h.call(() => first.promise);
  const aResult = assert.rejects(a, { code: 'PAGE_CHANGED' });
  await flush();
  let sends = 0;
  const b = h.call(() => sends++);
  const bResult = assert.rejects(b, { code: 'PAGE_CHANGED' });
  h.api.setSlot(2);
  first.resolve('old');
  await Promise.all([aResult, bResult]);
  assert.equal(sends, 0);
});

test('滑动令牌续期仍执行当前账号队列', async () => {
  const h = harness(), first = deferred();
  const a = h.call(() => first.promise);
  await flush();
  const b = h.call(() => 'second action');
  h.api.state.token = 'renewed';
  first.resolve('first action');
  assert.deepEqual(await Promise.all([a, b]), ['first action', 'second action']);
});

test('切换槽位后旧载入不渲染或启动轮询', async () => {
  const h = harness(), old = deferred(), current = deferred();
  h.api.loadSave = slot => slot === 1 ? old.promise : current.promise;
  h.api.gameState = () => new Promise(() => {});
  const a = h.enterGame(1);
  await flush();
  const b = h.enterGame(2);
  old.resolve({ talent: true, slot: 1 });
  await a;
  assert.equal(h.views.length, 0);
  assert.equal(h.intervals.size, 0);
  current.resolve({ talent: true, slot: 2 });
  await b;
  assert.deepEqual(h.views.map(v => v.slot), [2]);
  assert.equal(h.intervals.size, 1);
});

test('退出时旧元数据载入不能继续加载存档', async () => {
  const h = harness(), meta = deferred();
  h.S.meta = null;
  h.api.meta = () => meta.promise;
  let loads = 0;
  h.api.loadSave = () => { loads++; };
  const pending = h.enterGame(1);
  await flush();
  h.resetPage(); h.api.clearAuth(); h.api.setSlot(0);
  meta.resolve({ old: true });
  await pending;
  assert.equal(h.S.meta, null);
  assert.equal(loads, 0);
  assert.equal(h.intervals.size, 0);
});

test('返回选槽隔离旧轮询并清除当前槽位', async () => {
  const h = harness(), old = deferred();
  h.api.gameState = () => old.promise;
  const pending = h.tick();
  h.api.listSaves = () => [];
  await h.showSlots();
  old.resolve({ talent: true });
  await pending;
  assert.equal(h.api.state.slot, 0);
  assert.equal(h.S.view, null);
  assert.equal(h.views.length, 0);
});

test('401事件使旧载入失效且不会重新启动循环', async () => {
  const h = harness(), old = deferred();
  h.api.state.token = '';
  h.boot();
  h.api.state.token = 'first';
  h.api.loadSave = () => old.promise;
  const pending = h.enterGame(1);
  await flush();
  h.api.clearAuth();
  h.events.get('gongpu:unauthorized')();
  old.resolve({ talent: true });
  await pending;
  assert.equal(h.views.length, 0);
  assert.equal(h.intervals.size, 0);
  assert.equal(h.api.state.slot, 0);
});
