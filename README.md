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
│           └── gongpu-full.sql               # 数据库全量脚本（唯一入口,全程幂等）:
│                                             # §1 表结构 + §2 历史加列守卫 + §3 基线种子 + §4 内容迁移
│                                             # （原 schema.sql/seed-v3.sql/20 个 migration 已整合删除）
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
- `spring.jpa.ddl-auto=validate`：实体与表不匹配会**启动失败**——改实体必须同步改 gongpu-full.sql（§1 表结构；新列另在 §2 加守卫式加列）
- 新装库：`mysql --default-character-set=utf8mb4 < server/src/main/resources/gongpu-full.sql`（root 执行，含建库；存量升级/重复执行同一文件）
- **灌库字符集（必读）**：mysql CLI 导入任何含中文的 SQL 必须带 `--default-character-set=utf8mb4`（或在 my.cnf 的 [client] 段预设）。客户端按 latin1 读 UTF-8 文件会把文本双重编码存入（页面显示 å°è¡€ 式乱码），2026-11-02 公网库已中招并逐列反转修复，详见开发注意 #10。

---

## 公网部署（Muse VM · shaun88.cc.cd，2026-11-02 上线）

第二个部署目标，与 136 并行，专为这台受限 VM 适配：

- 公网地址：`https://pugong.shaun88.cc.cd/gongpu/`（Cloudflare 命名隧道 `muse-vm` → 本机 nginx :8088，配置同 deploy/games.conf）
- 部署目录：`~/workspace/pugong-deploy/`（start-gongpu.sh 启动后端，app.env 存密钥 600，supervise + cron 看门狗保活整条链路）
- **运行时全在持久层**：这台 VM 会被平台整体替换，系统包靠 os-intent 重放恢复、耗时几十分钟（2026-11-02 晚实测宕机约 15 分钟）。故 JDK 21（Temurin tarball）、Maven、MySQL 8.0.46 / Redis 7.0.15 / nginx 1.24（官方 deb 用 dpkg -x 解包）全部放在 `pugong-deploy/runtime/`（jdk/、maven/、sysroot/、bin/ 包装脚本），supervise.sh 与 start-gongpu.sh 把 `runtime/bin` 置于 PATH 首位——VM 替换后看门狗直接从持久层拉起全栈，不等系统包。注意：mysqld 包装脚本里 `--defaults-file` 必须保持为第一个参数，附加参数（--basedir/--secure-file-priv）只能追加在末尾；nginx.conf 的 mime.types 也指向 sysroot 副本。
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
8. **ddl-auto=validate**：改实体 = 改 gongpu-full.sql（§1 表结构 + §2 守卫加列），deploy.sh 已是单一入口无需改清单。
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
- **装备深度玩法**：词条洗炼（可锁定 1 条，费用×3）、分级一键卖（白/绿/蓝/紫及以下四档，紫档即全卖）、一键穿最强（评分换装）；挂机自动售卖同步四档，选定档位及以下掉落直接折现不进背包（选蓝档则只有紫装入包，选紫档则背包不进装备）
- **怪物词条**（v3.15）：野怪出生按区域深度掷词条——💢狂暴（残血攻速 +50%）/🪓破甲（无视 30% 双防）/🔗缴械（玩家普攻 -15%）；平原 10% 起每深一层 +3%，词条池随深度解锁，Boss 与村口木桩不出；怪物名后缀词条 emoji，描述行直读词条说明
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
  圆满金碑(12世集齐置顶)、管家悬浮面板 UI(接口 __game 已就绪)。怪物 缴械/破甲/狂暴 三系标签已于 v3.15 落地为「怪物词条」系统

- ~~离线结算未做离线死亡判定~~ **已验证迁移完成**：离线与在线共用战斗流程，打不过的怪离线真实击杀（§10.2/已定决策#10），回游戏进死亡画面+完整尸检→回档或重开；OfflineDeathTest 锁死语义
- ~~成就为纯前端展示层~~ **已落库（v3.3）**：成就服务端权威评估（Achievements.java，2026-11-03 起 24 项），随 state_json 持久化，换设备/清缓存不丢
- ~~旧版自动管家的 autoBurn/autoSkill buff 常驻链未迁移~~ **已迁移（v3.4）**：挂机管家新增「技能常驻」「燃烧常驻」开关，服务端 GameEngine.autoBuffTick 按 legacy 语义执行（涌动先行免蓝→急速→蓄力常驻、血<60% 补格挡、血<30% 怒吼、血>60% 补燃烧），离线同样生效

## 变更记录（2026-11-03 · SQL 整合）

- 拉入 136 环境提交（deploy.sh 迁移清单补全 v3.16~v3.24；DeploySocketConfig 改条件启用 `gongpu.redis.socket`，常规环境回归 TCP、Muse VM 靠启动参数走套接字）。
- resources 下 22 个 SQL 文件（schema.sql + seed-v3.sql + 20 个 migration）整合为单一 **`gongpu-full.sql`**：§1 表结构 → §2 历史加列（information_schema 守卫式，缺列才加；v3.16/v3.17 一次性回填仅在本次真加列时执行，避免重复回填冲掉终身口径）→ §3 基线种子 → §4 内容迁移（按版本序）。全程幂等：临时库实测全新导入与现行库 cfg 内容逐行一致、二次导入无错。
- deploy.sh 改为单一全量脚本入口（删除 migrate() 内联补列与 CONTENT_MIGRATIONS 清单，验收口径更新）；GunplayTest 改读整合文件。

## 变更记录（2026-11-03 · 第五批十轮增强）

主题：回收经济、怪物词条、成就闭环。本地内网自测，单测 242 → **262 全绿**，前端升 v=72。

1. **装备分解系统**：装备回收从「只有卖金」变双通道——POST /{slot}/dismantle 按来源区域分解为材料（Item 新增 zone 打标，旧档兜底兽皮；数量=品质+1、强化≥5 再+1），喂锻造合成；锁定件拒绝，Stats.dismantles 终身计数。
2. **自动分解**：autoSmeltKeep 档位（与自动售卖并存、分解优先），拦截决策抽 GameService.dropIntercept/grantDismantle 单点，逐杀与溢出两条掉落路径同口径；管家面板与药剂页双入口配置。
3. **怪物词条「💰富饶」**：击杀金币 ×3 的收益型词条，森林起入池（逐杀结算口径，溢出期望杀不吃词条）。
4. **怪物词条「⚡疾影」**：攻速常驻 +50%，矿洞起入池；狂暴残血加速与疾影统一抽到 traitHasteMul。词条池至此 7 种。
5. **战绩统计**：Stats 新增终身 totalDamage/bestHit（普攻帧累加、代表拳伤刷新最高单击），角色页统计表展示。
6. **成就里程碑扩充（24 → 28）**：万怪斩（终身击杀 1 万）、富甲一方（100 万金币）、屠龙者（Boss 10 杀，Stats.bossKillCount 在击杀漏斗计数）、开山一击（单击 100 万，吃第 5 轮 bestHit）。
7. **成就奖励领取**：Achievements.rewardOf 四档奖金（100/500/2000/5000），POST /{slot}/ach-claim 一成就一领、achClaimed 持久防重；金额由 view 单源下发，成就墙带领取按钮。（兼补第 6 轮前端表同步：achievements.js 补 4 条、总数断言对齐 28。）
8. **背包评分与对比显示**：view 新增 bagScores/equippedScores（复用 equipScore 单一口径，视图附件不污染 Item 持久化），背包行显示评分与对同部位已穿装备的升降差值。
9. **新被动「巨人杀手」**：对 Boss 伤害 +20%（被动 8→9）；GameConst 镜像、cfg_skill、DataParityTest 被动镜像锁三处同步。
10. **委托扩容（9 → 12）**：矿洞/沼泽/雪原各补 1 个高阶委托（深入矿脉/毒雾深处/雪原狼王），奖励用上第四批新药剂（远眺/鸿运/万灵）。

内网实测：全量导库后技能 15/委托 12；测试号实测分解旧档紫装出兽皮 ×4、成就领奖 +500 且重复领被拒、自动分解 3 分钟内 14 杀 2 件掉落全转材料背包恒定、战绩字段实时累积。

## 变更记录（2026-11-03 · 第四批十轮增强）

主题批次：玩家点名**药剂种类增强（各维度）**与**装备种类增强**。每轮独立提交，单测 223 → **242 全绿**，前端升 **v=67**。本地流程：内网起栈自测（含 7 个新迁移真实导库），不部署公网。

1. **药剂·回复系**（18 → 21 种）：特大血药（瞬回 100% 生命）、大蓝药（瞬回 100% 蓝量）、万灵药剂（双回 80%）——补高阶与双回维度（migration-v3.18）。
2. **药剂·新维度增益**（21 → 24 种）：生生药剂（回血 +25/秒）、远眺药剂（射程 +1）、疾行药剂（移速 +40）——三个此前无药剂覆盖的维度，面板接线且同受沉默压制（migration-v3.19）。
3. **药剂·收益维度**（24 → 25 种）：鸿运药剂——300 秒内装备与图纸掉落率 ×1.5，`GameEngine.dropLuckMul` 统一逐杀/溢出期望/图纸三处口径（migration-v3.20）。
4. **药剂·功能增强**：解毒剂兼清沉默——中毒/灼烧/沉默一剂三清（沉默恰是让药剂失效的状态，此前无解；migration-v3.21 同步描述）。
5. **装备·新词条**（14 → 16）：轻盈（闪避，此前只有药剂能加）、活力（生命上限 50~300——本作核心维度此前竟无词条覆盖）；旧档迁移表补 maxHP 恒等区间防静默失效（migration-v3.22）。
6. **装备·区域补缺 I**（36 → 41 件）：沼泽此前全区域仅 1 件，补毒牙匕首/沼泽法袍/瘴气护符；火山补熔岩巨剑/炎魔之盔（migration-v3.23）。
7. **装备·区域补缺 II**（41 → 45 件）：冰霜护腿/雪原长靴/熔火壁垒/瘴气软靴——补缺后区域分布平原 9/森林 9/矿洞 8/沼泽 5/雪原 8/火山 6，无区域低于 5 件（migration-v3.24）。
8. **前端**：商店药剂分组——meta 下发 group（服务端按回复/增益/功能计算），25 种分三组带标题渲染。
9. **测试基建**：数据一致性锁 DataParityTest——解析全部 SQL 资源断言药剂集合与代码镜像恰好相等、装备 ≥45 件且区域覆盖达标、base_stats 全为合法数值 JSON；数据双写漏同步任一边单测立即红。
10. **成就联动**（22 → 24 项）：品药成就——PlayerState 记录用过的药剂种类（usedPotions），🧪品鉴五味（5 种）/🌿神农后人（15 种）；内网实测喝满 5 种新药即时点亮。

## 变更记录（2026-11-03 · 第三批十轮增强）

再做十轮增强/优化/完善（每轮独立提交，另加 1 个实测修复提交），单测 199 → **223 全绿**，前端版本升 **v=65**。仍按本地流程：内网起栈自测，不部署公网。

1. **修复（统计口径）**：运营统计改终身口径——第二批的冗余列存的是当世 punches/kills，每世重置使全服 SUM 非单调、日增量被钳零失真（实测当日出拳显示 0）。Stats 新增 `lifetimeKills`（两处击杀点同步累加），冗余列改存 lifetimePunches/lifetimeKills；旧档解码以当世 kills 为终身种子（单调不回退）；`migration-v3.17` 回填存量（本地库实测回填后列 SUM 与 JSON 终身口径逐项相等）。
2. **玩法（大件）**：图纸合成系统接通——cfg_craft 配方、图纸掉落池（`GameData.randomCraftBlueprint`）、产物工厂（`EquipFactory.generateCraft`）此前全部配齐却无一处调用，图纸根本不掉、合成无入口。本轮接通：击杀按 Boss 30%/普通怪 4% 掉图纸（材料键 `bp_<配方>`）；新增 `CraftService` 合成板与合成（图纸/材料/金币/背包全校验后才扣料；基础配方品质 1、图纸配方品质 2、25% 升一品封顶 3）；`GET /{slot}/crafts` + `POST /{slot}/craft`；前端新增 ⚒️锻造 tab 与合成面板。内网实测后补修：数据侧另有 `item:<物品code>` 约定的烹饪类配方（烤兔肉等），产物走药剂栏叠加、不占背包。
3. **玩法**：怪物词条扩容——新增 🌵荆棘：玩家本帧所造伤害的 10% 反扎自身（护盾先吸、可扎死，死因记在该怪头上）；词条池雪原起五种全开，沼泽仍四种。
4. **功能**：修改密码——`POST /api/auth/password`（登录态）：验原密码 → 新密码 ≥6 位且不得与原密码相同 → 换哈希，旧密码立即失效、当前会话不受影响；前端选档页加「修改密码」入口（内网往返实测通过）。
5. **体验**：JWT 滑动续期——令牌剩余 <24h 时响应头 `X-New-Token` 回新令牌，api.js 无感替换；活跃玩家永不被 7 天 TTL 踢下线，闲置满 TTL 才过期（内网用自签 1 小时令牌实测续期链完整）。
6. **前端**：背包售价显示——卖出按钮标注确切售价（与服务端 `EquipFactory.sellPrice` 同公式，改价需两边同步）。
7. **修复**：图鉴总数口径对齐——面板此前把无限血木桩算进总数（永远差 1 集不满），renderCodex 跳过 maxHP=Infinity 的怪行，与服务端 `codexTotal` 同口径。
8. **前端**：死亡状态轮询降频——死亡后世界冻结，轮询由 1 秒改 5 秒一帧（回档/重开是写请求、即时返回新视图），存活挂机不变。
9. **性能**：QuestService 改静态共享 ObjectMapper + reqJson 按原文缓存；并补上该服务首个单测（接取→进度→交付全链路）。
10. **玩法**：成就扩充——成就 20 → 22 项：⚒️初试锻造、✨初试附魔（Stats 新增终身计数 crafts/enchants，合成/附魔成功时累加；内网实测合成后 first_craft 即时点亮）。

## 变更记录（2026-11-03 · 第二批十轮增强）

再做十轮增强/优化/完善（每轮独立提交），单测 172 → **199 全绿**，前端版本升 **v=58**。按玩家新定流程：本轮只在本地内网启动自测（本机栈起服 + API 冒烟 + 浏览器界面核对），不部署公网。

1. **性能（遗留 #3 完全销账）**：运营统计改走冗余列 SUM——`game_save` 新增 punches/kills/deaths/rebirths 四列，`SaveService.persist/rollback` 与 JSON 内 stats 同步维护，`StatDailyAggregator.collect` 改一条 SUM 查询，不再分页拉全表 LONGTEXT 解析 JSON；`schema.sql` 同步、存量库走 `migration-v3.16-stats-cols.sql` 从 JSON 回填（本地库实测回填值与 JSON 聚合逐项相等、persist 后列值与 JSON 实时一致）。
2. **性能**：`/config/meta` 按 GameData 快照身份缓存——快照未换直接复用整棵 Map，运维 reload 换快照自动重建（此前每请求全量重建）。
3. **玩法**：装备锁定——背包件可 🔒 锁定：单卖/丢弃直接拒绝、一键分级卖与挂机管护卖白装跳过、无相位栏管护不吞噬不穿入；新增 `POST /{slot}/lock`，前端背包加锁定按钮与名称标记（防分级售卖误删心头好；内网实测 keep=4 全卖 100 件只剩锁定 1 件）。
4. **功能**：背包一键整理——`POST /{slot}/sortbag` 按装备评分（与穿最强同口径）降序稳定排序，前端加「↕️ 整理背包」按钮。
5. **修复**：重置审计补漏——护盾是 shield_pot buff 附属量（buff 消失盾即清零），`resetForNewLife` 此前只清 buffs 漏清 shield，死亡重开/转生后新一世白带无 buff 的盾；已补清零，审计测试锁死当世清零/传承保留清单（委托跨转生经审计本已一致，不动）。
6. **性能**：`/health` 探活 2 秒短缓存——未鉴权高频轮询不再每请求真打 Redis ping + DB 连接（故障感知延迟 ≤2 秒）。
7. **玩法**：怪物词条扩容——新增 🩸嗜血：命中玩家时按实际掉血 30% 吸回自身生命（封顶其上限，结算在护盾吸收后、格挡反弹前）；词条池沼泽起四种全开，矿洞仍三种。
8. **健壮性**：全局异常处理补齐客户端错误码——路径参数类型不符 400、路径不存在 404、方法不允许 405，不再全落兜底 500 打 error 日志（内网实测 `/game/abc/state` 返回 400 参数类型不合法）。
9. **性能**：榜单读取 5 秒短缓存——公开接口 `LeaderboardService.top` 按（榜，limit）缓存；上报侧本身 60 秒节流，数据新鲜度地板远高于 5 秒，不损失语义。
10. **玩法**：图鉴成就——成就 18 → 20 项：📖图鉴·十种（记录 10 种怪）、📚万物图鉴（集满可收集总数；`GameConst.codexTotal` 按引擎写入同口径：可击杀怪种 + 各区域 Boss，无限血木桩不计），前后端成就表同步、一致性测试锁死。

## 变更记录（2026-11-03 · 十轮增强）

应玩家要求对全栈做十轮增强/优化/完善（每轮独立提交），单测 169 → **172 全绿**，前端版本升 **v=55**，已部署公网实测：

1. **性能**：无限装备栏批量穿入的空位扫描 O(n²) → 单调递增线性（`nextFreeExtra` 逐件重扫改一遍扫完）。
2. **性能**：用户名进 JWT claim——拦截器一次解析出 userId+用户名，`run()` 免去每秒轮询的账号查库（遗留 #4 username 部分销账）。
3. **性能**：`StatDailyAggregator` 不再全表反序列化存档对象图——分页 200/页 + 只读 JSON 树 stats 子树精确累加（遗留 #3 部分销账）。
4. **架构**：`SocialService` 祭奠/复活写路径收口 `SaveService.load+persist`（原手写绕过成就评估与冗余列、且直读 DB 会撞节流落库的旧金币；遗留 #2 销账）。
5. **架构**：锁模板收口 `StateCache.withLock/lockRetry`，三控制器统一短等重试语义，告别各抄一份与瞬时 409（遗留 #1 销账）。
6. **前端**：轮询失败退避——断网/服务端重启时连败间隔指数放大（封顶 30s），成功清零，切回前台立即补帧。
7. **玩法**：圆满金碑——飞升金碑集齐 12 个不同世代的玩家，其金碑在世界墓园置顶、标注「🌕 圆满金碑」配金色辉光（纯纪念无数值加成；`PerfectGraveTest` 锁判定）。
8. **前端**：管家悬浮面板——右下角 🤖 悬浮卡：血/蓝条速览、当前怪、自动管护开关、自动售卖档位、嗑药/吃食快捷按钮，不必切 tab 照看挂机。
9. **功能**：存档导出——选档页每档「导出」按钮下载存档 JSON 自备备份；只出不进（导入绕过服务端校验，明确不开放）。
10. **玩法**：挂机自动进食——管家新开关：饱食回血断档自动吃食物续上（buff 在身不浪费、村里不吃；`AutoEatTest` 锁语义），悬浮面板与管家 tab 双入口。

公网实测：自动进食开后买 2 份食物、一帧结算自动吃掉 1 份且饱食回血生效；导出接口返回附件头与完整存档 JSON；墓园/轮询/榜单全链路走新 JWT claim 路径正常。

## 变更记录（2026-11-03 · 分级售卖）

- **手动一键卖分级**（玩家需求）：背包区由单个「卖白装」按钮改为四档按钮组——卖白装/卖绿装及以下/卖蓝装及以下/卖紫装及以下（`autosell {keep}` 1~4，后端合法范围 3→4，keep=4 即卖 quality<4 的全部装备含史诗）。
- **掉落拦截扩档**：挂机自动售卖 `autoSellKeep` 钳制 0~3 → 0~4，管家下拉新增「紫色及以下（全折现）」；拦截逻辑本身通用（两条掉落分支均按 quality<keep 折现），选蓝档只有紫装进包、选紫档背包不进任何装备，正是玩家举的例子。
- **验证**：新增 SellTierTest 4 项（分档保留/紫档卖空+越界拒绝/钳制放宽/紫档拦截零入包），单测 **169 全绿**；公网实测：测试号背包 11 件（白1绿6蓝1紫3）keep=2 后剩蓝1紫3、keep=4 卖空、keep=5 被拒；autoSellKeep=4 下击杀增长而背包恒 0（测后已恢复该号设置为关闭）。

## 变更记录（2026-11-03 · v3.15 增强轮次）

- **新增 怪物词条系统**（玩法清单见上）：`GameConst.MOB_TRAITS` + `rollMobTrait(zoneId)`，出生掷骰、随存档持久化（`MobState.trait`，旧档无字段即普通怪）；效果落在 `GameEngine.dealDamage/mobAttack` 三处结算点；`mobView` 下发完整展示信息（含说明文），前端只渲染。遗留多年的 legacy 未实现项就此销账。
- **修复 BULLET_MP 被强化放大**（遗留 #6）：`PanelCalculator` 底子合并时成本属性不吃强化乘区——魔弹枪 +10 后每发耗蓝不变、魔攻照常 +50%（与吞噬合并的「成本不累加」同一口径）。
- **修复 榜单提交非原子**（遗留 #5）：`LeaderboardService.submitBoard` 改为单条 Redis Lua 脚本（读旧分→比大小→换 member→写新分一次做完），并发上报不再残留旧 member；60s 节流表超 4096 条时顺手清理过期项，不再单调膨胀。
- **优化 面板重复构建**：单次 `advance` 内原约 8 次 `PanelCalculator.of` 全量构建砍掉 3 处可证等价的——`grow` 复用主面板（只读 ACC，不受期间血蓝变动影响）、`autoPotion` 三分支懒构建共用一份（只读上限，嗑药/解毒不改上限）；其余构建点都在状态变更之后，语义必需，保留。
- **前端**：怪物名后缀词条 emoji、描述行直读词条说明；`<title>` 改为正式名「普攻永久增加生命上限 · 一拳一点命」并补 meta description / theme-color / Open Graph；静态版本升 v=53。
- **验证**：单测 160 → **165 项全绿**（新增 V315EnhanceTest 五项：耗蓝不吃强化/词条池区域约束/三词条结算数值）；顺带补齐本机离线测试依赖（`pugong-deploy/seed-test.py`），Muse VM 上 `mvn -o test` 可直接跑通；公网实测：平原刷出 💢狂暴野兔、词条数据与界面渲染均确认，新账号经原子榜单路径正常上榜。

## 变更记录（2026-11-02 · 公网部署轮次）

- **修复 注册/登录 403**：CORS 白名单启动参数补公网域名，见「公网部署」节。
- **修复 新手天赋觉醒仪式永不触发**：`web/js/app.js` 悬空引用 `renderBuffs._sig`（开发注意 #9），改挂 `applyView._buffSig`；真实浏览器全流程实测：新号建档即弹仪式，抽卡/确认/进游戏正常。
- **修复 cfg 配置文本乱码**：灌库客户端字符集错误致双重编码（开发注意 #10），8 张 cfg 表 45 个文本列已反转还原；玩家表未受影响。
- **优化 轮询写放大**：`GameController.run()` 的纯状态轮询帧改走 `SaveService.persistThrottled`——缓存与成就评估每帧照旧，MySQL 落库每档最多 5 秒一次，死亡状态强制立即落库；显式动作仍全量 persist。此前每玩家每秒 1 次全量写库，现降至约 1/5（遗留清单 #4 的 persist 部分同此）。
- **优化 重复账号查询**：`run()` 内两次 `accounts.require(uid)` 合并为一次（遗留清单 #4 的 username 部分；JWT claim 方案未动）。
- **新增 部署适配**：`DeploySocketConfig`（Redis Unix 套接字）+ MariaDB localSocket 启动参数，均为 Muse VM 的 TCP 限制适配，136 环境不启用。

## 技术遗留清单（v3.10 十轮优化后，按价值排序）

审计已识别、本轮有意不动的较大手术（各自值得独立轮次+专项回归）：

1. ~~**锁模板收口**：lock→try→finally unlock 在 Game/Save/Social 三个 controller 各抄一遍，抽 `StateCache.withLock(uid, slot, Supplier)`~~（2026-11-03 十轮增强第 5 轮已修：withLock+lockRetry 收口 StateCache，三控制器统一短等重试语义；锁 TTL 与离线大帧耦合复审未动，仍观察）
2. ~~**SocialService 写路径收口**：祭奠/香火复活直读 DB 改 JSON 再 save，绕过 persist~~（2026-11-03 十轮增强第 4 轮已修：改走 saves.load+persist，成就/冗余列/缓存口径与主流程一致）
3. ~~**StatDailyAggregator**：每小时全表 LONGTEXT 反序列化聚合~~（2026-11-03 第二批十轮第 1 轮完全销账：game_save 加 punches/kills/deaths/rebirths 四冗余列、persist 同步维护、collect 改一条 SUM 查询；schema.sql + migration-v3.16 回填存量）
4. **轮询瘦身**：GET /state 每次全量 JSON 编解码（仍在） + ~~每请求查用户名~~（2026-11-03 十轮增强第 2 轮已修：用户名进 JWT claim，run() 免查库）
5. ~~**榜单 submitBoard** 读-比-写三步非原子(并发残留旧 member);throttle Map 只增不删~~（2026-11-03 v3.15 已修：改 Redis Lua 脚本原子提交，节流表超 4096 条自动清理过期项，见变更记录）
6. ~~**BULLET_MP 会被强化放大**(测试 Agent 发现):魔弹枪强化后每发更耗蓝~~（2026-11-03 v3.15 已修：PanelCalculator 强化乘区排除 BULLET_MP，与吞噬合并不累加同口径，V315EnhanceTest 锁死）
