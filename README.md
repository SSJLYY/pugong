# 普攻永久增加生命上限 · v2.0（网游版）

> 一拳一点命 · 万世不修仙 · 死了就重开（现在，朋友还能用香火把你捞回来）

一款**反等级制 + 网游账号制 + 普攻赋能转生制**的大数放置游戏。
每次普攻永久 +1 生命上限；攻速无上限，一秒 1 载次（1e44）即通关飞升；
转生不清空成长维度，而是让普攻多养一条属性，万世叠加。

v1 是纯前端静态站（localStorage 存档，见 `gongpu-legacy/`，原样保留可对照）；
v2 重构为**前后端分离 + 服务端权威架构**：数值全部在服务端结算，前端只发"意图"，
杜绝改内存刷数值。

---

## 架构总览

```
浏览器（web/，纯静态）
   │  fetch  /gongpu/api/*  +  Authorization: Bearer <JWT>
   ▼
nginx :8088（deploy/games.conf）
   │  location ^~ /gongpu/api/  →  proxy_pass http://127.0.0.1:8081/api/
   │  location /gongpu/         →  /data/games/gongpu/（静态）
   ▼
Spring Boot 3.2.5（server/，Java 21，端口 8081）
   ├── 认证：JWT（无状态，BCrypt 密码哈希）
   ├── 结算：GameEngine.advance() 按 lastTickAt 时间差批量推进
   ├── 缓存：Redis（活跃档状态 + 分布式锁 + 排行榜 ZSET）
   └── 持久：MySQL 8（账号 / 存档槽 / 墓园，JSON 长文本 + 冗余索引列）
```

**数据流（写路径统一模式）**：

```
Controller.run()
  → cache.lock(uid, slot)            # Redis 锁，fail-closed
  → saves.load()                     # Redis 命中或 MySQL 回源
  → saves.advanceNow()               # 服务端权威时间结算（含离线）
  → action.apply(state)              # 换区/施法/嗑药/装备/转生…
  → saves.persist()                  # 写 MySQL + 刷 Redis（纯轮询帧走 persistThrottled 节流，见变更记录）
  → board.submit()                   # 榜单（节流，静默失败）
  → cache.unlock()
```

---

## 目录结构

```
game/
├── web/                      # 前端（部署到 /data/games/gongpu/）
│   ├── index.html            # 单页：登录 → 存档槽 → 游戏主界面
│   ├── css/style.css         # 旧版暗金主题（视觉延续）
│   ├── css/v2.css            # 新增组件（登录/存档槽/冷却/社交）
│   └── js/
│       ├── api.js            # fetch 封装 + JWT + API_BASE 相对路径
│       ├── app.js            # 主逻辑：渲染、轮询(1s)、死亡三选项、转生、江湖
│       ├── achievements.js   # 成就（纯展示层，从服务端统计推导）
│       ├── audio.js          # 音效（WebAudio 合成，无音频文件）
│       └── vendor/break_infinity.min.js   # 大数库
├── server/                   # 后端（部署到 /data/gongpu/app/，构建产物 dist/）
│   ├── pom.xml               # Spring Boot 3.2.5 / Java 21
│   ├── deploy/deploy.sh      # 一键部署：构建→DB迁移→systemd→健康检查
│   └── src/main/
│       ├── java/com/gongpu/
│       │   ├── account/      # User + AccountService（注册/登录/BCrypt）
│       │   ├── common/       # Num（BigDecimal 28 位精度大数）+ ApiException
│       │   ├── config/       # StateCache（Redis 缓存+锁）/ WebConfig（拦截器+CORS）
│       │   ├── game/         # GameConst（数值表）/ GameEngine（结算核心）
│       │   │                 # / PanelCalculator（面板）/ EquipFactory / GameService
│       │   ├── security/     # JwtService + AuthInterceptor
│       │   ├── state/        # PlayerState / GameSave / Grave + SaveService
│       │   │                 # / StateCodec（JSON 大数字符串）/ GraveService / NewState
│       │   ├── stats/        # LeaderboardService（ZSET）/ SocialService
│       │   └── web/          # Controllers + GlobalExceptionHandler
│       └── resources/
│           ├── application.yml
│           ├── schema.sql                    # 新装一次性建表（已含全部列）
│           ├── seed-v3.sql                   # cfg_* 基线（幂等,先 DELETE 再 INSERT）
│           └── migration-*.sql               # v2.x 加列已被 schema 合并(deploy.sh 内联 migrate);
│                                             # v3.1~v3.9 内容迁移全部幂等,deploy.sh 按序自动执行
├── deploy/games.conf         # nginx 站点配置
└── gongpu-legacy/            # v1 纯前端旧版（对照用，不再部署）
```

---

## 部署与运维（136 服务器）

### 环境（已就绪）

| 组件 | 版本/位置 |
|---|---|
| OS | Rocky Linux 9.7 |
| Java | 21（/usr/lib/jvm/java-21-openjdk，另有 8/17） |
| Maven | 3.6.3（镜像走 aliyun） |
| MySQL | 8.0.46（0.0.0.0:3306，root 密码在 /etc/gongpu/root.env 与 /root/.my.cnf） |
| Redis | 6.2（127.0.0.1:6379） |
| nginx | :8088 静态 + API 反代 |
| SSH | 本机公钥已入 /root/.ssh/authorized_keys，免密直连 |

### 关键路径

```
/data/gongpu/app/          # 后端源码（git 之外的 scp 工作副本）
/data/gongpu/dist/gongpu-server.jar    # 运行中的 jar（.bak 为上一版）
/data/gongpu/logs/         # stdout.log / stderr.log / gongpu-server.log
/data/games/gongpu/        # 前端静态站（nginx root）
/etc/gongpu/app.env        # DB_PASSWORD + JWT_SECRET（600 权限）
/etc/nginx/conf.d/games.conf
systemd 服务：gongpu-server
```

### 常用命令

```bash
# 改后端代码后部署（本地写码 → scp 上传 → 服务器构建）
scp -r server/src root@192.168.215.136:/data/gongpu/app/src
ssh root@192.168.215.136 'bash /data/gongpu/app/deploy/deploy.sh'

# 只重启不构建
ssh root@192.168.215.136 'bash /data/gongpu/app/deploy/deploy.sh --skip-build'

# 健康检查（三件套：服务/MySQL/Redis）
curl http://192.168.215.136:8088/gongpu/api/health

# 日志
ssh root@192.168.215.136 'journalctl -u gongpu-server -n 50 --no-pager'
ssh root@192.168.215.136 'tail -50 /data/gongpu/logs/gongpu-server.log'

# 回滚 jar
ssh root@192.168.215.136 'cp -f /data/gongpu/dist/gongpu-server.jar.bak /data/gongpu/dist/gongpu-server.jar && systemctl restart gongpu-server'

# 改前端（纯静态，传完即生效，index.html 已设 no-cache）
scp -r web/* root@192.168.215.136:/data/games/gongpu/
```

### 数据库

- 业务账号 `gongpu_app@127.0.0.1`（密码在 /etc/gongpu/app.env 的 DB_PASSWORD）
- 表：`account_user`（账号+香火+切磋战绩）、`game_save`（3 槽/人，state_json=滚动状态，snapshot_json=存档点快照）、`game_grave`（墓碑）、`stat_daily`
- `spring.jpa.ddl-auto=validate`：实体与表不匹配会**启动失败**——改实体必须同步改 schema.sql 并走 deploy.sh 的幂等迁移
- 新装库：`mysql < server/src/main/resources/schema.sql`（root 执行，含建库）
- **灌库字符集（必读）**：mysql CLI 导入任何含中文的 SQL 必须带 `--default-character-set=utf8mb4`（或在 my.cnf 的 [client] 段预设）。客户端按 latin1 读 UTF-8 文件会把文本双重编码存入（页面显示 å°è¡€ 式乱码），2026-11-02 公网库已中招并逐列反转修复，详见开发注意 #10。

---

## 公网部署（Muse VM · shaun88.cc.cd，2026-11-02 上线）

第二个部署目标，与 136 并行，专为这台受限 VM 适配：

- 公网地址：`https://pugong.shaun88.cc.cd/gongpu/`（Cloudflare 命名隧道 `muse-vm` → 本机 nginx :8088，配置同 deploy/games.conf）
- 部署目录：`~/workspace/pugong-deploy/`（start-gongpu.sh 启动后端，app.env 存密钥 600，supervise + cron 看门狗保活整条链路）
- **本 VM 的 JVM 出站 TCP 被平台拦截**（连 127.0.0.1 也会被重置/伪应答，curl/python 不受影响），全栈改走 Unix 套接字：
  - MySQL：MariaDB Connector/J + JNA 走 `localSocket`（驱动 jar 放 loader.path 的 lib/，DB 用户需 `mysql_native_password`）
  - Redis：`server/src/main/java/com/gongpu/config/DeploySocketConfig.java`（Lettuce + RedisSocketConfiguration 连 redis.sock；另需 netty epoll 两个 jar 进 lib/）
  - JPA：启动参数关 `ddl-auto` 并显式指定 MySQLDialect（Hibernate validate 与 MariaDB 驱动元数据不合；库仍用仓库脚本建）
- **CORS**：启动参数 `--gongpu.cors.origins` 必须含公网域名 `https://pugong.shaun88.cc.cd`，否则浏览器同源请求也会被默认白名单（仅 localhost）以 403 Invalid CORS request 拒绝（curl 无 Origin 头测不出来）
- **构建**：VM 上 Maven 无法直连中央仓（同 JVM TCP 限制），本地仓由 curl 预灌后 `mvn -o` 离线构建；新增依赖要先补灌
- 管理页：`/gongpu/admin.html`，密码在部署目录 app.env

---

## 游戏机制速查（服务端权威数值，均在 GameConst）

| 机制 | 规则 |
|---|---|
| 成长 | 每次普攻：所有已赋能属性 +步长；命中/连击按期望折算；村口 ×2 |
| 通关线 | 攻速 ≥ 1e44（载）→ 飞升转生 |
| 转生 | 面板/装备/技能清零；赋能传承累积；前 12 世新增赋能，之后强化步长 |
| 硬核死亡 | 死亡世界冻结；三种出路：香火复活（满血原地）/ 回档到快照 / 重开一世（传承保留） |
| 快照 | 村口手动存档写 snapshot_json；v3.10 起村口且距上次快照 ≥5 分钟也自动刷新(从未手动存档的玩家死亡也有保命底线)；state_json 每次结算滚动覆盖 |
| 离线 | 8h 上限 ×0.5 效率，服务端按 lastTickAt 补算 |
| 社交 | 祭奠他人墓碑（200金→墓主1香火）；切磋（双方最高面板 30 回合模拟，不伤身）；世界墓园；**三榜单**（生命上限/财富/世代，?board= 切换） |

## API 摘要

```
POST /api/auth/register|login                 → {token, userId, username}
GET  /api/saves                               → 槽位摘要列表
POST /api/saves/{1-3}                         → 新建档   GET …/{slot} → 载入+结算
POST /api/saves/{slot}/save                   → 村口存快照   DELETE …/{slot}
GET  /api/game/{slot}/state                   → 轮询（推进+全量视图）
POST /api/game/{slot}/zone|skill|potion|buy|equip|equipExtra|unequip|sell|enhance|discard
POST /api/game/{slot}/flee|boss|learn|rebirth|talent|death|daily|reroll|enchant
POST /api/game/{slot}/autoequip|autosell|autoconfig|craft|quest/accept|deliver|abandon
GET  /api/game/{slot}/bless-options|talent|crafts|quests
GET  /api/social/me|graveyard|flavor          POST /api/social/tribute|revive|duel
GET  /api/graveyard（个人） /api/leaderboard（公开） /api/config/meta /api/health
```

错误统一 `{ok:false, code, message}`；鉴权失败返回 401 并触发前端登出。

---

## 开发注意（踩过的坑）

1. **大数**：所有数值 `BigDecimal`（`Num.MC` 28 位精度），JSON 里是字符串；`Double`→JSON 会截断 1e44。攻速后期 1e44+，任何 `doubleValue()` 只用于比较阈值。
2. **锁**：所有写端点必须过 `cache.lock()`（Redis SETNX + token + Lua 释放，fail-closed）。新增 Controller 写端点时套 `run()` 或 `locked()` 模板。曾因 `List.of(token)` 当 varargs 传导致解锁永假、10 秒锁死。
3. **缓存一致性**：任何绕过 `SaveService` 直接 UPDATE state_json 的代码（如 SocialService），写完必须 `cache.evict(uid, slot)`，否则轮询会用旧缓存把 DB 覆盖回去（曾把"香火复活"覆盖回死亡态）。
4. **换行符**：本地写的 .sh 上传服务器前 `sed -i 's/\r$//'`，否则 `set: pipefail: invalid option name`。
5. **JDBC 字符集**：`characterEncoding=UTF-8`（不是 utf8mb4），配 `connectionCollation=utf8mb4_unicode_ci`。
6. **前端路径**：页面在 `/gongpu/` 子路径下，fetch 必须用 `API_BASE` 相对拼接；nginx 反代用 `^~` 前缀匹配，防止 API 路径被静态正则截走。
7. **XSS**：用户名/墓志铭/槽名进 innerHTML 前必须过 `esc()`。
8. **ddl-auto=validate**：改实体 = 改 schema.sql + deploy.sh 迁移列表，三处同步。
9. **前端渲染抛错会静默中断进游戏分支**：`applyView` 里任何一处抛错，`enterGame` 的后续分支（天赋觉醒仪式/死亡/转生弹窗）就永远到不了，界面却照常渲染，极难察觉。v3.14 的 buff 签名守卫引用了已被内联删除的 `renderBuffs._sig`，每次渲染存档视图必抛 ReferenceError，新手觉醒仪式因此从未触发；2026-11-02 改挂 `applyView._buffSig` 修复。给函数挂 `_sig` 属性前先确认函数还在。
10. **灌库字符集**：见「数据库」节——mysql CLI 必须 `--default-character-set=utf8mb4`；双重编码的修复式：`CONVERT(BINARY(CONVERT(col USING latin1)) USING utf8mb4)`（`desc` 等保留字列名要加反引号）。JDBC 侧不受影响。
11. **前端静态版本**：静态资源走 immutable 长缓存，改 `web/` 后必须同步提升 `index.html` 的 `?v=` 参数（当前 v=52），否则玩家继续用旧 JS；136 的 no-cache 部署同理确认。

## 配置主表与运维端（v3.0）

游戏数值不再硬编码：**6 张配置主表**入库，运行时由 `GameData` 注册表加载（DB 优先、GameConst 兜底），运维端改表后热刷新即时生效：

| 表 | 内容 | 行数 |
|---|---|---|
| cfg_equipment_template | 装备模板（底子属性 JSON/tier/最低区域） | 25 |
| cfg_affix | 随机词条池 | 13 |
| cfg_item | 商店商品（药剂/消耗品） | 6 |
| cfg_skill | 技能（6 主动 + 8 被动） | 14 |
| cfg_buff | 状态效果（增益/减益） | 9 |
| cfg_drop_rate | 区域爆率 + batch_cap 护栏 | 8 |

**运维端**：`http://192.168.215.136:8088/gongpu/admin.html`（独立页面，与玩家端分离）
- 管理密码在服务器 `/etc/gongpu/app.env` 的 `ADMIN_PASSWORD`（登录后发 2 小时管理 token）
- 功能：账号查询/重置密码/删除；六张配置表的新增/编辑/删除/启停；爆率调整；配置热刷新；操作审计日志（admin_audit_log）
- 所有配置写操作自动触发 `GameData.reload()`，玩家无感生效

## 玩法系统清单（v3.10，两轮十轮优化后）

- **药剂 8 种**：小/大血药、蓝药、兔肉、解毒剂、速度药剂 + 修行药剂（成长×2/120s）+ 铁壁药剂（双防×1.5/60s）
- **每日签到**：金币+药剂礼包，世代加码（gen≥1 送修行药剂、gen≥3 送铁壁药剂）
- **装备深度玩法**：词条洗炼（可锁定 1 条，费用×3）、一键卖白装、一键穿最强（评分换装）
- **Boss 首杀奖励**：每 Boss 一次性金币大奖（1000×区域深度，平原 2000~火山 7000）
- **枪械三线**（v3.2）：物理弹/物理附魔弹/纯魔法弹（BULLET_MP 每发耗蓝，无蓝哑火，回蓝自动恢复）；加特林 ASPD 8
- **委托与合成**（v3.3）：冒险公会委托（属性门槛+杀怪计数）+ 图纸合成；负重腰带
- **觉醒天赋**（v3.5）：7 种，两大顶级机缘（被动永续/无限装备栏）各 45% 只能靠命；无限装备栏每天 +1 无相位栏
- **挂机管家**（v3.4+）：自动嗑药/补 buff/逃跑线 + 自动卖装（品质折现）+ 自动买药 + 自动飞升
- **v3.10 十轮优化**：词条全部直接数值（率型按百分点显示）；头像属性浮层+大页签；转生/重开统一重置（药价阶梯每世清零）；GameData 热路径索引化；部署链补齐全部内容迁移
- **世界公告**：史诗装备掉落全服播报（顶栏公告条）
- **称号系统**（9 个）+ **怪物图鉴**（击杀点亮，转生不清空）
- **觉醒天赋 7 种**：两大顶级机缘（♾️ 被动永续 / 🧳 无限装备栏）各 **45%** 抽中，抽中即"万中无一"金色暴雨仪式；无限装备栏每天 +1 个无相位装备栏（任何装备可穿、属性全叠加、转生保留），其余天赋平分 10%（可自选）
- **挂机策略**：自动嗑药血线/蓝线可配置，服务端权威执行（在线与离线均生效）
- **战斗体验**：伤害/暴击/击杀飘字、低血量红色警示闪烁、离线收益提示

## 设计口径说明（有意调优项，与 DESIGN.md v0.4 的差异）

以下差异为上线实测后的有意调优，非缺失：

| 项 | 设计原文 | 实际实现 | 原因 |
|---|---|---|---|
| 燃烧生命 | 烧 30% 当前血 / 10s 攻×2 | 烧 25% / 300s 可常驻 | 常驻燃烧是核心 build（配合永续天赋），10s 太短无法形成流派 |
| Boss 沉默 | 封被动 30s | CD 15s < DUR 30s（近乎常开） | 毕业区 Boss 威慑设计，沉默领域常开是特性 |
| 速度药剂来源 | 委托奖励 | 商店直售 + 矿洞委托奖励双通道 | 委托已提供（mine_guard），商店保底获取 |
| 初始面板 | MP50/PATK1/PDEF1/REGEN0/ASPD1.0 | MP80/PATK2/PDEF2/REGEN2/ASPD2.0 | 新手体验调优（旧版原型即此数值，四轮实测沿用） |
| 委托击杀计数 | 按目标区域分计数 | 全局击杀计数（基线差值） | 服务端权威结算架构下引擎解耦设计，进度通用化 |

## 迁移状态（v3.9 全站审计后）

设计文档 §0-16 与 legacy 原型已完成**全量对照审计**（两轮智能体矩阵），除以下记录项外全部迁移：

- ✅ 已补齐：离线死亡真实结算(§10.2)/新手保底(前5杀武器+回春戒)/「每秒回复X%生命上限」词条/格挡反弹50%/
  伤害端 ACC×连击漏斗(miss 无效果)/±10% 伤害浮动/承伤 max(1)/村口自动快照/尸检复活建议+策略快照+击杀序列/
  autoRebirth 自动飞升/挂机自动售卖/自动买药/毒时逃跑线上浮/赋能到顶置灰/window.__game 管家接口/音效埋点
- 有意调优（设计口径说明表见上）：燃烧 25%、初始面板新手调优、Boss 沉默全局 CD 15s、怪物指定掉落改区域化+商店/烹饪补充
- 仍开放（双版共同欠账或低优）：自动进食/目标优先级(§10.1)、Base64 存档导出导入(云存档下低优)、
  圆满金碑(12世集齐置顶)、怪物 缴械/破甲/狂暴 三系标签(legacy 也未实现)、管家悬浮面板 UI(接口 __game 已就绪)

- ~~离线结算未做离线死亡判定~~ **已验证迁移完成**：离线与在线共用战斗流程，打不过的怪离线真实击杀（§10.2/已定决策#10），回游戏进死亡画面+完整尸检→回档或重开；OfflineDeathTest 锁死语义
- ~~成就为纯前端展示层~~ **已落库（v3.3）**：18 项成就服务端权威评估（Achievements.java），随 state_json 持久化，换设备/清缓存不丢
- ~~旧版自动管家的 autoBurn/autoSkill buff 常驻链未迁移~~ **已迁移（v3.4）**：挂机管家新增「技能常驻」「燃烧常驻」开关，服务端 GameEngine.autoBuffTick 按 legacy 语义执行（涌动先行免蓝→急速→蓄力常驻、血<60% 补格挡、血<30% 怒吼、血>60% 补燃烧），离线同样生效

## 变更记录（2026-11-02 · 公网部署轮次）

- **修复 注册/登录 403**：CORS 白名单启动参数补公网域名，见「公网部署」节。
- **修复 新手天赋觉醒仪式永不触发**：`web/js/app.js` 悬空引用 `renderBuffs._sig`（开发注意 #9），改挂 `applyView._buffSig`；真实浏览器全流程实测：新号建档即弹仪式，抽卡/确认/进游戏正常。
- **修复 cfg 配置文本乱码**：灌库客户端字符集错误致双重编码（开发注意 #10），8 张 cfg 表 45 个文本列已反转还原；玩家表未受影响。
- **优化 轮询写放大**：`GameController.run()` 的纯状态轮询帧改走 `SaveService.persistThrottled`——缓存与成就评估每帧照旧，MySQL 落库每档最多 5 秒一次，死亡状态强制立即落库；显式动作仍全量 persist。此前每玩家每秒 1 次全量写库，现降至约 1/5（遗留清单 #4 的 persist 部分同此）。
- **优化 重复账号查询**：`run()` 内两次 `accounts.require(uid)` 合并为一次（遗留清单 #4 的 username 部分；JWT claim 方案未动）。
- **新增 部署适配**：`DeploySocketConfig`（Redis Unix 套接字）+ MariaDB localSocket 启动参数，均为 Muse VM 的 TCP 限制适配，136 环境不启用。

## 技术遗留清单（v3.10 十轮优化后，按价值排序）

审计已识别、本轮有意不动的较大手术（各自值得独立轮次+专项回归）：

1. **锁模板收口**：lock→try→finally unlock 在 Game/Save/Social 三个 controller 各抄一遍，抽 `StateCache.withLock(uid, slot, Supplier)`；顺带复审锁 TTL 10s 与离线大帧耗时的耦合
2. **SocialService 写路径收口**：祭奠/香火复活直读 DB 改 JSON 再 save,绕过 persist(不评估成就/不推时间)——改走 `saves.load + persist`
3. **StatDailyAggregator**：每小时 `saves.findAll()` 全表 LONGTEXT 反序列化,玩家量上千后拖垮 DB/GC——punches/kills 冗余成数值列走 SUM SQL
4. **轮询瘦身**：GET /state 每次全量 JSON 编解码 + ~~每请求 2 次 DB 查用户名~~（2026-11-02 已合并为 1 次，见变更记录；username 进 JWT claim 仍可再进一步）
5. **榜单 submitBoard** 读-比-写三步非原子(并发残留旧 member);throttle Map 只增不删
6. **BULLET_MP 会被强化放大**(测试 Agent 发现):魔弹枪强化后每发更耗蓝,若非有意设计应从强化乘区排除
