#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
并发压测工具(运维资产):模拟 N 个玩家并发注册→建档→持续轮询 state,输出延迟分位与 RPS。
用法:
  python deploy/load_test.py [用户数] [轮询持续秒]
    默认 20 用户 30 秒;BASE 默认 http://127.0.0.1:8081/api(环境变量 LOAD_BASE 可覆盖)
退出码:成功率 <99% → 1(可接 CI)
"""
import json
import os
import sys
import threading
import time
import urllib.request
import urllib.error
from collections import Counter

BASE = os.environ.get("LOAD_BASE", "http://127.0.0.1:8081/api").rstrip("/")
N_USERS = int(sys.argv[1]) if len(sys.argv) > 1 else 20
DURATION = int(sys.argv[2]) if len(sys.argv) > 2 else 30


def req(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(r, timeout=15) as resp:
        return json.loads(resp.read().decode())


def percentile(sorted_vals, p):
    if not sorted_vals:
        return 0
    k = min(len(sorted_vals) - 1, int(len(sorted_vals) * p / 100))
    return sorted_vals[k]


results = {"ok": 0, "fail": 0}
errors = Counter()
lat = []            # 所有采样延迟 ms
lock = threading.Lock()
stop_at = 0.0
tokens = []


def worker(idx):
    # 用户名限 2~16 位:load + 时间戳后6位 + 序号 ≤ 16
    user = "load%s%d" % (str(int(time.time()))[-6:], idx)
    t0 = time.time()
    try:
        d = req("POST", "/auth/register", {"username": user, "password": "Load123456"})
        tok = d.get("token")
        if tok:
            with lock:
                tokens.append((tok, user))
    except Exception as e:
        with lock:
            results["fail"] += 1
            errors["register:%s" % type(e).__name__] += 1
        return
    try:
        req("POST", "/saves/1", {"name": "load"}, tok)
    except Exception as e:
        with lock:
            results["fail"] += 1
            errors["create:%s" % type(e).__name__] += 1
    with lock:
        results["ok"] += 2
        lat.append((time.time() - t0) * 1000)

    # 轮询阶段
    while time.time() < stop_at:
        t1 = time.time()
        try:
            req("GET", "/game/1/state", token=tok)
            dt = (time.time() - t1) * 1000
            with lock:
                results["ok"] += 1
                lat.append(dt)
        except urllib.error.HTTPError as e:
            with lock:
                results["fail"] += 1
                errors["state:HTTP%d" % e.code] += 1
        except Exception as e:
            with lock:
                results["fail"] += 1
                errors["state:%s" % type(e).__name__] += 1
        time.sleep(1.0)


def main():
    global stop_at
    print("压测目标 %s | %d 用户 | 轮询 %ds" % (BASE, N_USERS, DURATION))
    threads = []
    stop_at = time.time() + DURATION + 5   # 注册完成后仍有 DURATION 秒轮询
    for i in range(N_USERS):
        t = threading.Thread(target=worker, args=(i,))
        t.start()
        threads.append(t)
        time.sleep(0.05)   # 错峰注册
    for t in threads:
        t.join()

    total = results["ok"] + results["fail"]
    ok_rate = results["ok"] * 100.0 / total if total else 0
    s = sorted(lat)
    print("=" * 56)
    print("总请求 %d | 成功 %.1f%% | 失败 %d" % (total, ok_rate, results["fail"]))
    if s:
        span = max(1, DURATION)
        print("P50 %.0fms | P95 %.0fms | P99 %.0fms | RPS %.1f" % (
            percentile(s, 50), percentile(s, 95), percentile(s, 99),
            results["ok"] / span))
    if errors:
        print("错误分布:")
        for k, v in errors.most_common(8):
            print("  %s x%d" % (k, v))
    # 清理压测档
    for tok, _ in tokens:
        try:
            req("DELETE", "/saves/1", token=tok)
        except Exception:
            pass
    print("已清理 %d 个压测存档" % len(tokens))
    sys.exit(0 if ok_rate >= 99.0 else 1)


if __name__ == "__main__":
    main()
