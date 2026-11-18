'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../js/api.js'), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

function harness() {
  let now = 0;
  let nextTimer = 0;
  const timers = new Map();
  const storage = new Map();
  const calls = [];
  const events = [];
  const window = { dispatchEvent: event => events.push(event.type) };
  const context = vm.createContext({
    window,
    location: { pathname: '/gongpu/' },
    localStorage: {
      getItem: key => storage.get(key) ?? null,
      setItem: (key, value) => storage.set(key, String(value)),
      removeItem: key => storage.delete(key)
    },
    performance: { now: () => now },
    AbortController,
    CustomEvent: class { constructor(type) { this.type = type; } },
    setTimeout(callback, delay) {
      const id = ++nextTimer;
      timers.set(id, { callback, due: now + delay });
      return id;
    },
    clearTimeout: id => timers.delete(id),
    fetch(url, options) {
      const body = deferred();
      const call = {
        url,
        options,
        bodyStarted: false,
        aborted: false,
        respond(data = { ok: true }, status = 200, token = null) {
          response.status = status;
          response.ok = status >= 200 && status < 300;
          response.headers.get = name => name === 'X-New-Token' ? token : null;
          body.resolve(JSON.stringify(data));
        }
      };
      const response = {
        status: 200,
        ok: true,
        headers: { get: () => null },
        text() { call.bodyStarted = true; return body.promise; }
      };
      options.signal.addEventListener('abort', () => {
        call.aborted = true;
        const error = new Error('请求已中止');
        error.name = 'AbortError';
        body.reject(error);
      }, { once: true });
      calls.push(call);
      return Promise.resolve(response);
    }
  });
  vm.runInContext(source, context, { filename: 'api.js' });
  return {
    api: window.GP_API,
    calls,
    events,
    storage,
    timers,
    async advance(ms) {
      const target = now + ms;
      for (;;) {
        const next = [...timers.entries()]
          .filter(([, timer]) => timer.due <= target)
          .sort((a, b) => a[1].due - b[1].due)[0];
        if (!next) break;
        now = next[1].due;
        timers.delete(next[0]);
        next[1].callback();
        await flush();
      }
      now = target;
      await flush();
    }
  };
}

function login(h, token = 'user-a') {
  h.api.setAuth({ token, userId: token, username: token });
}

function expectError(promise, code) {
  return assert.rejects(promise, error => error.code === code);
}

test('响应头已返回但正文挂起时，15秒超时中止请求并抛ERR_TIMEOUT', async () => {
  const h = harness();
  const result = expectError(h.api.health(), 'ERR_TIMEOUT');
  await flush();
  assert.equal(h.calls[0].bodyStarted, true);
  await h.advance(14999);
  assert.equal(h.calls[0].aborted, false);
  await h.advance(1);
  await result;
  assert.equal(h.calls[0].aborted, true);
  assert.equal(h.api.state.online, false);
  assert.equal(h.timers.size, 0);
});

test('四个GET正文占满并发槽，第五个排队，超时后释放并继续执行', async () => {
  const h = harness();
  const blocked = Array.from({ length: 4 }, () => expectError(h.api.health(), 'ERR_TIMEOUT'));
  const fifth = h.api.meta();
  await flush();
  assert.equal(h.calls.length, 4);
  assert.ok(h.calls.every(call => call.bodyStarted));
  await h.advance(15000);
  await Promise.all(blocked);
  assert.equal(h.calls.length, 5);
  assert.equal(h.calls[4].url, '/gongpu/api/config/meta');
  assert.equal(h.calls[4].aborted, false);
  h.calls[4].respond({ fifth: true });
  assert.equal((await fifth).fifth, true);
  assert.equal(h.timers.size, 0);
});

for (const token of [null, 'stale-renewal']) {
  test(`旧会话成功响应${token ? '携带续期' : '不携带续期'}时拒绝结果并保留新登录`, async () => {
    const h = harness();
    login(h);
    const result = expectError(h.api.gameState(1), 'ERR_SESSION');
    await flush();
    login(h, 'user-b');
    h.calls[0].respond({ oldUser: true }, 200, token);
    await result;
    assert.equal(h.api.state.token, 'user-b');
    assert.equal(h.api.state.user.id, 'user-b');
    assert.equal(h.storage.get('gongpu_token_v2'), 'user-b');
    assert.equal(h.events.length, 0);
  });
}

test('旧会话401不能清除新登录或发出unauthorized事件', async () => {
  const h = harness();
  login(h);
  const result = expectError(h.api.listSaves(), 'ERR_SESSION');
  await flush();
  login(h, 'user-b');
  h.calls[0].respond({ code: 'EXPIRED' }, 401);
  await result;
  assert.equal(h.api.state.token, 'user-b');
  assert.equal(h.storage.get('gongpu_token_v2'), 'user-b');
  assert.equal(h.events.length, 0);
});

test('当前会话有效401清除身份并发出一次unauthorized事件', async () => {
  const h = harness();
  login(h);
  const result = expectError(h.api.listSaves(), 'EXPIRED');
  await flush();
  h.calls[0].respond({ code: 'EXPIRED' }, 401);
  await result;
  assert.equal(h.api.state.token, '');
  assert.equal(h.api.state.user, null);
  assert.equal(h.storage.has('gongpu_token_v2'), false);
  assert.equal(h.storage.has('gongpu.user'), false);
  assert.deepEqual(h.events, ['gongpu:unauthorized']);
});

test('同会话并发续期时，后返回的旧请求不能覆盖已经接受的新token', async () => {
  const h = harness();
  login(h);
  const first = h.api.health();
  const second = h.api.meta();
  await flush();
  assert.ok(h.calls.every(call => call.options.headers.Authorization === 'Bearer user-a'));
  h.calls[1].respond({ order: 1 }, 200, 'renewed-token');
  await second;
  h.calls[0].respond({ order: 2 }, 200, 'older-renewal');
  await first;
  assert.equal(h.api.state.token, 'renewed-token');
  assert.equal(h.storage.get('gongpu_token_v2'), 'renewed-token');
});

test('同会话续期后旧token的401不能清除已续期身份', async () => {
  const h = harness();
  login(h);
  const renewal = h.api.health();
  const stale = expectError(h.api.listSaves(), 'EXPIRED');
  await flush();
  h.calls[0].respond({}, 200, 'renewed-token');
  await renewal;
  h.calls[1].respond({ code: 'EXPIRED' }, 401);
  await stale;
  assert.equal(h.api.state.token, 'renewed-token');
  assert.equal(h.events.length, 0);
});

test('退出再登录后，原会话排队GET拒绝且不携带新用户token发送', async () => {
  const h = harness();
  login(h);
  const active = Array.from({ length: 4 }, () => expectError(h.api.health(), 'ERR_SESSION'));
  const queued = expectError(h.api.listSaves(), 'ERR_SESSION');
  await flush();
  assert.equal(h.calls.length, 4);
  h.api.clearAuth();
  login(h, 'user-b');
  for (const call of h.calls) call.respond();
  await Promise.all([...active, queued]);
  assert.equal(h.calls.length, 4);
  const fresh = h.api.listSaves();
  await flush();
  assert.equal(h.calls.length, 5);
  assert.equal(h.calls[4].options.headers.Authorization, 'Bearer user-b');
  h.calls[4].respond({ fresh: true });
  assert.equal((await fresh).fresh, true);
});

test('GET遇429按1秒、2秒、4秒退避，第四次成功正常返回', async () => {
  const h = harness();
  const result = h.api.health();
  await flush();
  for (const [index, delay] of [1000, 2000, 4000].entries()) {
    h.calls[index].respond({ code: 'RATE_LIMIT' }, 429);
    await flush();
    await h.advance(delay - 1);
    assert.equal(h.calls.length, index + 1);
    await h.advance(1);
    assert.equal(h.calls.length, index + 2);
  }
  h.calls[3].respond({ recovered: true });
  assert.equal((await result).recovered, true);
  assert.equal(h.timers.size, 0);
});

test('GET持续429最多重试三次后保留服务端错误', async () => {
  const h = harness();
  const result = expectError(h.api.health(), 'RATE_LIMIT');
  await flush();
  for (const [index, delay] of [1000, 2000, 4000].entries()) {
    h.calls[index].respond({ code: 'RATE_LIMIT' }, 429);
    await flush();
    await h.advance(delay);
  }
  h.calls[3].respond({ code: 'RATE_LIMIT' }, 429);
  await result;
  assert.equal(h.calls.length, 4);
  assert.equal(h.timers.size, 0);
});

test('GET在429退避期间切换登录，重试拒绝且不发送新身份请求', async () => {
  const h = harness();
  login(h);
  const result = expectError(h.api.health(), 'ERR_SESSION');
  await flush();
  h.calls[0].respond({ code: 'RATE_LIMIT' }, 429);
  await flush();
  login(h, 'user-b');
  await h.advance(1000);
  await result;
  assert.equal(h.calls.length, 1);
  assert.equal(h.api.state.token, 'user-b');
  assert.equal(h.timers.size, 0);
});

test('写请求遇429立即失败，不重试', async () => {
  const h = harness();
  const result = expectError(h.api.manualSave(1), 'RATE_LIMIT');
  await flush();
  assert.equal(h.calls[0].options.method, 'POST');
  h.calls[0].respond({ code: 'RATE_LIMIT' }, 429);
  await result;
  await h.advance(10000);
  assert.equal(h.calls.length, 1);
  assert.equal(h.timers.size, 0);
});
