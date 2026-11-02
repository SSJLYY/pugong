#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
全链路冒烟测试(运维资产):覆盖认证/建档/战斗/装备/社交/死亡的每一条写路径。
用法:
  python deploy/smoke_test.py [BASE_URL]
    BASE_URL 默认 http://127.0.0.1:8081/api
    136 上跑: python deploy/smoke_test.py http://192.168.215.136:8088/gongpu/api
退出码: 0=全绿, 1=有失败(可接 CI)
"""
import json
import os
import sys
import time
import urllib.request
import urllib.error

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8081/api").rstrip("/")
USER = "smoke_%d" % int(time.time())
PASS = "Smoke123456"
results = []


def call(method, path, body=None, token=None, expect_ok=None):
    """发请求并记录 PASS/FAIL。expect_ok: True 要求 ok=true, False 要求 ok=false, None 不检查"""
    url = BASE + path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            d = json.loads(r.read().decode("utf-8"))
            status = r.status
    except urllib.error.HTTPError as e:
        try:
            d = json.loads(e.read().decode("utf-8"))
        except Exception:
            d = {"ok": False, "code": "HTTP%d" % e.code}
        status = e.code
    except Exception as e:
        results.append(("FAIL", method, path, "连接失败: %s" % e))
        return {}

    if isinstance(d, list):
        d = {"ok": status < 400, "_list": d}
    ok = d.get("ok", status < 400)
    name = "%s %s" % (method, path)
    if expect_ok is True and not ok:
        results.append(("FAIL", method, path, json.dumps(d, ensure_ascii=True)[:160]))
    elif expect_ok is False and ok:
        results.append(("FAIL", method, path, "期望失败却成功: " + json.dumps(d, ensure_ascii=True)[:120]))
    else:
        results.append(("PASS", method, path, ""))
    return d


def main():
    t0 = time.time()

    # ---- 0. 健康 ----
    d = call("GET", "/health", expect_ok=True)
    assert d.get("ok") and d.get("redis") == "up" and d.get("mysql") == "up", "健康检查三件套不齐"

    # ---- 1. 认证 ----
    d = call("POST", "/auth/register", {"username": USER, "password": PASS}, expect_ok=True)
    tok = d.get("token", "")
    assert tok, "注册没拿到 token"
    d = call("POST", "/auth/login", {"username": USER, "password": PASS}, expect_ok=True)
    assert d.get("token"), "登录没拿到 token"
    call("POST", "/auth/register", {"username": USER, "password": PASS}, expect_ok=False)  # 重复注册应拒绝
    call("POST", "/auth/login", {"username": USER, "password": "wrong-password"}, expect_ok=False)

    # ---- 2. 建档 + 轮询 ----
    d = call("POST", "/saves/1", {"name": "smoke_a"}, token=tok, expect_ok=True)
    assert d.get("zone") == "village"
    call("POST", "/saves/1", {"name": "dup"}, token=tok, expect_ok=False)  # 重复建档应拒绝
    d = call("GET", "/saves", token=tok, expect_ok=True)

    d = call("GET", "/game/1/state", token=tok, expect_ok=True)
    hp1 = d.get("panel", {}).get("maxHP", "0")
    time.sleep(2.2)
    d = call("GET", "/game/1/state", token=tok, expect_ok=True)
    hp2 = d.get("panel", {}).get("maxHP", "0")
    assert float(hp2) > float(hp1), "挂机 2s 血上限没涨(%s -> %s)" % (hp1, hp2)
    call("GET", "/game/1/state", expect_ok=False)  # 无 token 应 401

    # ---- 3. 战斗路径 ----
    call("POST", "/game/1/zone", {"zone": "village"}, token=tok, expect_ok=True)     # 村口(安全区)
    # 平原需 maxHP≥500,新号(~107)进不去是正确行为;不在此断言,避免为凑血量拖慢冒烟
    call("POST", "/game/1/zone", {"zone": "no_such_zone"}, token=tok, expect_ok=False)
    call("POST", "/game/1/skill", {"id": "burn"}, token=tok, expect_ok=True)         # 燃烧生命
    call("POST", "/game/1/skill", {"id": "no_such"}, token=tok, expect_ok=False)
    call("POST", "/game/1/skill", None, token=tok, expect_ok=False)                  # 空 body 应 400 而非 500
    call("POST", "/game/1/flee", None, token=tok, expect_ok=True)
    call("POST", "/game/1/boss", None, token=tok, expect_ok=False)                   # 村口无 Boss → 400
    call("POST", "/game/1/autoconfig", {"hpLine": 0.5, "mpLine": 0.3}, token=tok, expect_ok=True)

    # ---- 4. 签到(先领 200 金,后续购买才有余额) ----
    call("POST", "/game/1/daily", None, token=tok, expect_ok=True)
    call("POST", "/game/1/daily", None, token=tok, expect_ok=False)  # 一天一次

    # ---- 5. 商店/背包/装备 ----
    d = call("GET", "/game/1/state", token=tok, expect_ok=True)
    call("POST", "/game/1/buy", {"id": "hp_s", "count": 2}, token=tok, expect_ok=True)
    call("POST", "/game/1/buy", {"id": "no_such_item", "count": 1}, token=tok, expect_ok=False)
    call("POST", "/game/1/potion", {"id": "hp_s"}, token=tok, expect_ok=True)
    call("POST", "/game/1/autosell", {"keep": 0}, token=tok, expect_ok=True)
    call("POST", "/game/1/autoequip", None, token=tok, expect_ok=True)
    call("POST", "/game/1/enhance", {"index": 0}, token=tok, expect_ok=False)  # 空背包强化应被拒
    call("POST", "/game/1/sell", {"index": 0}, token=tok, expect_ok=False)
    call("POST", "/game/1/unequip", {"slot": "weapon"}, token=tok, expect_ok=False)
    call("POST", "/game/1/reroll", {"index": 0}, token=tok, expect_ok=False)          # 无锁洗炼(lockIndex 缺省)
    call("POST", "/game/1/reroll", {"index": 0, "lockIndex": -1}, token=tok, expect_ok=False)

    # ---- 6. 系统:天赋/委托/合成/图鉴 ----
    call("POST", "/game/1/equip-extra", {"index": 0}, token=tok, expect_ok=False)  # 无天赋加穿应拒
    call("POST", "/game/1/talent", {"key": "eternal"}, token=tok, expect_ok=False)  # 顶级机缘不可自选
    call("POST", "/game/1/talent", {"key": "no_such"}, token=tok, expect_ok=False)
    call("POST", "/game/1/talent", None, token=tok, expect_ok=True)  # 随机抽取(命运水晶)
    call("GET", "/game/1/talent", token=tok, expect_ok=True)
    call("GET", "/game/1/quests", token=tok, expect_ok=True)
    call("POST", "/game/1/quest/accept", {"code": "no_such_quest"}, token=tok, expect_ok=False)
    call("GET", "/game/codes", token=tok, expect_ok=True)
    call("GET", "/config/meta", token=tok, expect_ok=True)

    # ---- 6. 社交/榜单 ----
    call("GET", "/social/me", token=tok, expect_ok=True)
    call("GET", "/social/graveyard", token=tok, expect_ok=True)
    call("GET", "/social/bulletin", expect_ok=True)
    call("GET", "/social/flavor", token=tok, expect_ok=True)
    d = call("GET", "/leaderboard", expect_ok=True)
    assert d.get("board") == "maxhp", "默认榜应为 maxhp"
    d = call("GET", "/leaderboard?board=gold", expect_ok=True)
    assert d.get("board") == "gold", "gold 榜参数应生效"
    d = call("GET", "/leaderboard?board=gen", expect_ok=True)
    assert d.get("board") == "gen", "gen 榜参数应生效"
    d = call("GET", "/leaderboard?board=%E9%9D%9E%E6%B3%95", expect_ok=True)  # URL 编码的"非法"
    assert d.get("board") == "maxhp", "非法 board 应回落 maxhp"
    call("GET", "/graveyard", token=tok, expect_ok=True)
    call("POST", "/social/tribute", {"graveId": 999999}, token=tok, expect_ok=False)
    call("POST", "/social/duel", {"target": USER}, token=tok, expect_ok=False)  # 自切磋应拒

    # ---- 7. 存档快照 + 删除 ----
    call("POST", "/saves/1/save", None, token=tok, expect_ok=True)
    call("POST", "/saves/1/save", None, token=tok, expect_ok=True)  # 幂等

    # ---- 8. 死亡三选项(强制死亡路径) ----
    # 连续 boss 战至死或直接构造:用 death 接口的三种选择无法在不死时验证,改为只验证参数校验
    call("POST", "/game/1/death", {"choice": "invalid_choice"}, token=tok, expect_ok=False)
    call("POST", "/game/1/statpoint", {"stat": "PATK", "count": 1}, token=tok, expect_ok=False)   # 无 statlord 天赋应拒

    # ---- 9. 清理:删档 ----
    call("DELETE", "/saves/1", token=tok, expect_ok=True)

    # ---- 10. 运维端 /api/admin/**(独立 AdminGuard JWT,玩家端冒烟测不出它的回归) ----
    # 密码从环境变量 SMOKE_ADMIN_PASSWORD 注入(136=app.env 的 ADMIN_PASSWORD,
    # 本地=server/local.env);未设置则整节跳过,不算失败。
    admin_pw = os.environ.get("SMOKE_ADMIN_PASSWORD", "")
    if admin_pw:
        ar = call("POST", "/admin/login", {"password": admin_pw}, expect_ok=True)
        atok = (ar or {}).get("token") or ""
        if atok:
            call("GET", "/admin/overview", None, token=atok, expect_ok=True)
            call("GET", "/admin/stats", None, token=atok, expect_ok=True)      # 近 30 天运营趋势
            al = call("GET", "/admin/affix", None, token=atok, expect_ok=True)
            rows = (al or {}).get("rows") or (al or {}).get("_list") or []
            tmp = next((r for r in rows if r.get("code") == "smoke_tmp_a"), None)
            if tmp:
                # 上次运行残留:先清掉再建(用后即删的幂等保障)
                call("DELETE", "/admin/affix/%s" % tmp.get("id"), None, token=atok, expect_ok=True)
            cr = call("POST", "/admin/affix",
                      {"code": "smoke_tmp_a", "name": "冒烟临时", "statKey": "PATK",
                       "statType": "flat", "minVal": 1, "maxVal": 2, "rarity": "common",
                       "enabled": True, "sortNo": 999},
                      token=atok, expect_ok=True)
            tid = (cr or {}).get("id")
            if tid:
                call("PUT", "/admin/affix/%s" % tid, {"name": "冒烟临时2"}, token=atok, expect_ok=True)
            call("POST", "/admin/config/reload", {}, token=atok, expect_ok=True)   # 热刷新 GameData
            if tid:
                call("DELETE", "/admin/affix/%s" % tid, None, token=atok, expect_ok=True)
            call("GET", "/admin/audit", None, token=atok, expect_ok=True)
        else:
            results.append(("FAIL", "POST", "/admin/login", "登录成功但无 token"))
    else:
        print("（未设置 SMOKE_ADMIN_PASSWORD,跳过运维端冒烟节）")

    # ---- 汇总 ----
    fails = [r for r in results if r[0] == "FAIL"]
    print("=" * 60)
    for st, m, p, msg in results:
        mark = "PASS" if st == "PASS" else "FAIL"
        line = "[%s] %s %s" % (mark, m, p)
        if msg:
            line += "  <- " + msg
        print(line)
    print("=" * 60)
    print("共 %d 项, 失败 %d 项, 耗时 %.1fs, 测试账号: %s" % (len(results), len(fails), time.time() - t0, USER))
    sys.exit(1 if fails else 0)


if __name__ == "__main__":
    main()
