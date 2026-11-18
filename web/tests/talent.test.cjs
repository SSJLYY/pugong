'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../js/app.js'), 'utf8');
const pool = [{ id: 'eternal', name: '永续', emoji: '*', desc: '永久增益', topTier: true }];
const result = { ...pool[0], talent: 'eternal' };
const undrawn = () => ({ drawn: false, pool });
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
const flush = () => new Promise(resolve => setImmediate(resolve));
function harness() {
  const nodes = new Map(), intervals = new Set(), timers = [], notices = [];
  function makeNode() {
    return { style: {}, dataset: {}, children: [], disabled: false,
      classList: { add() {}, remove() {}, toggle() {}, contains() { return false; } },
      addEventListener(name, fn) { this[name] = fn; }, setAttribute() {},
      appendChild(child) { this.children.push(child); },
      set innerHTML(value) { this.html = value; this.children = []; },
      get innerHTML() { return this.html || ''; } };
  }
  const node = id => {
    if (!nodes.has(id)) nodes.set(id, makeNode());
    return nodes.get(id);
  };
  const api = { state: { token: 'token', user: { id: 1 }, slot: 1 },
    setSlot(slot) { this.state.slot = slot; }, talentView: async () => undrawn(),
    gameState: async () => ({ talent: 'eternal' }) };
  let tutorialStarts = 0;
  const window = { GP_API: api, addEventListener() {},
    __tutorial: { start() { tutorialStarts++; } } };
  const context = { window, document: { readyState: 'loading', hidden: false,
    getElementById: node, querySelectorAll: () => [], createElement: makeNode, addEventListener() {} },
    navigator: {}, console,
    setTimeout(fn) { timers.push(fn); return fn; }, clearTimeout() {},
    setInterval(fn) { intervals.add(fn); return fn; }, clearInterval(fn) { intervals.delete(fn); } };
  const marker = "  if (document.readyState === 'loading') {";
  assert.equal(source.split(marker).length, 2, '测试入口必须唯一');
  window.__notices = notices;
  const injection = `
    applyView = () => {};
    toast = msg => window.__notices.push(msg);
    setNet = () => {};
    window.__app = { S, showTalentRitual, resetPage, enterGame, initAuth };
  `;
  vm.runInNewContext(source.replace(marker, injection + marker), context, { filename: 'app.js' });
  window.__app.S.meta = {};
  window.__app.S._metaAt = Date.now();
  return { ...window.__app, api, node, intervals, timers, notices,
    tutorialStarts: () => tutorialStarts, drawButton: () => node('talentChoices').children[0] };
}

test('抽取首次失败后恢复按钮，重试可以成功', async () => {
  const h = harness();
  let draws = 0;
  h.api.drawTalent = async () => { if (++draws === 1) throw new Error('断线'); return { result }; };
  await h.showTalentRitual();
  const button = h.drawButton();
  await button.click();
  assert.equal(button.disabled, false);
  assert.equal(h.intervals.size, 0);
  await button.click();
  assert.equal(draws, 2);
  assert.match(h.drawButton().innerHTML, /永续/);
});

test('成功响应丢失后按奖池恢复结果，确认后恢复轮询且防重复确认', async () => {
  const h = harness();
  let queries = 0, draws = 0;
  h.api.talentView = async () => ++queries === 1 ? undrawn() : { drawn: true, talent: 'eternal', pool };
  h.api.drawTalent = async () => { draws++; throw new Error('响应丢失'); };
  await h.showTalentRitual();
  await h.drawButton().click();
  assert.equal(draws, 1);
  assert.match(h.drawButton().innerHTML, /永续/);
  assert.match(h.drawButton().className, /eternal/);
  assert.notEqual(h.node('talentRain').innerHTML, '');
  const confirm = h.node('btnAwakeGo').click;
  confirm(); confirm();
  assert.equal(h.node('talentMask').style.display, 'none');
  assert.equal(h.node('talentChoices').children.length, 0);
  assert.equal(h.intervals.size, 1);
  assert.equal(h.timers.length, 1);
});

test('对账也失败时捕获异常并保留重试入口', async () => {
  const h = harness();
  let queries = 0;
  h.api.talentView = async () => { if (++queries > 1) throw new Error('查询断线'); return undrawn(); };
  h.api.drawTalent = async () => { throw new Error('抽取断线'); };
  await h.showTalentRitual();
  const button = h.drawButton();
  await assert.doesNotReject(button.click());
  assert.equal(button.disabled, false);
  assert.match(h.notices.at(-1), /请重试/);
  await assert.doesNotReject(button.click());
  assert.equal(queries, 3);
});

test('重复点击仅提交一次抽取', async () => {
  const h = harness(), pending = deferred();
  let draws = 0;
  h.api.drawTalent = () => { draws++; return pending.promise; };
  await h.showTalentRitual();
  const button = h.drawButton(), first = button.click();
  await button.click();
  await flush();
  assert.equal(draws, 1);
  pending.resolve({ result });
  await first;
});

test('切页后旧查询成功或失败均不能弹遮罩或恢复循环', async () => {
  for (const fail of [false, true]) {
    const h = harness(), pending = deferred();
    h.api.talentView = () => pending.promise;
    const showing = h.showTalentRitual();
    h.resetPage(); h.api.setSlot(2);
    if (fail) pending.reject(new Error('旧请求失败')); else pending.resolve(undrawn());
    await showing;
    assert.equal(h.node('talentMask').style.display, 'none');
    assert.equal(h.intervals.size, 0);
  }
});

test('旧抽取返回和旧按钮在换档后不发送请求或恢复结果', async () => {
  const h = harness(), pending = deferred();
  let draws = 0, queries = 0;
  h.api.talentView = async () => { queries++; return undrawn(); };
  h.api.drawTalent = () => { draws++; return pending.promise; };
  await h.showTalentRitual();
  const button = h.drawButton(), clicking = button.click();
  await flush();
  h.resetPage(); h.api.setSlot(2);
  pending.reject(new Error('旧响应丢失'));
  await clicking;
  await button.click();
  assert.equal(draws, 1);
  assert.equal(queries, 1);
  assert.equal(h.node('talentMask').style.display, 'none');
  assert.equal(h.intervals.size, 0);
});

test('对账返回前切页，不展示旧结果', async () => {
  const h = harness(), pending = deferred();
  let queries = 0;
  h.api.talentView = () => ++queries === 1 ? undrawn() : pending.promise;
  h.api.drawTalent = async () => { throw new Error('响应丢失'); };
  await h.showTalentRitual();
  const clicking = h.drawButton().click();
  await flush();
  h.resetPage();
  pending.resolve({ drawn: true, talent: 'eternal', pool });
  await clicking;
  assert.equal(h.node('talentChoices').children.length, 0);
  assert.equal(h.intervals.size, 0);
});

test('退出清理结果和金雨，旧确认及延迟引导不能影响新页面', async () => {
  const h = harness();
  h.api.drawTalent = async () => ({ result });
  await h.showTalentRitual();
  await h.drawButton().click();
  const confirm = h.node('btnAwakeGo').click;
  confirm();
  h.resetPage();
  confirm();
  h.timers.forEach(fn => fn());
  assert.equal(h.tutorialStarts(), 0);
  assert.equal(h.intervals.size, 0);
  assert.equal(h.node('talentChoices').children.length, 0);
});

test('退出结果页立即清理卡片，旧确认不能开启轮询或引导', async () => {
  const h = harness();
  h.api.drawTalent = async () => ({ result });
  await h.showTalentRitual();
  await h.drawButton().click();
  const confirm = h.node('btnAwakeGo').click;
  h.resetPage();
  confirm();
  assert.equal(h.node('talentChoices').children.length, 0);
  assert.equal(h.node('talentMask').style.display, 'none');
  assert.equal(h.intervals.size, 0);
  assert.equal(h.timers.length, 0);
});

test('普通天赋对账恢复名称、描述和普通结果样式', async () => {
  const h = harness(), ordinary = { id: 'swift', name: '迅捷', emoji: '+', desc: '速度增加', topTier: false };
  let queries = 0;
  h.api.talentView = async () => ++queries === 1 ? undrawn() : { drawn: true, talent: 'swift', pool: [ordinary] };
  h.api.drawTalent = async () => { throw new Error('响应丢失'); };
  await h.showTalentRitual();
  await h.drawButton().click();
  assert.match(h.drawButton().innerHTML, /迅捷/);
  assert.match(h.drawButton().innerHTML, /速度增加/);
  assert.doesNotMatch(h.drawButton().className, /eternal/);
});

test('直接改变槽位也使旧抽取按钮失效', async () => {
  const h = harness();
  let draws = 0;
  h.api.drawTalent = async () => { draws++; return { result }; };
  await h.showTalentRitual();
  const button = h.drawButton();
  h.api.setSlot(2);
  await button.click();
  assert.equal(draws, 0);
});

test('首次进入未觉醒存档，查询和展示仪式期间始终暂停轮询', async () => {
  const h = harness(), pending = deferred();
  h.api.loadSave = async () => ({ talent: null });
  h.api.talentView = () => pending.promise;
  const entering = h.enterGame(1);
  await flush();
  assert.equal(h.intervals.size, 0);
  pending.resolve(undrawn());
  await entering;
  assert.equal(h.node('talentMask').style.display, 'flex');
  assert.equal(h.intervals.size, 0);
});
