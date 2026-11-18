'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../js/app.js'), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
const options = { gen: 1, options: [{ key: 'hp', name: '生命', emoji: '+', step: 1, disabled: false }] };
function harness() {
  const nodes = new Map(), timers = [], intervals = new Set(), views = [], notices = [], effects = [];
  function makeNode() {
    return { style: {}, children: [], disabled: false,
      classList: { contains: () => false }, setAttribute() {},
      addEventListener(name, fn) { this[name] = fn; },
      appendChild(child) { this.children.push(child); }, remove() { this.removed = true; },
      set innerHTML(value) { this.html = value; this.children = []; },
      get innerHTML() { return this.html || ''; } };
  }
  const node = id => { if (!nodes.has(id)) nodes.set(id, makeNode()); return nodes.get(id); };
  const api = { state: { token: 'token', user: { id: 1 }, slot: 1 },
    blessOptions: async () => options, rebirth: async () => ({ gen: 2 }),
    gameState: () => new Promise(() => {}) };
  const window = { GP_API: api, __views: views, __notices: notices };
  const context = { window, document: { readyState: 'loading', hidden: false,
    getElementById: node, createElement: makeNode, addEventListener() {},
    body: { appendChild(el) { effects.push(el); } } }, navigator: {}, console, confirm: () => true,
    setTimeout(fn, ms) { timers.push({ fn, ms }); return fn; }, clearTimeout() {},
    setInterval(fn) { intervals.add(fn); return fn; }, clearInterval(fn) { intervals.delete(fn); } };
  const marker = "  if (document.readyState === 'loading') {";
  assert.equal(source.split(marker).length, 2);
  vm.runInNewContext(source.replace(marker, `
    applyView = v => window.__views.push(v);
    toast = msg => window.__notices.push(msg);
    window.__app = { S, showRebirth, resetPage };
  ` + marker), context, { filename: 'app.js' });
  window.__app.S._metaAt = Date.now();
  return { ...window.__app, api, node, views, notices, intervals, effects,
    animate() { timers.splice(0).forEach(timer => timer.fn()); },
    button: () => node('rebirthChoices').children[0] };
}
async function open(h, view = {}) { const pending = h.showRebirth(view); h.animate(); await pending; }

test('动画等待期间换档或退出不请求新槽位，立即清理光柱和遮罩', async () => {
  for (const slot of [0, 2]) {
    const h = harness(); let requests = 0;
    h.api.blessOptions = async () => { requests++; return options; };
    const showing = h.showRebirth({});
    h.resetPage(); h.api.state.slot = slot;
    assert.equal(h.effects[0].removed, true);
    h.animate(); await showing;
    assert.equal(requests, 0);
    assert.equal(h.node('rebirthMask').style.display, 'none');
    assert.equal(h.intervals.size, 0);
  }
});

test('选项请求在途换档后成功或异常均不显示旧仪式、不重启轮询', async () => {
  for (const fail of [false, true]) {
    const h = harness(), pending = deferred(), slots = [];
    h.api.blessOptions = slot => { slots.push(slot); return pending.promise; };
    const showing = h.showRebirth({}); h.animate(); await flush();
    h.resetPage(); h.api.state.slot = 2;
    if (fail) pending.reject(new Error('旧请求失败')); else pending.resolve(options);
    await showing;
    assert.deepEqual(slots, [1]);
    assert.equal(h.node('rebirthMask').style.display, 'none');
    assert.equal(h.intervals.size, 0);
  }
});

test('正常转生固定槽位、防重复点击并恢复轮询，集齐赋能保留强化提示', async () => {
  const h = harness(), pending = deferred(), sent = [];
  h.api.rebirth = (slot, key) => { sent.push([slot, key]); return pending.promise; };
  await open(h, { allBlessed: true });
  assert.match(h.node('rebirthTip').textContent, /强化纪元/);
  const button = h.button(), clicking = button.click();
  await button.click(); await flush();
  assert.deepEqual(sent, [[1, 'hp']]);
  pending.resolve({ gen: 2 }); await clicking;
  assert.deepEqual(h.views, [{ gen: 2 }]);
  assert.equal(h.S.rebirthShown, false);
  assert.equal(h.node('rebirthMask').style.display, 'none');
  assert.equal(h.intervals.size, 1);
});

test('选项失败恢复轮询，转生失败保留可重试按钮且没有未捕获异常', async () => {
  const h = harness();
  h.api.blessOptions = async () => { throw Object.assign(new Error('断网'), { code: 'ERR_NET' }); };
  await open(h);
  assert.equal(h.S.rebirthShown, false);
  assert.equal(h.intervals.size, 1);
  h.S.busy = false;
  h.api.blessOptions = async () => options;
  await open(h);
  h.api.rebirth = async () => { throw Object.assign(new Error('超时'), { code: 'ERR_TIMEOUT' }); };
  await assert.doesNotReject(h.button().click());
  assert.equal(h.button().disabled, false);
  assert.equal(h.node('rebirthMask').style.display, 'flex');
  assert.equal(h.intervals.size, 0);
  h.api.rebirth = async () => ({ gen: 2 });
  await h.button().click();
  assert.equal(h.intervals.size, 1);
});

test('转生请求在途换档后成功或失败均不更新页面，旧按钮不再提交', async () => {
  for (const fail of [false, true]) {
    const h = harness(), pending = deferred(); let sends = 0;
    h.api.rebirth = () => { sends++; return pending.promise; };
    await open(h);
    const button = h.button(), clicking = button.click(); await flush();
    h.resetPage(); h.api.state.slot = 2;
    if (fail) pending.reject(new Error('旧请求失败')); else pending.resolve({ gen: 2 });
    await clicking; await button.click();
    assert.equal(sends, 1);
    assert.equal(h.views.length, 0);
    assert.equal(h.intervals.size, 0);
    assert.equal(h.node('rebirthMask').style.display, 'none');
  }
});

test('同页新仪式使旧动画、旧选项和旧按钮失效', async () => {
  const h = harness(), pending = deferred(); let queries = 0, sends = 0;
  h.api.blessOptions = () => ++queries === 1 ? pending.promise : Promise.resolve(options);
  const old = h.showRebirth({}); h.animate(); await flush();
  const replacement = h.showRebirth({}); h.animate();
  pending.reject(new Error('旧查询失败')); await old; await replacement;
  assert.equal(h.node('rebirthMask').style.display, 'flex');
  assert.equal(h.intervals.size, 0);
  const oldButton = h.button();
  h.api.rebirth = async () => { sends++; return { gen: 2 }; };
  const fresh = h.showRebirth({});
  await oldButton.click();
  assert.equal(sends, 0);
  assert.equal(h.effects.at(-2).removed, true);
  h.animate(); await fresh;
  const first = h.showRebirth({}), second = h.showRebirth({});
  h.animate(); await Promise.all([first, second]);
  assert.equal(queries, 4);
});
