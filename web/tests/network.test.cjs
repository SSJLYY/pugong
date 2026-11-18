'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../js/app.js'), 'utf8');
function harness() {
  const nodes = new Map();
  const node = id => {
    if (!nodes.has(id)) nodes.set(id, { style: {}, setAttribute(name, value) { this[name] = value; } });
    return nodes.get(id);
  };
  const api = { state: { token: 'token', user: { id: 1 }, slot: 1 } };
  const window = { GP_API: api };
  const marker = "  if (document.readyState === 'loading') {";
  assert.equal(source.split(marker).length, 2);
  vm.runInNewContext(source.replace(marker, `
    toast = applyView = loadBulletin = () => {};
    window.__app = { S, call, tick, resetPage };
  ` + marker), { window, document: { readyState: 'loading', hidden: false,
    getElementById: node, addEventListener() {} }, navigator: {}, console, setTimeout, clearTimeout },
  { filename: 'app.js' });
  window.__app.S._metaAt = Date.now();
  return { ...window.__app, api, node };
}
const error = code => Object.assign(new Error(code), { code });
for (const operation of ['call', 'tick']) {
  test(`${operation}：断线及超时连续变黄、第三次变红、成功复绿`, async () => {
    const h = harness();
    async function send(code) {
      h.S._nextPollAt = 0;
      if (operation === 'call') {
        if (code) await assert.rejects(h.call(() => Promise.reject(error(code))), { code });
        else await h.call(() => Promise.resolve({}));
      } else {
        h.api.gameState = () => code ? Promise.reject(error(code)) : Promise.resolve({});
        await h.tick();
      }
    }
    await send('ERR_NET');
    assert.equal(h.node('netState').className, 'net-warn');
    assert.equal(h.S._netFails, 1);
    await send('ERR_TIMEOUT');
    assert.equal(h.node('netState').className, 'net-warn');
    assert.equal(h.S._netFails, 2);
    await send('ERR_NET');
    assert.equal(h.node('netState').className, 'net-bad');
    assert.match(h.node('netState')['aria-label'], /连接中断/);
    await send();
    assert.equal(h.node('netState').className, 'netok');
    assert.equal(h.S._netFails, 0);
    await send('ERR_TIMEOUT');
    assert.equal(h.node('netState').className, 'net-warn');
    assert.equal(h.S._netFails, 1);
  });

  test(`${operation}：业务4xx错误表示连接正常，清除断线累计`, async () => {
    const h = harness();
    for (const code of ['ERR_NET', 'FORBIDDEN', 'BAD_REQUEST']) {
      h.S._nextPollAt = 0;
      if (operation === 'call') {
        await assert.rejects(h.call(() => Promise.reject(error(code))), { code });
      } else {
        h.api.gameState = () => Promise.reject(error(code));
        await h.tick();
      }
      assert.equal(h.node('netState').className, code === 'ERR_NET' ? 'net-warn' : 'netok');
    }
    assert.equal(h.S._netFails, 0);
  });
}

test('旧页面失败不能污染新页面网络状态', async () => {
  const h = harness(); let reject;
  const pending = h.call(() => new Promise((yes, no) => { reject = no; }));
  const result = assert.rejects(pending, { code: 'PAGE_CHANGED' });
  await new Promise(resolve => setImmediate(resolve));
  h.resetPage();
  await h.call(() => Promise.resolve({}));
  reject(error('ERR_NET')); await result;
  assert.equal(h.node('netState').className, 'netok');
  assert.equal(h.S._netFails, 0);
});
