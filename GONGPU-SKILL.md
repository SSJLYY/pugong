# GONGPU-SKILL · 《普攻永久增加生命上限》项目操作手册

> 新会话读这一份即可直接开工：连服务器、改代码、构建、部署、验证、排错。
> 周末无内网 → 直接走第 8 节本地闭环（git/编译/起服/冒烟/单测全套齐备）。

## 0. 一句话项目画像

放置类网游（出拳永久涨血，1e44 攻速通关转生）。**前后端分离 + 服务端权威**：
前端纯静态（nginx），后端 Spring Boot 3.2.5 + Java 21（8081），
MySQL 8 持久 + Redis 缓存/锁/榜单。数值全部服务端结算，前端只发意图。

## 0.5 当前状态速览（2026-11-03 更新，v3.15 增强轮后）

- **v3.15 增强轮（2026-11-03，已部署公网并实测）**：①新玩法「怪物词条」——野怪出生按区域深度掷 💢狂暴/🪓破甲/🔗缴械（平原 10% 起每深一层 +3%，池随深度解锁，Boss/木桩不出），`MobState.trait` 持久化、mobView 下发展示信息、前端名后缀+描述行渲染，公网实测刷出狂暴野兔且界面渲染确认。②销账两条技术遗留：BULLET_MP 排除强化乘区（遗留 #6）、榜单 submitBoard 改 Lua 原子提交+节流表清理（遗留 #5）。③性能：advance 内面板构建砍 3 处可证等价的重复（grow 复用主面板、autoPotion 三分支共用一份）。④前端标题/meta 补齐、版本升 v=53。⑤同日续作「分级售卖」（玩家需求）：手动一键卖扩为白/绿/蓝/紫四档按钮、挂机自动售卖扩到紫档（全折现不进包），前端升 v=54。⑥同日再续「十轮增强」（每轮独立提交，已部署公网实测）：nextFreeExtra 线性化 / 用户名走 JWT claim / 日聚合轻量采集 / SocialService 写路径收口 / 锁模板 withLock 收口 / 前端轮询失败退避 / 圆满金碑 / 管家悬浮面板 / 存档导出 / 自动进食，前端升 v=55。单测 **172 全绿**（V315EnhanceTest 5 项 + SellTierTest 4 项 + PerfectGraveTest 1 项 + AutoEatTest 2 项）；本机（Muse VM）测试依赖已补齐，`mvn -o test` 可直接离线跑通（seed-test.py 在 pugong-deploy）。详见 README「变更记录（2026-11-03）」各节。
- **（以下为 2026-11-02 晚状态）**

- **第二部署目标已上线**：`https://pugong.shaun88.cc.cd/gongpu/`（Muse VM，Cloudflare 隧道 → nginx :8088 → 本栈）。该 VM 的 JVM 出站 TCP 被平台拦截，MySQL/Redis 全走 Unix 套接字（MariaDB localSocket + 新增 `DeploySocketConfig`），构建走离线 Maven 仓；细节见 README「公网部署」节，下方 136 的路径/命令不适用于此目标。部署目录 `~/workspace/pugong-deploy/`，看门狗 supervise + cron 保活。
- **三处修复（已实测，详见 README 变更记录）**：①CORS 白名单缺公网域名致浏览器注册/登录 403，启动参数已补；②前端 `renderBuffs._sig` 悬空引用致新手天赋觉醒仪式永不触发，改挂 `applyView._buffSig`（浏览器实测抽卡全流程通过），前端版本升 v=52；③灌库 CLI 字符集错误致 cfg_* 文本双重编码乱码，45 列已反转还原，CLI 导入必须 `--default-character-set=utf8mb4`。
- **后端优化**：轮询帧落库节流 `SaveService.persistThrottled`（MySQL 写降至约 1/5，死亡强制立即写）；`run()` 内重复账号查询合并。

- **验证基线**：单测 **172 全绿**（mvn -o test，含 PotionsV314Test 12 项 + V315EnhanceTest 5 项 + SellTierTest 4 项 + PerfectGraveTest 1 项 + AutoEatTest 2 项）+ 冒烟 **66 全绿**（136 已跑）；每次改完必须双绿。
- **136 部署**：2026-11-02 v3.14 已按第 3 节完整部署（8180c24），双层冒烟 66/66 全绿；前端 ?v=49 已生效，136 商店 meta 实测 18 种药剂。
- **v3.14 商店药剂扩容 8→18**（玩家需求）：新增锐眼(暴击+10%/90s)/残暴(暴伤+50%)/嗜血(吸血+10%)/狂暴(攻击×1.5)/磐石(双防×1.5)/疾风(攻速×1.5)/明察(命中+10%)/幻影(闪避+10%)/凝神(回蓝+10/s)/护盾(30%生命上限吸收盾/30s)。全部受沉默压制；**护盾是新机制**：PlayerState.shield 字段，mobAttack/dotTick 两路先扣盾后扣血，shield_pot 到期 tickBuff 清盾，fullView 下发 panel.shield，前端血条显示 🛡余量。配置链：GameConst.POTIONS 兜底 18 种 + migration-v3.14-potions.sql（cfg_item 10 行+cfg_buff 10 行幂等）；**灌库后必须 admin/config/reload 热刷新才对玩家生效**（本地实测踩坑：灌库不刷新 meta 仍旧 8 种）。
- **v3.13 背包膨胀根治**（玩家三槽卡死修复）：根因三连——①v3.12 前吞噬把 BAG_CAP 累加进合并腰带（bagCap 虚增到 33720 格）②autoSellKeep=0 掉落全进背包（33720 件=14.3MB）③状态接口每秒全量下发 13.9MB JSON 前端卡死。修复：挂机管家新增「🧲 自动管护」开关（autoEquipBest，结算后自动卖白装→同名吞噬→穿最强）；fastAutoEquip 重写 O(n log n)（排序+名字哈希吞噬，空背包短路）；devourInto 对 BAG_CAP 取大不求和+bagCap 钳制封顶；fullView/inventoryView 背包截断 BAG_VIEW_LIMIT=200+bagTotal 下发。三槽实测：背包 33720→0、响应 13.9MB→57KB、轮询 0.21s。
- **前端性能小修**（v3.13 同轮）：renderBag 签名补 bagTotal；editing 白名单补 autoEquipChk；__game 桥接补参；buffBar 签名守卫只更新倒计时文本；renderTitles 签名剔除每秒变化字段。
- **技术遗留**（较大手术，已列入 README「技术遗留清单」）：persist 每秒全量 JSON 编解码+MySQL 全量写（已节流 5s，对象化未做） / StateCache 缓存 JSON 而非对象 / equipped 词条膨胀仍全量下发 / 前端快照-点击 ≤1s 竞态 / StatDailyAggregator 冗余数值列+SUM（对象图反序列化已于十轮增强消除）。（已销账：榜单原子化 v3.15 Lua、BULLET_MP 强化放大 v3.15、advance 内面板重复构建 v3.15 部分、锁模板 withLock 收口、SocialService persist 收口、nextFreeExtra O(n²)、用户名查库——后四项为 2026-11-03 十轮增强）
- 本文件是唯一开工入口；读完本节+§8/§9 即可动手。

## 1. 服务器连接（免密已配置，不要用密码）

```bash
ssh root@192.168.215.136 '<命令>'     # 本机 ed25519 公钥已入 authorized_keys
```

- 不要在代码/文档/记忆里出现任何明文密码。
- MySQL root 免密登录：服务器上直接 `mysql`（读 /root/.my.cnf）。
- 业务账号 `gongpu_app@127.0.0.1` 的 DB_PASSWORD 与 JWT_SECRET 在 `/etc/gongpu/app.env`（600 权限），只被 systemd 注入。

## 2. 路径地图（136 服务器）

| 路径 | 内容 |
|---|---|
| `/data/gongpu/app/` | 后端源码工作副本（pom/src/deploy） |
| `/data/gongpu/dist/gongpu-server.jar` | 运行 jar（`.bak` = 上一版，可回滚） |
| `/data/gongpu/logs/` | stdout / stderr / gongpu-server.log |
| `/data/games/gongpu/` | 前端静态站（nginx root，改完即生效） |
| `/data/games/gongpu-legacy-v1/` | v1 纯前端旧版备份（对照用） |
| `/etc/gongpu/app.env` | DB_PASSWORD + JWT_SECRET |
| `/etc/nginx/conf.d/games.conf` | nginx 站点（8088） |

本地工作区（`D:\个人\充电\练手项目\game`）：`web/` 前端、`server/` 后端、`deploy/games.conf`、`README.md`、`gongpu-legacy/` 旧版。

## 3. 改代码 → 上线（本地写码，服务器构建，本地绝不构建）

```bash
# 后端：上传改动的源码文件，然后服务器构建+迁移+重启+健康检查
scp -r server/src root@192.168.215.136:/data/gongpu/app/src
scp server/deploy/*.sh server/deploy/*.py root@192.168.215.136:/data/gongpu/app/deploy/   # 运维脚本同步,别漏
ssh root@192.168.215.136 'bash /data/gongpu/app/deploy/deploy.sh'

# 前端：纯静态直接传（index.html 是 no-cache，传完即生效）
scp -r web/* root@192.168.215.136:/data/games/gongpu/
ssh root@192.168.215.136 "sed -i 's/\r\$//' /data/games/gongpu/js/*.js"   # 去 CRLF！
```

deploy.sh 流程：Maven 构建（Java 21）→ 备份旧 jar → **部署前 DB 自动备份**（backup-db.sh,失败仅告警）→ 幂等 DB 迁移 → systemd 重启 → 健康检查（含磁盘告警,失败打印日志并提示回滚）→ **双层冒烟**（直连 8081 + 8088 反代,各 66 项,含运维端 9 项;未注入 SMOKE_ADMIN_PASSWORD 时自动跳过该节=57 项）。应用日志已配滚动（20MB/14天/200MB 封顶）。

## 4. 验证清单（改完必跑）

```bash
# 1) 三件套健康
curl http://192.168.215.136:8088/gongpu/api/health      # {"ok":true,"redis":"up","mysql":"up"}

# 2) 注册登录 → 建档 → 轮询涨血（HP 数字应随时间增长）
curl -s -X POST http://192.168.215.136:8088/gongpu/api/auth/register -H 'Content-Type: application/json' -d '{"username":"t1","password":"test123456"}'
# → 拿 token 后：GET /gongpu/api/game/1/state（带 Authorization: Bearer <token>）

# 3) 反代与静态
curl -o /dev/null -w '%{http_code}' http://192.168.215.136:8088/gongpu/
```

浏览器手测入口：`http://192.168.215.136:8088/gongpu/`（注册→建槽→打木桩看涨血→换区→死亡三选项→江湖页祭奠/切磋）。
> deploy.sh 健康检查后会自动跑冒烟：先直连 8081 后端、再打 8088 反代层（各 66 项，运维节需 SMOKE_ADMIN_PASSWORD），两层全绿才算部署完成。

## 5. 排错速查

| 症状 | 大概率原因 | 查法/修法 |
|---|---|---|
| 服务反复重启 | ddl-auto=validate 与表不匹配 | `journalctl -u gongpu-server -n 50`；改实体要同步 schema.sql + deploy.sh 迁移列表 |
| 起不来 + jar 报错 | dist jar 没更新 | deploy.sh 已修：`--skip-build` 也强制 cp target jar |
| `set: pipefail: invalid option` | sh 脚本有 CRLF | `sed -i 's/\r$//'` |
| 请求 10 秒内全报"操作太快" | unlock 的 Lua token 释放失败 | 检查 StateCache.unlock 是否把 token 当单个 varargs（不能包 List） |
| 改了 DB 状态但被"顶回去" | 直写 DB 后没失效缓存 | 写 state_json 后必须 `cache.evict(uid, slot)` |
| 登录/注册 403"失败次数过多" | AuthRateLimiter 同 IP 限速 | 换 IP 或等窗口(10min/1h);回环豁免;Redis key `gongpu:auth:*` |
| 冒烟注册 400 用户名不合法 | 用户名超 16 位 | 命名 ≤16 位(load_test/smoke 已对齐) |
| JDBC `Unsupported character encoding` | characterEncoding 写成 utf8mb4 | 用 `UTF-8` + connectionCollation |
| API 404 / 双重 /api/api/ | 前端 fetch 没走 API_BASE 或 nginx 丢了前缀 | api.js 用 API_BASE 拼接；nginx `^~ /gongpu/api/` |
| mysql 客户端跑 SQL 中文报错/表被清空 | Windows 客户端默认字符集非 utf8；seed 是先 DELETE 再 INSERT | 一律 `mysql --default-character-set=utf8mb4`（deploy.sh 已全量内置；本地库 2026-10-31 踩坑后已重灌） |
| git commit 中文信息变乱码 | Bash 管道编码被污染 | 用 `git commit -F <utf8文件>`（Write 工具写消息文件），别走 -m 长串 |
| 并行会话把自己/别人的半成品提交了 | 多智能体同时开，有人用 `git add -A` | 小批即提交、`git add <具体文件>`；开工前先 `git log -2`+`git status` 探测并行改动 |
| 玩家点击频繁弹"操作太快" | 轮询每秒持锁~百毫秒,点击撞窗(已修:前端等锁+409 重试+写队列,后端 run 短等重试) | 别改回瞬时拒绝;若再现查 StateCache.unlock 的 Lua token 是否单 varargs |
| 玩家中后期页面卡死 | 背包膨胀(吞噬累加 BAG_CAP+售卖关闭)→状态接口全量下发 MB 级 JSON | 开「自动管护」开关即自清;根治链已 v3.13 部署(截断+钳制+O(n log n));若再现查 devourInto 是否又累加了容量类词条 |
| scp -r server/src 上传后服务器构建报找不到文件 | `scp -r` 目标目录已存在会把源码嵌套进 `src/src/` | 服务器上 `cd src && rm -rf main test && mv src/* . && rmdir src` 提平；或 scp 时目标写到父目录整体覆盖 |

## 6. 运维端（v3.0）

- 入口 `http://192.168.215.136:8088/gongpu/admin.html`；密码 = `/etc/gongpu/app.env` 的 ADMIN_PASSWORD（服务器上 `grep ADMIN_PASSWORD /etc/gongpu/app.env` 查看）
- 后端 `/api/admin/**`（AdminGuard 独立 JWT，subject=admin，2h 有效）；改配置自动热刷新 GameData
- 配置数据在 6 张 cfg_* 表；种子文件 server/src/main/resources/seed-v3.sql（幂等可重跑）
- 改数值三选一：运维端页面改（推荐）/ 直接 UPDATE cfg_* 表后 POST /api/admin/config/reload / 重启服务
- 所有运维操作落 admin_audit_log 审计表

## 7. 玩法系统速查（v3.10，2026-10-31 两轮十轮优化后）

| 系统 | 位置 | 要点 |
|---|---|---|
| 药剂（18 种） | cfg_item + GameConst.POTIONS 兜底 | 修行药剂 growth_pot 成长×2（GameEngine.grow 乘区）、铁壁药剂 iron_pot 双防×1.5（PanelCalculator 乘区）；v3.14 新增 10 种：锐眼/残暴/嗜血/狂暴/磐石/疾风/明察/幻影/凝神/护盾（乘区/加区全在 PanelCalculator，受沉默压制）；护盾为新机制（PlayerState.shield，mobAttack/dotTick 先扣盾，buff 到期清盾，panel.shield 下发）；**灌库后必须 admin/config/reload 热刷新** |
| 每日签到 | POST /api/game/{slot}/daily | 每账号每天一次（account_user.last_claim_day）；金币 200+gen×100、血药×3 蓝药×2、gen≥1 送修行、gen≥3 送铁壁 |
| 洗炼+词条锁定 | POST /api/game/{slot}/reroll {index,lockIndex} | EquipFactory.rerollAffixes(it,lockIdx)；锁定词条保留且不放回池；锁定费用 ×3（rerollCost×3） |
| 分级一键卖 | POST /api/game/{slot}/autosell {keep} | keep 1~4 合法（4=紫及以下即全卖，越界 400）；卖出 quality<keep 的全部背包装备；前端背包区四档按钮组（卖白装/绿及以下/蓝及以下/紫及以下） |
| 一键穿最强 | POST /api/game/{slot}/autoequip | equipScore=品质×1000+词条×100+底子和，强化+5%/级；同部位更优即换，戒指自动填 ring2 |
| 装备词条显示 | 前端 app.js affixText 唯一出口 | 全部直接数值无 %：率型键(CRIT/CRITD/ACC/EVA/LIFESTEAL)按百分点显示(内部仍存小数概率)；pct_hp 生命泉涌刻意保留 %；旧档 pct 词条由 StateCodec.decode 线性映射转 flat(LEGACY_PCT_MAP 必须覆盖全部词条 key，漏键=词条静默失效，DataConsistencyTest 锁死) |
| 挂机自动售卖 | 挂机管家 autoSellKeep 0~4 | 档位=白色及以下/绿色及以下/蓝色及以下/紫色及以下(全折现)，掉落直接折现**不进背包**（选蓝档只有紫装入包）；逐杀(onMobKilled)与溢出期望(dealDamage 护栏外)两分支同语义(玩家反馈修复,CombatRegressTest+SellTierTest 锁死)；手动分级卖走 autosell 端点；新手保底装备直入包不受拦截（福利豁免） |
| 自动进食 | 挂机管家 autoEat 开关 | 战斗区内饱食回血（regen_food）断档自动吃 1 份食物（food）续上；buff 在身不吃、村里不吃；GameEngine.autoEatTick 在 autoBuffTick 后，AutoEatTest 锁 |
| 圆满金碑 | 世界墓园 | 玩家飞升金碑覆盖 ≥12 个不同世代 → 其金碑在墓园置顶 +「🌕 圆满金碑」金辉样式（SocialService.perfectUsers 聚合金碑近 500 条判定，PerfectGraveTest 锁）；纯纪念无数值加成 |
| 存档导出 | GET /api/saves/{slot}/export | 返回存档 JSON 附件供玩家自备备份；**只出不进**，导入绕过服务端校验明确不开放 |
| 管家悬浮面板 | 前端右下角 🤖 | 血/蓝条+当前怪速览、自动管护开关、自动售卖档位、嗑药/吃食按钮；走 autoconfig 与 usePotion 既有通道，渲染签名 5% 分桶 |
| 挂机自动管护 | 挂机管家 autoEquipBest(v3.13) | 每秒结算后自动「卖白装→同名吞噬→穿最强」(GameService.autoMaintain→fastAutoEquip)；**背包膨胀根治开关**——三槽 33720 件卡死根因；O(n log n) 空背包短路，异常 WARN 不静默；非 baglord 只走卖白装+原生栏换装(不碰无相位栏) |
| 背包容量 | GameConst.BAG_MAX=100 | 动态上限 bagCap=BAG_MAX+装备 BAG_CAP 词条(负重腰带+20)；改容量只动常量 |
| 永续天赋展示 | 顶栏徽章=信息入口 | 永续 ♾️ 下 8 个 ETERNAL_BUFFS 增益不再进 buff 条，悬浮/点击顶栏「♾️ 被动永续」徽章弹 talentPop 清单卡；前后端永续键表必须一致(GameConst.ETERNAL_BUFFS ↔ app.js ETERNAL_KEYS)；其他天赋沿用 title |
| 转生/重开重置 | SaveService.resetForNewLife 唯一实现 | rebirth 复用它(勿再手抄副本)；清 药价阶梯 potionBought/尸检 recentKills/赋能委托等全部派生数据；「每日」时区统一 GameConst.DAILY_ZONE(Asia/Shanghai) |
| 世界公告 | GET /api/social/bulletin（公开） | 史诗(quality≥3)掉落播报；Redis LIST gongpu:bulletin 20 条/1h 过期；玩家名 bulletinName（transient，三个结算入口填充） |
| Boss 首杀 | GameEngine Boss 击杀分支 | PlayerState.bossKills 持久化防重复；金币 1000×(区域序+1)；AdvanceResult.bossFirstKill 下发 |
| 称号+图鉴+成就 | 前端本地推导/渲染 + fullView 下发 | computeTitles 9 称号；codex 集合记录击杀（转生不清空）；**成就 18 项服务端权威落库**（Achievements.java persist 前评估，state_json.achievements 持久化，前端只渲染+diff toast，前后端 id 表有单测锁死） |
| 觉醒天赋 | POST /api/game/{slot}/talent | **8 种天赋**：三大顶级机缘（♾️被动永续/🧳无限装备栏/💠无限属性点）各 30%，其余平分 10%（可自选，顶级只能靠命）；抽中顶级=万中无一金雨仪式；**无限装备栏 v3.12 起觉醒即无限**（不再每天 +1；openExtraSlots=已用 extra_N 数+1，加穿后自动再开一格；穿最强把背包剩余全部穿入无相位栏；**同名装备自动吞噬合并**（equipExtra/穿最强入口：base 求和、同名同键词条 val 叠加、不同词条追加、强化/品质取高；autoEquip 入口先 devourLegacyDuplicates 清历史膨胀——性能卡顿源即已穿件数线性膨胀；原生 ring1/ring2 双戒不动）；旧字段 extraSlots/lastSlotDay 仅兼容旧档，grantDailySlot 已删除） |
| 安全限速 | AuthRateLimiter | 登录失败同 IP 10 次/10 分钟锁；注册同 IP 5 次/小时；回环豁免；Redis 挂了放行 |
| 可观测性 | TimingInterceptor | >gongpu.slow-ms(默认500)的 /api/** 打 WARN 慢日志；启动横幅带版本/profile；500 日志带 METHOD+URI |
| 迁移审计 | 两轮全站对照(设计 §0-16+legacy 六模块) | v3.9 全量迁移完成,剩余项见 README「迁移状态」;autoRebirth/autoSellKeep/autoRebirth 开关在挂机管家 |
| 新手保底 | GameEngine.grantNewbieDrops | 每世前5杀必掉保底武器、≥10杀必掉回春戒(每秒回1%生命上限,type=pct_hp);**空戒指位自动穿上**(两戒占位才进背包;背包满且无空位等待不吞);字段 newbieKills/newbieWeaponsGiven/ringGranted;面板回血行=固定回血+regenHpPerSec(fullView 下发) |
| 排行榜 | GET /api/leaderboard?board=maxhp\|gold\|gen | 三榜统一上报(LeaderboardService.submit,60s 节流)；分数 log10 防 1e308 溢出；member=uid\|名\|世代\|值明文,单榜旧条目兼容 |
| 无限属性点 | POST /{slot}/statpoint {stat,count} | statlord 专属：每杀一怪 +1 自由点（溢出期望击杀同口径发放）；加点值=STAT_POINT_STEP[stat]×count 并入面板 flat 区（攻/防+1、攻速+0.05、暴击+0.5%…14 属性可加，金币不可加）；转生清零重赚；前端点属性卡属性行弹窗加点 || 区域攻击门槛 | gotoZone 校验 req.ATK | **v3.12 新增**:矿洞 60/沼泽 150/雪山 400/火山 1200（ATK=物攻/魔攻取高,物理/附魔/魔攻三条路都算）;此前只卡生命上限,堆血冲进高防区每拳伤害触底 1 点=白打(玩家反馈"后期不杀怪"根因);前端 ZONE_REQ_TEXT 需与后端 req 同步 |
| 压测 | deploy/load_test.py | N 用户并发注册→建档→1s 轮询,P50/95/99+RPS,退出码可接 CI;基线见 deploy/LOAD_TEST.md(40 并发 P95=66ms) |
| 枪械系统 | cfg_equipment_template base_stats | 三线：物理弹（纯 PATK 高攻速）/物理附魔弹（PATK+MATK 双轨不耗蓝）/纯魔法弹（纯 MATK + BULLET_MP 每发耗蓝，无蓝哑火，蓝回复后自动恢复射击）；加特林 ASPD 8、魔弹加特林 ASPD 10；一发子弹=一次普攻
| 挂机策略 | POST /api/game/{slot}/autoconfig {hpLine,mpLine,autoBurn,autoSkill} | autoHpLine/autoMpLine 持久化（0~0.9 钳制）；autoPotion 在怪物攻击**前**执行（顺序 bug 修复：放攻击后低血先死嗑不到药），先大血药后小血药，离线也生效；**v3.4 新增 autoBurn/autoSkill**：GameEngine.autoBuffTick 按 legacy autobot 语义自动补 buff（涌动先行免蓝→急速→蓄力；血<60% 格挡；血<30% 怒吼；血>60% 补燃烧），走 castSkill 统一入口，村口/沉默跳过 |
| 战斗飘字 | 纯前端 app.js floatDmg | 帧间怪物血量差弹伤害字（暴击按 CRIT 抽样金红样式），120ms 窗口 4 个预算节流；复用旧版 float-dmg CSS |
| 怪物词条（v3.15） | GameConst.MOB_TRAITS/rollMobTrait + MobState.trait | 出生掷骰：村口不出、平原 10% 每深一层 +3%、池随深度解锁（平原仅狂暴/森林加破甲/矿洞起全开），Boss 不出；效果：狂暴=残血攻速×1.5（mobAttack hits）、破甲=玩家双防×0.7 结算（mobAttack）、缴械=玩家普攻×0.85（dealDamage）；mobView 下发 emoji/名/说明，前端名字后缀+mobDesc 直读；V315EnhanceTest 锁数值 |
| 离线收益提示 | GameController.run 下发 offline | seconds>60 时下发摘要，前端每会话弹一次 |

## 8. 本地开发闭环（周末无内网时用）

**周末本地开发全套工具链已就绪**（2026-10-30 建立），改代码不必连 136 即可完整验证：

| 环节 | 命令/位置 |
|---|---|
| 版本控制 | 项目根 `git`（初始基线 bacea7c）；会话草稿 qa_*.py/scratch_*.py 已 gitignore |
| JDK21 | `/d/soft/java/jdk21`（PATH 上的 Oracle Java 17 会 UnsupportedClassVersionError！） |
| Maven | `/d/soft/apache-maven-3.9.12`，**离线依赖已全缓存**，`mvn -o` 即可 |
| 本地 MySQL | 服务已跑，库 gongpu 已建（schema+seed+全部 v3.x 迁移已灌），业务号 gongpu_app/gongpu_local（口令仅存本机 server/local.env，不入库） |
| 本地 Redis | choco 版 8.6.3，`redis-server` 起（服务模式未装，需手动拉起） |
| 一键起服 | `bash server/run-local.sh`（读 server/local.env 注入密钥→打包→8081 起 local profile） |
| 密钥文件 | `server/local.env`（**绝不入库**，模板是 local.env.example） |
| 全链路冒烟 | `python -I server/deploy/smoke_test.py`（66 项，覆盖全部写端点+运维端 CRUD；SMOKE_ADMIN_PASSWORD 未设置时 57 项；136 上传参 BASE_URL 即可） |
| 单元测试 | `mvn -o test`（165 项：Baglord 边界/经济/成长/常量表/序列化边界/战斗回归 CombatRegressTest/迁移 DataConsistencyTest/枪械 GunplayTest/快照/赋能封顶/无限装备栏语义/v3.15 增强 V315EnhanceTest；Muse VM 本机依赖已补齐可直接跑） |
| DB 备份 | `server/deploy/backup-db.sh`（136 上挂 cron 3:30，保留 7 份，gzip 完整性校验） |
| 压测 | `python deploy/load_test.py 20 30`（基线 deploy/LOAD_TEST.md） |
| 移动端 | css/v2.css 末尾媒体查询(768/420 两档):单栏重排/技能两列/横滑地图/弹窗限高;改布局后 375 视口必测 |
| 前端结构 | 纯原生 JS 无构建：app.js(IIFE 单文件)/api.js/admin.js；**两栏布局**(战斗+地图\|大页签 4×2)；左上角头像方块悬浮弹 statCard 属性卡(成就/称号/图鉴在「👤 角色」页签)；通用点选弹窗 pickModal、装备词条触屏卡 item-pop、永续徽章卡 talent-pop 均在 app.js+style.css 尾部 |
| 前端验证 | 静态页由 127.0.0.1:8090 常驻服务提供(挂 web/+/api 反代 8081,通常用户已拉起,先 curl 探测)；**改前端必须升 index.html 版本戳 ?v=N**(当前 v=53)；测试档 gui_bg_60945(槽1,6.5万血)可自由折腾；视口三件套 1366×768/1366×640/390×844 |

部署流程升级：deploy.sh 健康检查通过后**自动跑冒烟**（失败不回滚只报警）。

## 9. 改动守则（本项目血泪浓缩）

1. **新增写端点**：必须套 `run()`（GameController）或 `locked()`（Save/Social）模板——先锁再结算再落库；请求体字段先过 `reqStr/reqIdx` 兜底（GameConst 查找表是 Map.of，get(null) 直接 NPE 500）。
2. **写存档一律走 `SaveService.load + persist`**（2026-11-03 起 SocialService 亦然）：禁止再手写「直查 DB→decode→encode→save」绕过成就评估/冗余列/缓存刷新；万一真要直改 DB，写完必须 `evict` 缓存。
3. **大数**：全 BigDecimal 字符串；`doubleValue()` 只许用于阈值比较；跨账号计数（香火/战绩/签到）一律原子 UPDATE（UserRepository/GraveRepository 已有范式）。
4. **用户输入进 innerHTML**：先过 `esc()`。
5. **实体改动三同步**：实体类 + schema.sql + deploy.sh 迁移函数。
6. **每轮修改后必跑**：`mvn -o test`（165 项）+ `python -I server/deploy/smoke_test.py`（66 项），双绿才算完成。
7. **kills/gold 等基线类字段**：死亡重开/转生清零时，引用它的派生数据（如委托基线）必须同步清理。
8. **前端渲染加签名守卫**：applyView 每秒全量跑，任何新 renderXXX 必须挂 `_sig` 内容签名（范式见 renderPotions/renderBag），否则每秒销毁重建 DOM=掉帧+点击丢失。
9. **浮层一律不透明底**：`--panel/--panel-2` 是近乎全透的白色叠层(靠暗底撑住)，浮层底下有内容会透——用 `--bg1/--bg2` 渐变(statCard/item-pop/talent-pop 三处先例)；新弹窗优先复用 pickModal/death-mask 体系。
10. **改前端必升 `?v=N`**；改数值语义先想"感知层 vs 数值层"——显示口径变化只动 affixText/fmt 等展示函数，结算不动(率型词条先例)。
11. **多智能体协作**：小批即提交、`git add <具体文件>` 绝不 add -A；开工前 git log/status 探测并行会话改动；发现别人半成品先 adopt+审计而非覆盖。
12. **前端写请求必须走 `call()`**：它内置等轮询让锁(≤1.5s)+双击合并(<400ms)+写串行队列(_writeChain)+409 静默重试——绕过 call 直接打 API 会吃 409"操作太快"且绕过期帧丢弃。
13. **改动"按天/按次数解锁"类玩法前先想无限模型**：无限装备栏先例=openExtraSlots 返回"已用数+1",永远恰有一个空位可操作,UI 不渲染无穷格;旧档积累字段保留兼容但不参与计算。
7. **kills/gold 等基线类字段**：死亡重开/转生清零时，引用它的派生数据（如委托基线）必须同步清理。
