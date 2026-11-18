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
function harness() {
  const nodes = new Map(), notices = [], views = [];
  function makeNode() {
    return { style: {}, dataset: {}, disabled: false, value: '', innerHTML: '',
      addEventListener(name, fn) { this[name] = fn; }, setAttribute() {} };
  }
  const node = id => { if (!nodes.has(id)) nodes.set(id, makeNode()); return nodes.get(id); };
  node('matFrom').value = 'pelt'; node('matTo').value = 'ore';
  node('craftButton').dataset.craft = 'sword';
  node('tabCraft').querySelector = selector => ['#btnMatExchange', '#matFrom', '#matTo'].includes(selector) ? node(selector.slice(1)) : null;
  node('tabCraft').querySelectorAll = selector => selector === '[data-craft]' ? [node('craftButton')] : [];
  const api = { state: { token: 'token', user: { id: 1 }, slot: 1 }, craftBoard: async () => ({ crafts: [] }) };
  const window = { GP_API: api, __notices: notices, __views: views };
  const marker = "  if (document.readyState === 'loading') {";
  assert.equal(source.split(marker).length, 2);
  vm.runInNewContext(source.replace(marker, `
    fmt = value => String(value);
    toast = msg => window.__notices.push(msg);
    applyView = v => window.__views.push(v);
    window.__app = { S, loadCrafts, resetPage, call };
  ` + marker), { window, document: { readyState: 'loading', getElementById: node, addEventListener() {} },
    navigator: {}, console, setTimeout, clearTimeout }, { filename: 'app.js' });
  return { ...window.__app, api, node, notices, views };
}

test('兑换双击仅提交一次POST，使用原槽位并应用回执刷新材料', async () => {
  const h = harness(), pending = deferred(), sent = []; let boards = 0;
  h.api.craftBoard = async slot => { boards++; assert.equal(slot, 1); return { crafts: [] }; };
  h.api.matExchange = (slot, from, to) => { sent.push([slot, from, to]); return pending.promise; };
  await h.loadCrafts();
  const button = h.node('btnMatExchange'), clicking = button.click();
  assert.equal(button.disabled, true);
  await button.click(); await flush();
  assert.deepEqual(sent, [[1, 'pelt', 'ore']]);
  const result = { result: { fromHave: 6, toHave: 2 } };
  pending.resolve(result); await clicking;
  assert.deepEqual(h.views, [result]);
  assert.equal(boards, 2);
  assert.match(h.notices.at(-1), /余 6.*现有 2/);
});

test('兑换失败恢复按钮供手动重试，CONFLICT不自动重复POST', async () => {
  const h = harness(); let sends = 0;
  h.api.matExchange = async () => {
    if (++sends === 1) throw Object.assign(new Error('冲突'), { code: 'CONFLICT' });
    return { result: { fromHave: 0, toHave: 1 } };
  };
  await h.loadCrafts();
  await h.node('btnMatExchange').click();
  assert.equal(sends, 1);
  assert.equal(h.node('btnMatExchange').disabled, false);
  assert.deepEqual(h.notices, ['冲突']);
  await h.node('btnMatExchange').click();
  assert.equal(sends, 2);
  assert.equal(h.views.length, 1);
});

for (const fail of [false, true]) {
  test(`兑换在途换档后${fail ? '失败' : '成功'}不提示、不应用视图、不重载`, async () => {
    const h = harness(), pending = deferred(); let boards = 0, sends = 0;
    h.api.craftBoard = async () => { boards++; return { crafts: [] }; };
    h.api.matExchange = () => { sends++; return pending.promise; };
    await h.loadCrafts();
    const button = h.node('btnMatExchange'), clicking = button.click(); await flush();
    h.resetPage(); h.api.state.slot = 2;
    if (fail) pending.reject(new Error('旧兑换失败')); else pending.resolve({ result: {} });
    await clicking; await button.click();
    assert.equal(sends, 1);
    assert.equal(boards, 1);
    assert.deepEqual(h.notices, []);
    assert.deepEqual(h.views, []);
  });
}

test('排队兑换在换档后取消，不向新槽位发POST', async () => {
  const h = harness(), pending = deferred(); let sends = 0;
  await h.loadCrafts();
  const blocking = h.call(() => pending.promise);
  const result = assert.rejects(blocking, { code: 'PAGE_CHANGED' });
  await flush();
  h.api.matExchange = async () => { sends++; return {}; };
  const clicking = h.node('btnMatExchange').click();
  h.resetPage(); h.api.state.slot = 2;
  pending.resolve({}); await result; await clicking;
  assert.equal(sends, 0);
  assert.deepEqual(h.notices, []);
});

test('制作列表旧请求换档后返回不会重绘新页或绑定旧回调', async () => {
  const h = harness(), pending = deferred();
  h.api.craftBoard = slot => slot === 1 ? pending.promise : Promise.resolve({ crafts: [] });
  const old = h.loadCrafts();
  h.resetPage(); h.api.state.slot = 2;
  await h.loadCrafts();
  const currentClick = h.node('btnMatExchange').click;
  h.node('craftList').innerHTML = '新页内容';
  pending.resolve({ crafts: [] }); await old;
  assert.equal(h.node('craftList').innerHTML, '新页内容');
  assert.equal(h.node('btnMatExchange').click, currentClick);
});

for (const result of [{ itemName: '星陨长剑', quality: 0 }, { itemName: '大恢复药剂' }]) {
  test(`锻造按嵌套result显示${result.quality != null ? '装备真实名称、品质和背包' : '药剂真实名称和药剂栏'}`, async () => {
    const h = harness();
    h.api.craftDo = async (slot, code) => {
      assert.equal(slot, 1); assert.equal(code, 'sword'); return { result };
    };
    await h.loadCrafts(); await h.node('craftButton').click();
    assert.match(h.notices.at(-1), new RegExp(result.itemName));
    assert.match(h.notices.at(-1), result.quality != null ? /品质 0.*已入背包/ : /已放入药剂栏/);
  });
}

test('旧锻造按钮和在途回执换档后不能提示或刷新', async () => {
  const h = harness(), pending = deferred(); let sends = 0, boards = 0;
  h.api.craftBoard = async () => { boards++; return { crafts: [] }; };
  h.api.craftDo = () => { sends++; return pending.promise; };
  await h.loadCrafts();
  const button = h.node('craftButton'), clicking = button.click();
  h.resetPage(); h.api.state.slot = 2;
  pending.resolve({ result: { itemName: '旧装备', quality: 3 } });
  await clicking; await button.click();
  assert.equal(sends, 1); assert.equal(boards, 1);
  assert.deepEqual(h.notices, []);
});
