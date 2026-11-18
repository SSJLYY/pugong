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
const flush = () => new Promise(resolve => setImmediate(resolve));
async function harness(options = {}) {
  const nodes = new Map(), notices = [], views = [];
  let starts = 0;
  const node = id => {
    if (!nodes.has(id)) nodes.set(id, { style: {}, disabled: false,
      classList: { add() {}, remove() {}, toggle() {}, contains() { return false; } },
      addEventListener() {} });
    return nodes.get(id);
  };
  const api = { state: { token: 'token', user: { id: 1 }, slot: 1 },
    socialMe: options.socialMe || (async () => ({ incense: 2 })), incenseRevive: async () => ({}),
    gameState: async slot => ({ slot, alive: true }) };
  const window = { GP_API: api, addEventListener() {}, __notices: notices, __views: views,
    __start() { starts++; } };
  const context = { window, document: { readyState: 'loading', getElementById: node,
    querySelectorAll: () => [], addEventListener() {} }, navigator: {}, console,
    setTimeout, clearTimeout, setInterval, clearInterval };
  const marker = "  if (document.readyState === 'loading') {";
  assert.equal(source.split(marker).length, 2, '测试入口必须唯一');
  const injection = `
    applyView = v => window.__views.push(v);
    toast = msg => window.__notices.push(msg);
    setNet = sfx = stopLoop = () => {};
    fmt = v => String(v || 0);
    startLoop = () => window.__start();
    window.__app = { S, renderDeath, loadWorldGrave, resetPage, call };
  `;
  vm.runInNewContext(source.replace(marker, injection + marker), context, { filename: 'app.js' });
  window.__app.renderDeath({ panel: {}, stats: {}, gen: 1 });
  await flush();
  return { ...window.__app, api, node, notices, views, starts: () => starts,
    click: () => node('btnIncense').onclick() };
}

test('连续点击只提交一次复活并使用原槽位刷新', async () => {
  const h = await harness(), post = deferred(), sent = [];
  h.api.incenseRevive = slot => { sent.push(['POST', slot]); return post.promise; };
  h.api.gameState = async slot => { sent.push(['GET', slot]); return { alive: true }; };
  const pending = h.click();
  assert.equal(h.node('btnIncense').disabled, true);
  await h.click();
  await flush();
  assert.deepEqual(sent, [['POST', 1]]);
  post.resolve({});
  await pending;
  assert.deepEqual(sent, [['POST', 1], ['GET', 1]]);
  assert.equal(h.views.length, 1);
  assert.equal(h.starts(), 1);
  assert.equal(h.S.wasDead, false);
  assert.equal(h.node('deathMask').style.display, 'none');
  assert.equal(h.node('btnIncense').disabled, false);
});

for (const outcome of ['resolve', 'reject']) {
  test(`复活期间换档后旧请求${outcome}不影响新页面`, async () => {
    const h = await harness(), post = deferred(), reads = [];
    h.api.incenseRevive = () => post.promise;
    h.api.gameState = async slot => { reads.push(slot); return {}; };
    const pending = h.click();
    await flush();
    h.resetPage(); h.api.state.slot = 2;
    h.node('deathMask').style.display = 'new-page';
    h.node('btnIncense').disabled = true;
    if (outcome === 'resolve') post.resolve({}); else post.reject(new Error('旧请求失败'));
    await pending;
    assert.deepEqual(reads, []);
    assert.deepEqual(h.notices, []);
    assert.deepEqual(h.views, []);
    assert.equal(h.starts(), 0);
    assert.equal(h.node('deathMask').style.display, 'new-page');
    assert.equal(h.node('btnIncense').disabled, true);
  });
}

test('刷新期间切页后旧状态不应用或启动轮询', async () => {
  const h = await harness(), state = deferred(), reads = [];
  h.api.gameState = slot => { reads.push(slot); return state.promise; };
  const pending = h.click();
  await flush();
  h.resetPage(); h.api.state.slot = 2;
  h.notices.length = 0;
  h.node('deathMask').style.display = 'new-page';
  state.resolve({ slot: 1 });
  await pending;
  assert.deepEqual(reads, [1]);
  assert.deepEqual(h.notices, []);
  assert.deepEqual(h.views, []);
  assert.equal(h.starts(), 0);
  assert.equal(h.node('deathMask').style.display, 'new-page');
});

test('旧页面保留的点击入口不能提交复活', async () => {
  const h = await harness();
  let posts = 0;
  h.api.incenseRevive = async () => { posts++; };
  h.resetPage();
  await h.click();
  assert.equal(posts, 0);
});

test('排队复活在换档后取消且不请求新槽位', async () => {
  const h = await harness(), first = deferred();
  const queued = h.call(() => first.promise);
  const rejected = assert.rejects(queued, { code: 'PAGE_CHANGED' });
  await flush();
  let posts = 0;
  h.api.incenseRevive = async () => { posts++; };
  const pending = h.click();
  h.resetPage(); h.api.state.slot = 2;
  first.resolve({});
  await Promise.all([pending, rejected]);
  assert.equal(posts, 0);
  assert.deepEqual(h.notices, []);
});

test('复活成功但刷新失败会恢复轮询并提示错误', async () => {
  const h = await harness();
  h.api.gameState = async () => { throw new Error('刷新断线'); };
  await h.click();
  assert.equal(h.starts(), 1);
  assert.equal(h.views.length, 0);
  assert.equal(h.node('deathMask').style.display, 'none');
  assert.equal(h.S.wasDead, false);
  assert.equal(h.node('btnIncense').disabled, false);
  assert.equal(h.notices.at(-1), '刷新断线');
});

test('复活POST失败恢复按钮并保留死亡页，不自动重试冲突', async () => {
  const h = await harness();
  let posts = 0, reads = 0;
  h.api.incenseRevive = async () => {
    posts++;
    throw Object.assign(new Error('复活冲突'), { code: 'CONFLICT' });
  };
  h.api.gameState = async () => { reads++; return {}; };
  await h.click();
  assert.equal(posts, 1);
  assert.equal(reads, 0);
  assert.equal(h.starts(), 0);
  assert.equal(h.S.wasDead, true);
  assert.equal(h.node('deathMask').style.display, 'flex');
  assert.equal(h.node('btnIncense').disabled, false);
  assert.deepEqual(h.notices, ['复活冲突']);
  await h.click();
  assert.equal(posts, 2);
});

test('余额请求晚于复活点击返回时仍保持忙碌禁用', async () => {
  const balance = deferred(), post = deferred();
  const h = await harness({ socialMe: () => balance.promise });
  let posts = 0;
  h.api.incenseRevive = () => { posts++; return post.promise; };
  const pending = h.click();
  await flush();
  balance.resolve({ incense: 5 });
  await flush();
  assert.equal(h.node('btnIncense').disabled, true);
  await h.click();
  assert.equal(posts, 1);
  post.resolve({});
  await pending;
});

for (const outcome of ['resolve', 'reject']) {
  test(`换页后旧余额请求${outcome}不修改新页`, async () => {
    const balance = deferred();
    const h = await harness({ socialMe: () => balance.promise });
    h.resetPage(); h.api.state.slot = 2;
    h.node('deathIncenseInfo').textContent = 'new';
    h.node('btnIncense').disabled = false;
    if (outcome === 'resolve') balance.resolve({ incense: 0 });
    else balance.reject(new Error('旧余额失败'));
    await flush();
    assert.equal(h.node('deathIncenseInfo').textContent, 'new');
    assert.equal(h.node('btnIncense').disabled, false);
  });
}

async function graveHarness() {
  const h = await harness();
  const button = { dataset: { grave: '7' }, disabled: false,
    addEventListener(name, fn) { this[name] = fn; } };
  h.node('worldGraveBox').querySelectorAll = () => [button];
  let loads = 0;
  h.api.worldGraveyard = async () => {
    loads++;
    return { rows: [{ id: 7, owner: '好友', gen: 1 }] };
  };
  await h.loadWorldGrave();
  return { ...h, button, loads: () => loads };
}

test('祭奠双击只发一次POST，成功后刷新当前墓园', async () => {
  const h = await graveHarness(), post = deferred(), sent = [];
  h.api.tribute = (grave, slot) => { sent.push([grave, slot]); return post.promise; };
  const pending = h.button.click();
  await h.button.click();
  await flush();
  assert.deepEqual(sent, [[7, 1]]);
  assert.equal(h.button.disabled, true);
  post.resolve({ owner: '好友' });
  await pending;
  assert.equal(h.loads(), 2);
  assert.equal(h.button.disabled, false);
});

test('祭奠失败恢复按钮，允许再次点击但不自动重试', async () => {
  const h = await graveHarness();
  let posts = 0;
  h.api.tribute = async () => {
    posts++;
    throw Object.assign(new Error('祭奠失败'), { code: 'CONFLICT' });
  };
  await h.button.click();
  assert.equal(posts, 1);
  assert.equal(h.button.disabled, false);
  assert.equal(h.loads(), 1);
  assert.deepEqual(h.notices, ['祭奠失败']);
  await h.button.click();
  assert.equal(posts, 2);
});

test('祭奠旧按钮换槽后不能发POST', async () => {
  const h = await graveHarness();
  let posts = 0;
  h.api.tribute = async () => { posts++; };
  h.api.state.slot = 2;
  await h.button.click();
  assert.equal(posts, 0);
  assert.equal(h.loads(), 1);
});

for (const outcome of ['resolve', 'reject']) {
  test(`祭奠在途换槽后${outcome}不提示、不刷新或解锁新页`, async () => {
    const h = await graveHarness(), post = deferred(), sent = [];
    h.api.tribute = (grave, slot) => { sent.push([grave, slot]); return post.promise; };
    const pending = h.button.click();
    await flush();
    h.resetPage(); h.api.state.slot = 2;
    if (outcome === 'resolve') post.resolve({ owner: '好友' });
    else post.reject(new Error('旧祭奠失败'));
    await pending;
    assert.deepEqual(sent, [[7, 1]]);
    assert.equal(h.loads(), 1);
    assert.deepEqual(h.notices, []);
    assert.equal(h.button.disabled, true);
  });
}

test('墓园加载期间换槽后不渲染旧列表', async () => {
  const h = await graveHarness(), load = deferred();
  h.api.worldGraveyard = () => load.promise;
  const pending = h.loadWorldGrave();
  h.api.state.slot = 2;
  h.node('worldGraveBox').innerHTML = 'new-page';
  load.resolve({ rows: [] });
  await pending;
  assert.equal(h.node('worldGraveBox').innerHTML, 'new-page');
});
