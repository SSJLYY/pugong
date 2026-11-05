# GONGPU-SKILL · 《普攻永久增加生命上限》项目操作手册

> 新会话读这一份即可直接开工：连服务器、改代码、构建、部署、验证、排错。
> 周末无内网 → 直接走第 8 节本地闭环（git/编译/起服/冒烟/单测全套齐备）。

## 0. 一句话项目画像

放置类网游（出拳永久涨血，1e44 攻速通关转生）。**前后端分离 + 服务端权威**：
前端纯静态（nginx），后端 Spring Boot 3.2.5 + Java 21（8081），
MySQL 8 持久 + Redis 缓存/锁/榜单。数值全部服务端结算，前端只发意图。

## 0.5 当前状态速览（2026-11-05 更新，第十二批 100 轮后）

- **第十二批一百轮增强（2026-11-05，本批按玩家要求全程单次提交；本地内网自测）**：新系统——宝石三阶（GEMS 12→18：buildGems 自动生成三阶「曜」×5.5，fuseGem 二阶→三阶分流、fuseAllGems 级联，gemTier1Ids 必须排除 _2/_3 双后缀否则三阶进掉落池；Stats.gemTier3Fused；equipScore 三阶 +500）/ 第十三区域归墟界 guixu（门槛 5000 亿血/双防 20000/攻 100 万，Boss 归墟之主 5e13 血，归墟珠售 60000、归墟战装第 11 套、图鉴 48）/ 区域精通（Stats.zoneKills 分区域终身击杀：GameEngine.zoneMasteryMul 每 500 杀 +2%、封顶 +20%，onMobKilled 累加，dealDamage 与 expectedPerHit 双点同口径，fullView 下发 zoneKills+masteryPct，StateCodec 剔除未知区域键/负数钳 0）。战斗深化——怪物词条 26→29（🔔金钟 dealDamage 入口整帧归零一次且不吞一次性增益、🧪腐蚀 mobAttack defMul 单点叠层 -3%/层上限 10、⛈️雷霆 mobAttack 额外 PATK×30% 真伤归墟专属）/ 主动天罚（下一击 ×6）/ 被动归墟体（暴击 +5%）+ 潮汐之体（闪避 +6%）。任务经济——周常/月常模板 +awaken/evolve 两类（counterOf 共用 awakens/petEvolves）、黑市池 24→28、成就里程碑扩八档（120→500 万）。前端——第七主题深海 theme-tide、成就搜索框、🚀最深可进区域按钮、区域精通角标、称号 19→21（归墟主宰压轴）。内容——药剂 46→47（归墟琼浆）、词条 32→34（归墟/瀚海）、技能 39→42、委托 34→38、配方 34→38、装备 90→98、天赋 14→15（星辰之心：宝石 ×1.5 与宝石亲和在 PanelCalculator gemMul 叠乘）、成就 113→128。前端升 **v=226**。收官总口径见 Batch12FinalCountsTest。详见 README「变更记录（2026-11-05 · 第十二批一百轮增强）」。
- **第十一批一百轮增强（2026-11-04，本批按玩家要求全程单次提交；本地内网自测）**：新系统——宠物进化（PlayerState.petEvolved 集合：满级 50 可进化、属性 ×2，成本 20 万金 + 混沌核心 ×3，端点 /evolve-pet，petView 下发 evolved；StateCodec 兜底剔除未领养/图鉴外）/ 宠物 8→9（麒麟 pet_qilin）/ 第十二区域鸿蒙界 hongmeng（门槛 500 亿血/双防 8000/攻 400000，Boss 鸿蒙道祖 5000 亿血，鸿蒙紫气售 20000、鸿蒙战装第 10 套、图鉴 43）/ 装备觉醒（Item.awaken 0~3、满 5 星前置、底子第四乘区 +5%/级，成本 10 万×级金 + 混沌核心×级，端点 /awaken，StateCodec 钳制 [0,3]，吞噬取 max）。战斗深化——怪物词条 23→26（💀不朽首次致死保留 30% 血、👹吞噬攻击额外真伤 2% 上限、🕳️太虚自带 25% 闪避等效普攻 ×0.75 鸿蒙专属）/ 主动万剑归宗（下一击 ×5）/ 被动鸿蒙体（攻速 +0.10）+ 太虚剑意（暴伤 +20%）。任务经济——周常/月常模板 +star/treat 两类（counterOf 共用）、黑市池 20→24、成就里程碑奖金扩七档（105→300 万）。前端——第六主题极光 theme-aurora、日志搜索、页签标题 DPS 峰值、进化/觉醒按钮、称号 17→19（鸿蒙主宰压轴）。内容——药剂 44→46（鸿蒙灵液/太虚神液）、词条 30→32（鸿蒙/太初）、技能 36→39、委托 30→34、配方 30→34、装备 82→90、天赋 13→14（鸿蒙之心：羁绊 ×1.5 与混沌之心叠乘，petBondMul 三参重载）、成就 98→113。前端升 **v=218**。收官总口径见 Batch11FinalCountsTest。详见 README「变更记录（2026-11-04 · 第十一批一百轮增强）」。

- **第十批一百轮增强（2026-11-04，本批按玩家要求全程单次提交；本地内网自测）**：新系统——宠物羁绊（GameConst.petBondMul：总等级 30/100/250 档 ×1.25/1.6/2.2，PanelCalculator 宠物乘区同乘、伙伴来源不压制；天赋混沌之心再 ×1.5；petView 下发 totalLevels/bondMul/bondNext）/ 宠物 6→8（熊猫/雪貂）、宠物零食 pet_treat（usePotion 特殊分支 +60 经验、无出战先校验不消耗）、板凳宠物经验分享 25%（gainPetExp 单点）/ 第十一区域混沌界 chaos（门槛 20 亿血/双防 3000/攻 150000，Boss 混沌魔君 300 亿血，混沌核心售 6000、混沌战装第 9 套、图鉴 38）/ 装备升星（Item.star 0~5、底子 +3%/星第三乘区，成本 5000×n 金 + star_shard×n，sellPrice ×(1+0.1星)、equipScore 同乘、devourInto 取 max（顺带补 temper 取 max）、StateCodec 钳制 [0,5]、端点 /star-up）。战斗深化——怪物词条 20→23（🪞镜像反弹实际承受 10% 与荆棘同段同口径、🥀虚弱普攻 ×0.85 与缴械双点同乘、🌀狂乱攻速 ×1.4/攻击 ×1.2 混沌专属）/ 主动时停（buff time_stop、mobAttack ×0.5）/ 被动时之体（ASPD 平直 +0.08 进乘区前）+ 混沌亲和（混沌界专伤 ×1.25，dealDamage 与 expectedPerHit 双点）。任务经济——月常模板 5→7（enchant/rebirth，counterOf 四新类型 feed/fuse/enchant/rebirth 共用）、每日模板 7→9、黑市池 16→20、里程碑六档（90→100 万，achMilestonePaid 位掩码自动兼容）、feed-pet-max 端点。前端——第五主题霓虹 theme-neon、petChip、DPS 峰值、成就徽标、静态模式 gongpu_motion、日志掉落过滤、Q/E 快捷键；称号 15→17（万兽之王/混沌主宰压轴，renderTitles 签名补 boss_chaos 与宠物总等级）。内容——药剂 41→44（混沌精华/凤凰之血）、词条 28→30（混沌/永恒）、技能 33→36、委托 26→30、配方 26→30、装备 74→82、天赋 12→13（混沌之心）、成就 82→98。前端升 **v=210**。收官总口径见 Batch10FinalCountsTest。详见 README「变更记录（2026-11-04 · 第十批一百轮增强）」。

- **第九批一百轮增强（2026-11-04，每轮独立提交；本地内网自测）**：新系统——宠物伙伴（GameConst.PETS 6 只，领养/出战/喂食/连喂/改名，击杀 +1 经验满级 50、等级放大 15%/级，petBonus 并入 flat 区不压制、被动宠物亲和 ×1.3 乘区受压制，petView 挂 fullView 本体）/ 第十区域虚空界 void（门槛 2 亿血/双防 1200/攻 40000，Boss 无相天魔 30 亿血，虚空碎片售 2000、无相战装第 8 套、图鉴 33）/ 月常任务（MonthlyQuests 月界 YearMonth 键、5 模板抽 3、全清 +30 万金 + 宝石 ×3，ensure 在 runLocked 落库前）/ 参照《我的文字修仙全靠刷》布局新增水墨主题 theme-ink（原创 CSS、CC BY-NC 仅借鉴设计语言）+ 纸面卡布局/属性标签网格/叙事条/顶栏综合战力 powerChip。战斗深化——怪物词条 17→20（🔋噬能命中抽蓝 10%、✴️裂空闪避减半 effEva 单点虚空专属、🩸燃血残血攻击 +60%）/ 主动虚空斩（下一击 ×4）/ 被动宠物亲和 + 招财手（goldMul ×1.15 与聚宝乘算）。经济便利——sell-all-materials/fuse-all-gems/unsocket-all/sell-extra-blueprints/mat-exchange-all/feed-pet-batch 六批量端点（与逐个等价）。内容——药剂 39→41（虚空精华/星尘甘露）、词条 26→28（裂帛/灵泉）、技能 30→33、委托 22→26、配方 22→26、装备 66→74、天赋 11→12（兽语者宠物经验 ×2）、成就 66→82、称号 12→15（虚空行者压轴）。稳健——health 指纹加 pets=、normalize 兜底宠物/月常字段（修坏档 NPE）、Batch9OrderTest 源码序锁、成就 evaluate 全覆盖锁（实修 pet_first/pet_25 永不解锁 bug）、里程碑奖金扩 75 档。前端升 **v=202**。单测 **656 全绿**；deploy.sh 验收口径同步（74/41/26/26/28/33）。详见 README「变更记录（2026-11-04 · 第九批一百轮增强）」。
- **第八批一百轮增强（2026-11-04，每轮独立提交；本地内网自测）**：新系统——宝石镶嵌（GameConst.GEMS 12 种：一阶 6 + 璀璨二阶 6，Item.gems 每件 3 孔，Boss 必掉/词条怪 10%，socket/unsocket/fuse-gem 三端点，宝石 flat 并入装备区、沉默不压制，出售/直购 ×4 与材料同端点口径）/ 周常任务（WeeklyQuests ISO 周键、5 模板抽 3、复用 DailyQuests 计数口径，全清 +50,000 金与随机宝石 ×2，ensure 在 runLocked 落库前）/ 第九区域星界 star（门槛 2000 万血/双防 400/攻 12000，4 怪 + Boss 星核主宰 3 亿血、星核碎片售 650、图鉴 28、星辰套装第 7 套）。战斗深化——怪物词条 14→17（🌿再生每秒回 3%、🪨坚韧暴击率减半 effCrit 单点、🌑星蚀暴伤超额减半 effCritD 单点且星界专属池）/ 被动破甲专精（effDef 与破甲药剂乘算）/ 主动连环掌（flurryHits 计数型双击）/ 词条首次命中预警（traitWarned）。经济与便利——图纸出售（区域深度定价）/ 材料商店 ×3 / 宝石直购 ×4 / 一键穿套装 equip-set / 金币里程碑播报（goldMilestone，10^k 跨档 events）/ 成就里程碑奖金（15/30/45/60 档，achMilestonePaid 掩码）。内容——药剂 36→39（破甲/鹰目/生机）、词条 24→26（无影/不息史诗上位）、技能 27→30（+宝石嗅觉被动）、委托 18→22、配方 18→22、装备 58→66、天赋 10→11（宝石亲和：宝石效果 ×1.25）、成就 48→66、称号 9→12。前端——背包名称搜索/顶栏 DPS 与称号速览/区域收益预览/页签快捷键 Z/X/C/V/星空主题（主题三循环）/字号三档/战斗日志过滤/装备孔位指示/委托页签徽标；升 **v=177**。稳健——health 新增 cache（StateCache 命中率）/frames（advance 帧耗时）/save（存档体积+备份环计数，共用探活连接）三段、写端点限流（10 秒 60 次 429、纯轮询不计）、隐藏标签轮询节流前置、第八批字段编解码往返与源码序锁（里程碑检查在 fullView 前、限流在 load 前）。单测 **588 全绿**（含收官实测修复 2 项）；deploy.sh 验收口径同步（66/39/22/22/26/30）。详见 README「变更记录（2026-11-04 · 第八批一百轮增强）」。
- **第七批一百轮增强（2026-11-03，每轮独立提交；本地内网自测）**：新系统——装备套装（Sets.java 六套区域 4 件套，2/4 件两档加成、沉默不压制，SetParityTest 锁成员与区域）/ 装备淬炼（Item.temper 0–5，底子 +4%/级与强化分层乘区，区域材料 + 金币成本）/ 洗炼保底（rerollPity 10 次必出史诗）+ 双锁（成本 ×3/×5）/ 新区域深渊裂隙 abyss（8 区之末，4 怪 + Boss 无光君王 30M 血、深渊结晶、装备 52→58、委托/配方各 +2、图鉴 23）/ 每日任务（DailyQuests 7 模板抽 3、终身计数基线口径、全清 +5000）/ 每日黑市（BlackMarket 16 池抽 3、3–5 折、单品限购 5）/ 材料出售（MAT_SELL_PRICE 七种区域材料）/ 一键补给包 / 总伤害榜（第四榜 damage）/ 每日首祭免费。战斗深化——怪物词条 12→14（💀诅咒回血减半、🧊寒霜攻速 -15%，healMul 单点口径）/ Boss 二阶段（phase2 血线 50% 攻速 +30% 攻击 +20%）/ 弱点打击（punchAcc 每第 10 拳必暴，期望口径差额结算）/ 🌵荆棘反扎封顶修复（基数 min(整帧伤害, 开帧目标剩余血)，死因带词条标注）。内容——药剂 34→36（狂怒复合暴击/时之砂平直攻速）、词条 22→24（破军/绝杀史诗上位）、技能 23→27（血怒/冲锋/处决者/迟缓咒 MobState.slowT）、天赋 8→10（幸运儿/铁胃）、委托 16→18、成就 34→48。前端——布局重构 v3.2（三列网格/顶栏品牌/页签滚动）、加点阶梯放大（最高 +1 亿 + 全部）、飘字三路（伤害/金币/受击）、低特效模式、宣纸主题、药剂置顶、区域就绪标记、成就筛选置顶、快捷键扩展、背包可售总价；升 **v=137**。稳健——serverTime 过期帧守卫、存档滚动备份环（backup_ring 列，decodeOrRecover 三级自愈）、慢请求指标进 health（perf 段）、BCrypt 72 字节守卫、18 端点经济审计日志（economy-audit）、弱网自适应轮询、响应压缩调优、宽松性能守卫。单测 **518 全绿**（含收官实测修复 2 项）；deploy.sh 验收口径同步（58/36/18/18/24/27）。详见 README「变更记录（2026-11-03 · 第七批一百轮增强）」。
- **第六批一百轮增强（2026-11-03，每轮独立提交；本地内网自测）**：内容总数——药剂 25→**34**（生命/金钟/大力/石肤/荆棘/巨人/聚灵/回春 + 聚宝金币维度）、词条 16→**22**（血契/磐石/猎风/生命之源/魔力源泉 + 疾雷）、装备 45→**52**（平原弓/森林胸/矿洞盔/沼泽腿/雪山胸+头/火山腿补缺）、怪物词条 7→**12**（堡垒/剧毒/晶化/潜行/时滞）、技能 15→**23**（金刚体/幸运星/鹰骨/材料嗅觉 + 治愈/嗜血/铁壁/净化）、委托 12→**14**、配方 14→**16**、成就 28→**34**（含进度条 progressOf 与一键全领）。玩法系统：战斗日志 events（环形 20）/ 自动推进 autoAdvance / Boss 连战 autoBoss / 一键分解 / 材料 3:1 兑换 / 按目标数量补给 buyfill / 每日连签（claim_streak 列 + 周奖）/ 存档改名。稳健：改密令牌版本失效（token_version 列 + claim v + 拦截器 30s 缓存比对）/ 登录防爆破（5 次锁 5 分钟）/ 存档解码自愈（主档坏回退快照）/ 时钟回拨重锚 / 415 异常映射 / 技能按钮重复绑定修复 / 管护签名跳过 / 成就进度复用面板 / health 内容指纹。前端：背包筛选+显示排序、区域锁定提示、补给预警、顶栏速览（金币/击杀/击杀速率）、图鉴进度、词条图鉴、击杀预估（estPunches 与 dealDamage 同口径）、快捷键 1-5/F/B、战绩摘要复制、帮助弹窗、页签标题速览。SQL 仍是单一 gongpu-full.sql（§4.1 逐轮 DELETE+INSERT 幂等追加；新增守卫列 claim_streak/token_version）。前端升 **v=103**，单测 **373 全绿**；deploy.sh 验收口径同步（52/34/14/16/22/23）。详见 README「变更记录（2026-11-03 · 第六批一百轮增强）」。
- **第五批十轮增强（2026-11-03，本地内网自测）**：装备分解系统（Item.zone 打标按区域出材料、旧档兜底兽皮）+ 自动分解档位 autoSmeltKeep（dropIntercept 单点、分解优先于售卖）/ 怪物词条扩至 7 种（💰富饶金币 ×3 森林起、⚡疾影攻速 +50% 矿洞起，结算抽 traitGoldMul/traitHasteMul）/ 战绩统计（Stats.totalDamage/bestHit 终身口径）/ 成就 24→28 且可领奖（rewardOf 四档 100/500/2000/5000、ach-claim 一成就一领、金额 view 单源下发）/ 背包评分对比（bagScores/equippedScores 视图附件，equipScore 单一口径）/ 新被动巨人杀手（Boss 伤害 +20%，PASSIVE_SKILLS 镜像与 SQL、Parity 锁三同步）/ 委托 9→12。前端升 v=72，单测 **262 全绿**；内网实测分解/领奖/自动分解全链路通过。详见 README「变更记录（2026-11-03 · 第五批十轮增强）」。
- **SQL 整合 + 136 同步（2026-11-03）**：①拉入 136 环境提交（shaun88）：deploy.sh 迁移清单补全 v3.16~v3.24、DeploySocketConfig 改 @ConditionalOnProperty(gongpu.redis.socket)——**本机 start-gongpu.sh 已补 --gongpu.redis.socket 参数**（旧版是硬编码路径，下次本机重建不补会断 Redis）。②resources 下 22 个 SQL 文件整合为单一 **gongpu-full.sql**（§1 表结构/§2 守卫式加列与一次性回填/§3 基线种子/§4 内容迁移按序），全程幂等：临时库实测全新导入与现行库 cfg 内容逐行一致（除自增 id）、二次导入无错；deploy.sh 已改为单一入口并删除 migrate() 内联与 CONTENT_MIGRATIONS 清单。GunplayTest 改读整合文件。单测 242 全绿。
- **第四批十轮增强（2026-11-03，主题：药剂与装备种类增强；本地内网自测）**：药剂 18 → **25 种**（回复系特大血/大蓝/万灵双回、新维度生生/远眺/疾行、收益维度鸿运掉落 ×1.5、解毒剂兼清沉默）/ 装备模板 36 → **45 件**（沼泽 1→5、火山 3→6、雪原 6→8）/ 词条 14 → **16**（轻盈 EVA、活力 maxHP）/ 商店药剂分组（meta group 字段）/ DataParityTest 数据一致性锁 / 品药成就（成就 24 项，usedPotions 记种类）。前端升 v=67，单测 **242 全绿**；内网实测：7 个新迁移导库后 meta 药剂 25 种分组正确、新药喝下 buff 全生效、potion_5 即时点亮。详见 README「变更记录（2026-11-03 · 第四批十轮增强）」。迁移序号推进到 v3.24。
- **第三批十轮增强（2026-11-03，每轮独立提交 + 1 个实测修复；本地内网自测）**：统计改终身口径（Stats.lifetimeKills + migration-v3.17，当世值每世重置不可作累计）/ **图纸合成系统接通**（图纸掉落 Boss 30%/普通 4%、CraftService、⚒️锻造 tab；另有 item: 物品产物约定走药剂栏）/ 新词条🌵荆棘（雪原起解锁）/ 修改密码 / JWT 滑动续期（X-New-Token）/ 背包售价显示 / 图鉴总数口径对齐 / 死亡轮询降频 / QuestService 解析缓存+首测 / 成就扩充（22 项，crafts/enchants 终身计数）。前端升 v=65，单测 **223 全绿**；内网实测：迁移回填相等、合成装备与烹饪双产物落地且 first_craft 点亮、改密往返、自签短令牌续期链完整。详见 README「变更记录（2026-11-03 · 第三批十轮增强）」。
- **第二批十轮增强（2026-11-03，每轮独立提交；按玩家新流程只在本地内网自测，未部署公网）**：统计 SUM 化（遗留 #3 完全销账，game_save 四冗余列 + migration-v3.16 回填）/ Meta 快照缓存 / 装备锁定 / 背包整理 / 护盾跨世残留修复 / Health 探活短缓存 / 新词条🩸嗜血（沼泽起解锁）/ 异常处理补齐 400/404/405 / 榜单读缓存 / 图鉴成就（成就 20 项）。前端升 v=58，单测 **199 全绿**；内网实测：迁移回填与 JSON 聚合逐项相等、锁定全卖只剩锁定件、类型不符路径 400。详见 README「变更记录（2026-11-03 · 第二批十轮增强）」。
- **v3.15 增强轮（2026-11-03，已部署公网并实测）**：①新玩法「怪物词条」——野怪出生按区域深度掷 💢狂暴/🪓破甲/🔗缴械（平原 10% 起每深一层 +3%，池随深度解锁，Boss/木桩不出），`MobState.trait` 持久化、mobView 下发展示信息、前端名后缀+描述行渲染，公网实测刷出狂暴野兔且界面渲染确认。②销账两条技术遗留：BULLET_MP 排除强化乘区（遗留 #6）、榜单 submitBoard 改 Lua 原子提交+节流表清理（遗留 #5）。③性能：advance 内面板构建砍 3 处可证等价的重复（grow 复用主面板、autoPotion 三分支共用一份）。④前端标题/meta 补齐、版本升 v=53。⑤同日续作「分级售卖」（玩家需求）：手动一键卖扩为白/绿/蓝/紫四档按钮、挂机自动售卖扩到紫档（全折现不进包），前端升 v=54。⑥同日再续「十轮增强」（每轮独立提交，已部署公网实测）：nextFreeExtra 线性化 / 用户名走 JWT claim / 日聚合轻量采集 / SocialService 写路径收口 / 锁模板 withLock 收口 / 前端轮询失败退避 / 圆满金碑 / 管家悬浮面板 / 存档导出 / 自动进食，前端升 v=55。单测 **172 全绿**（V315EnhanceTest 5 项 + SellTierTest 4 项 + PerfectGraveTest 1 项 + AutoEatTest 2 项）；本机（Muse VM）测试依赖已补齐，`mvn -o test` 可直接离线跑通（seed-test.py 在 pugong-deploy）。详见 README「变更记录（2026-11-03）」各节。
- **（以下为 2026-11-02 晚状态）**

- **第二部署目标已上线**：`https://pugong.shaun88.cc.cd/gongpu/`（Muse VM，Cloudflare 隧道 → nginx :8088 → 本栈）。该 VM 的 JVM 出站 TCP 被平台拦截，MySQL/Redis 全走 Unix 套接字（MariaDB localSocket + 新增 `DeploySocketConfig`），构建走离线 Maven 仓；细节见 README「公网部署」节，下方 136 的路径/命令不适用于此目标。部署目录 `~/workspace/pugong-deploy/`，看门狗 supervise + cron 保活。
- **三处修复（已实测，详见 README 变更记录）**：①CORS 白名单缺公网域名致浏览器注册/登录 403，启动参数已补；②前端 `renderBuffs._sig` 悬空引用致新手天赋觉醒仪式永不触发，改挂 `applyView._buffSig`（浏览器实测抽卡全流程通过），前端版本升 v=52；③灌库 CLI 字符集错误致 cfg_* 文本双重编码乱码，45 列已反转还原，CLI 导入必须 `--default-character-set=utf8mb4`。
- **后端优化**：轮询帧落库节流 `SaveService.persistThrottled`（MySQL 写降至约 1/5，死亡强制立即写）；`run()` 内重复账号查询合并。

- **验证基线**：单测 **242 全绿**（mvn -o test，含第四批新增 PotionsExpandTest 3 / PotionsDimensionTest 5 / LuckPotionTest 2 / CureExpandTest 1 / AffixExpandTest 3 / DataParityTest 3 / PotionAchievementTest 2 等 19 项）+ 冒烟 **66 全绿**（136 已跑）；每次改完必须双绿。注：2026-11-03 起玩家改为本地改代码流程，公网部署维护已停，冒烟以本机内网栈为准。
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
| 服务反复重启 | ddl-auto=validate 与表不匹配 | `journalctl -u gongpu-server -n 50`；改实体要同步 gongpu-full.sql（§1 表结构 + §2 守卫加列） |
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
- 配置数据在 8 张 cfg_* 表；全量脚本 server/src/main/resources/gongpu-full.sql（基线种子在其 §3，幂等可重跑；原 seed-v3.sql 等 22 个 SQL 文件已整合删除）
- 改数值三选一：运维端页面改（推荐）/ 直接 UPDATE cfg_* 表后 POST /api/admin/config/reload / 重启服务
- 所有运维操作落 admin_audit_log 审计表

## 7. 玩法系统速查（持续更新至 2026-11-04 第十批）

| 系统 | 位置 | 要点 |
|---|---|---|
| 宝石三阶 | GameConst.buildGems + GameService.fuseGem/fuseAllGems | GEMS 18 种：三阶「曜」由 buildGems 按一阶 ×5.5 自动生成（_3 后缀）；fuseGem 对 _2 入参融出 _3（_3 拒绝再融），fuseAllGems 二阶级联；gemTier1Ids 排除 _2/_3（掉落池只一阶）；GEM_SELL_PRICE 三阶 = 一阶 ×9；equipScore 三阶 +500；Stats.gemTier3Fused |
| 归墟界（第十三区域） | GameConst.ZONES guixu | 门槛 maxHP 5e11/双防 20000/ATK 1e6；Boss 归墟之主 5e13 血/金 3 亿；ZONE_MAT=guixu_pearl（售 60000）；归墟战装 guixusuit 第 11 套；词条率 43%、专属池 28 条（鸿蒙池 + 雷霆）；nextZoneId(hongmeng)=guixu |
| 区域精通 | Stats.zoneKills + GameEngine.zoneMasteryMul + fullView zoneKills/masteryPct | 分区域终身击杀（onMobKilled 按 s.zone 累加，Boss 计入）；每满 500 杀 +2% 地利伤害、5000 杀封顶 +20%；dealDamage 与 expectedPerHit 双点乘入（不受沉默压制）；StateCodec 剔未知区域键/负数钳 0；成就口径 Achievements.maxZoneKills/zonesAtMastery |
| 怪物词条·金钟/腐蚀/雷霆 | GameConst.MOB_TRAITS golden_bell/corrode/thunder | 金钟：dealDamage 入口整帧归零一次（MobState.bellUsed，一次性增益消费之前判定故不被吞）；腐蚀：mobAttack defMul 单点（1−3%×层，上限 10 层，MobState.corrodeStacks，伤害落地后叠层）；雷霆：mobAttack 额外真伤 = 怪 PATK×0.3（护盾先吸，归墟专属池）；计数 bellKills/corrodeKills/thunderKills 在 onMobKilled |
| 天罚/归墟体/潮汐之体/星辰之心 | GameService.castSkill + PanelCalculator | 天罚：主动、buff heaven_wrath、dealDamage 下一击 ×6；归墟体：被动暴击 +0.05 进 CRIT 加链；潮汐之体：被动闪避 +0.06 进 EVA 加链；星辰之心：天赋、PanelCalculator 宝石 gemMul 再 ×1.5（与 gem_affinity ×1.25 叠乘） |
| 宠物进化 | PlayerState.petEvolved + GameService.evolvePet + POST /{slot}/evolve-pet | 满级 50 可进化：属性 ×2（PanelCalculator petBonus 乘区，伙伴来源不压制）；成本 200,000 金 + chaos_core×3；petView 下发 evolved 名单；StateCodec 兜底剔除未领养/图鉴外；Stats.petEvolves；宠物共 9 只（+麒麟 pet_qilin） |
| 鸿蒙界（第十二区域） | GameConst.ZONES hongmeng | 门槛 maxHP 5e10/双防 8000/ATK 400000；Boss 鸿蒙道祖 5000 亿血/金 1 亿；ZONE_MAT=hongmeng_qi（售 20000）；鸿蒙战装 hongmengsuit 第 10 套；词条率 40%、专属池 25 条（混沌池 + 太虚）；nextZoneId(chaos)=hongmeng |
| 装备觉醒 | Item.awaken + GameService.awaken + POST /{slot}/awaken | 0~3 级，满 5 星前置；底子 +5%/级（PanelCalculator 第四乘区，与强化/淬炼/升星独立）；成本 100000×(级+1) 金 + chaos_core×(级+1)；sellPrice ×(1+0.15级)、equipScore ×(1+0.05级)、devourInto 取 max；StateCodec 钳制 [0,3]；Stats.awakens |
| 怪物词条·不朽/吞噬/太虚 | GameConst.MOB_TRAITS immortal/devour/taixu | 不朽：dealDamage 击杀分支前拦截、首次致死保留 30% 血（MobState.immortalUsed、溢出同帧可真杀）；吞噬：mobAttack 额外真伤 = 玩家上限 ×2%（护盾先吸）；太虚：dealDamage+expectedPerHit 双点 ×0.75（鸿蒙专属）；计数 immortalKills/devourKills/taixuKills 在 onMobKilled |
| 万剑归宗/鸿蒙体/鸿蒙之心 | GameService.castSkill + PanelCalculator + GameConst.petBondMul | 万剑归宗：主动、buff sword_rain 30s、dealDamage 下一击 ×5；鸿蒙体：被动 ASPD 平直 +0.10 进乘区前；鸿蒙之心：天赋、petBondMul 三参重载再 ×1.5（与混沌之心叠乘 ×2.25，PanelCalculator 与 petView 双调用点） |
| 宠物伙伴 | GameConst.PETS + GameService.petView | 9 只（+熊猫/雪貂/麒麟）；adopt-pet/switch-pet/feed-pet/feed-pet-batch/feed-pet-max/rename-pet/evolve-pet 七端点；击杀 +1 经验（天赋兽语者 ×2）、满级 50、petExpNeed=20+(lv−1)×10；petBonus 并入 flat 区（不压制），被动宠物亲和 PET_BOND_MUL=1.3 乘区受沉默压制；petView 挂 fullView 本体（含 levels 全表供称号/宠物卡） |
| 宠物羁绊/零食/分享 | GameConst.petBondMul + PanelCalculator + GameService.gainPetExp | 羁绊：总等级 30/100/250 → 出战加成 ×1.25/1.6/2.2（天赋混沌之心 ×1.5），petView 下发 totalLevels/bondMul/bondNext；宠物零食 pet_treat 走 usePotion 特殊分支 +60 经验（无出战先校验不消耗，Stats.petTreats）；非出战宠物分享 25% 经验（gainPetExp 单点、兽语者翻倍在分享前结算） |
| 混沌界（第十一区域） | GameConst.ZONES chaos | 门槛 maxHP 2e9/双防 3000/ATK 150000；Boss 混沌魔君 300 亿血/金 2000 万；ZONE_MAT=chaos_core（售 6000）；混沌战装 chaossuit 第 9 套；词条率 37%、专属池 22 条（含狂乱）；nextZoneId(void)=chaos |
| 装备升星 | Item.star + GameService.starUp + POST /{slot}/star-up | 0~5 星，底子 +3%/星（PanelCalculator 第三乘区，与强化/淬炼独立）；成本 5000×(星+1) 金 + star_shard×(星+1)；sellPrice ×(1+0.1星)、equipScore ×(1+0.03星)、devourInto 取 max；StateCodec 钳制 [0,5]；Stats.starsDone |
| 怪物词条·镜像/虚弱/狂乱 | GameConst.MOB_TRAITS mirror/weaken/chaos_frenzy | 镜像：dealDamage 帧尾与荆棘同段、反弹 min(总伤害,开帧血量)×10%、护盾先吸；虚弱：dealDamage+expectedPerHit 双点 ×0.85（缴械同乘区）；狂乱：traitHasteMul ×1.4 + mobAttack 双攻 ×1.2（混沌专属）；计数 mirrorKills/weakenKills/frenzyKills 在 onMobKilled |
| 时停/时之体/混沌亲和 | GameService.castSkill + GameEngine/PanelCalculator | 时停：buff time_stop 10s、mobAttack 攻速 ×0.5（迟缓咒后乘算）；时之体：ASPD 平直 +0.08 进乘区前（chronoAspd 变量）；混沌亲和：混沌界内专伤 ×1.25（dealDamage 与 expectedPerHit 双点、查 s.zone 与沉默） |
| 月常任务 | MonthlyQuests + POST /{slot}/monthly-claim | YearMonth 月界键、5 模板抽 3（hunt/boss/craft/punch/damage）、复用 DailyQuests.counterOf 终身基线、全清 +300,000 金 + 随机宝石 ×3；ensure 必须在 runLocked 落库前；monthlyTasks 挂 fullView 本体；Stats.monthlyClaims |
| 怪物词条·噬能/裂空/燃血 | GameConst.MOB_TRAITS mana_drain/void_rend/blood_fury | 噬能：mobAttack 按实际伤害 10% 抽蓝（深区起）；裂空：effEva 单点闪避 ×0.5（虚空专属）；燃血：Boss 二阶段乘区后按已损生命放大双攻（最高 +60%，深区起）；三者击杀计数 drainKills/rendKills/burnKills 在 onMobKilled |
| 批量经济端点 | GameService/CraftService | sell-all-materials / fuse-all-gems / unsocket-all（每件 200 金，不足跳过）/ sell-extra-blueprints（每种留 1）/ mat-exchange-all / feed-pet-batch（固定 ×10）；口径=逐个操作循环，余额不足部分跳过 |
| 水墨主题+战力 | web v2.css body.theme-ink + app.js powerChip | 四主题轮换 dark/light/star/ink（localStorage gongpu_theme）；水墨=纸面卡 + 远山底 + 朱砂点缀（原创 CSS，参照《我的文字修仙全靠刷》设计语言、CC BY-NC 仅借鉴）；战力 = DPS×(1+maxHP/(2×(PATK+MATK)))×(1+(PDEF+MDEF)/2000) |
| 药剂（18 种） | cfg_item + GameConst.POTIONS 兜底 | 修行药剂 growth_pot 成长×2（GameEngine.grow 乘区）、铁壁药剂 iron_pot 双防×1.5（PanelCalculator 乘区）；v3.14 新增 10 种：锐眼/残暴/嗜血/狂暴/磐石/疾风/明察/幻影/凝神/护盾（乘区/加区全在 PanelCalculator，受沉默压制）；护盾为新机制（PlayerState.shield，mobAttack/dotTick 先扣盾，buff 到期清盾，panel.shield 下发）；**灌库后必须 admin/config/reload 热刷新** |
| 每日签到 | POST /api/game/{slot}/daily | 每账号每天一次（account_user.last_claim_day）；金币 200+gen×100、血药×3 蓝药×2、gen≥1 送修行、gen≥3 送铁壁 |
| 洗炼+词条锁定 | POST /api/game/{slot}/reroll {index,lockIndex} | EquipFactory.rerollAffixes(it,lockIdx)；锁定词条保留且不放回池；锁定费用 ×3（rerollCost×3） |
| 分级一键卖 | POST /api/game/{slot}/autosell {keep} | keep 1~4 合法（4=紫及以下即全卖，越界 400）；卖出 quality<keep 的全部背包装备（锁定件跳过）；前端背包区四档按钮组（卖白装/绿及以下/蓝及以下/紫及以下） |
| 装备锁定 | POST /api/game/{slot}/lock {index} | Item.locked：单卖/丢弃拒绝、一键卖与管护卖白装跳过、无相位管护不吞噬不穿入；前端背包 🔒 按钮+名称标记；ItemLockTest 锁 |
| 背包整理 | POST /api/game/{slot}/sortbag | 按 equipScore 降序稳定排序；前端「↕️ 整理背包」按钮 |
| 怪物词条·嗜血 | GameConst.MOB_TRAITS leech | 🩸 命中按玩家实际掉血（护盾吸收后）30% 回怪血、封顶上限；沼泽起入池（矿洞仍三词条）；TraitLeechTest 锁 |
| 运营统计 | StatDailyAggregator | game_save 冗余列（punches/kills/deaths/rebirths）一条 SUM 聚合，persist/rollback 同步维护；gongpu-full.sql §1 自带冗余列、§2 守卫加列+一次性回填（存终身口径） |
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
| 转生/重开重置 | SaveService.resetForNewLife 唯一实现 | rebirth 复用它(勿再手抄副本)；清 药价阶梯 potionBought/尸检 recentKills/护盾 shield（buff 附属量）等全部当世派生数据，传承（赋能/图鉴/成就/已完成委托/无相位栏）保留；ResetAuditTest 锁清单；「每日」时区统一 GameConst.DAILY_ZONE(Asia/Shanghai) |
| 世界公告 | GET /api/social/bulletin（公开） | 史诗(quality≥3)掉落播报；Redis LIST gongpu:bulletin 20 条/1h 过期；玩家名 bulletinName（transient，三个结算入口填充） |
| Boss 首杀 | GameEngine Boss 击杀分支 | PlayerState.bossKills 持久化防重复；金币 1000×(区域序+1)；AdvanceResult.bossFirstKill 下发 |
| 称号+图鉴+成就 | 前端本地推导/渲染 + fullView 下发 | computeTitles 9 称号；codex 集合记录击杀（转生不清空）；**成就 24 项服务端权威落库**（Achievements.java persist 前评估，含 codex_10/codex_all 图鉴成就、first_craft/first_enchant 里程碑与 potion_5/potion_15 品药成就（usedPotions 记种类）、总数口径 GameConst.codexTotal，state_json.achievements 持久化，前端只渲染+diff toast，前后端 id 表有单测锁死） |
| 图纸合成 | GET /api/game/{slot}/crafts + POST /{slot}/craft | CraftService：图纸（材料键 bp_<配方>，击杀 Boss 30%/普通怪 4% 掉落）+ 材料 + 金币 → 装备（基础配方品质 1、图纸配方品质 2、25% 升一品封顶 3）；product_code 为 `item:<code>` 时产物进药剂栏（烹饪类）；全校验后才扣料；前端 ⚒️锻造 tab |
| 觉醒天赋 | POST /api/game/{slot}/talent | **8 种天赋**：三大顶级机缘（♾️被动永续/🧳无限装备栏/💠无限属性点）各 30%，其余平分 10%（可自选，顶级只能靠命）；抽中顶级=万中无一金雨仪式；**无限装备栏 v3.12 起觉醒即无限**（不再每天 +1；openExtraSlots=已用 extra_N 数+1，加穿后自动再开一格；穿最强把背包剩余全部穿入无相位栏；**同名装备自动吞噬合并**（equipExtra/穿最强入口：base 求和、同名同键词条 val 叠加、不同词条追加、强化/品质取高；autoEquip 入口先 devourLegacyDuplicates 清历史膨胀——性能卡顿源即已穿件数线性膨胀；原生 ring1/ring2 双戒不动）；旧字段 extraSlots/lastSlotDay 仅兼容旧档，grantDailySlot 已删除） |
| 安全限速 | AuthRateLimiter | 登录失败同 IP 10 次/10 分钟锁；注册同 IP 5 次/小时；回环豁免；Redis 挂了放行 |
| 可观测性 | TimingInterceptor | >gongpu.slow-ms(默认500)的 /api/** 打 WARN 慢日志；启动横幅带版本/profile；500 日志带 METHOD+URI |
| 迁移审计 | 两轮全站对照(设计 §0-16+legacy 六模块) | v3.9 全量迁移完成,剩余项见 README「迁移状态」;autoRebirth/autoSellKeep/autoRebirth 开关在挂机管家 |
| 新手保底 | GameEngine.grantNewbieDrops | 每世前5杀必掉保底武器、≥10杀必掉回春戒(每秒回1%生命上限,type=pct_hp);**空戒指位自动穿上**(两戒占位才进背包;背包满且无空位等待不吞);字段 newbieKills/newbieWeaponsGiven/ringGranted;面板回血行=固定回血+regenHpPerSec(fullView 下发) |
| 每日连签（第六批） | POST /{slot}/daily | account_user.claim_streak 守卫列；原子领取的同一 UPDATE 用 CASE 维护连签（昨日签过 +1 否则 1）；奖励加成 +50/天封顶 350、每 7 天周奖大血药 ×2；并发败者 revertDailyReward 同步扣回加成；AccountService.nextStreak/streakBonusGold 静态口径，DailyStreakTest 锁 |
| 战斗日志（第六批） | PlayerState.events + fullView.events | Boss 击杀/图纸/史诗三类大事环形 20 条（GameEngine.pushEvent 单点），前端战斗区倒序 6 条；旧档 decode 补空表 |
| 自动推进/Boss 连战（第六批） | autoconfig autoAdvance/autoBoss | Boss 击杀漏斗 advanceAfterKill：连战（留区 spawnBoss）优先于推进（下一区门槛与 gotoZone 同口径 zoneReqMet，不达标回村）；setAutoLines 已 15 参，前端三处调用（管家 saveAuto/配置区/策略应用）必须同步加参 |
| 一键分解/材料兑换（第六批） | POST /{slot}/dismantle-all {keep}、/{slot}/mat-exchange {from,to} | 分解与一键卖同档位语义、锁定跳过、产出按 Item.zone 汇总；兑换仅六种区域材料 3:1（MAT_EXCHANGE_COST），CraftService.exchange 单点校验 |
| 按量补给/成就全领（第六批） | POST /{slot}/buyfill {id,target}、/{slot}/ach-claim-all | buyfill 循环单瓶 buyPotion 保持动态定价口径、金币不足静默停；claim-all 遍历 Achievements.ALL 只领已解锁未领，返回总额 |
| 令牌版本（第六批） | JWT claim v + account_user.token_version | 改密自增版本，AuthInterceptor 逐请求比对 AccountService.currentTokenVersion（30s 内存缓存 + 改密主动失效）；滑动续期继承版本，JwtRenewVersionTest 锁；老无 v 令牌按 0 兼容 |
| 登录防爆破（第六批） | AccountService.login | 同用户名连错 5 次锁 5 分钟回 429（与 AuthRateLimiter 的 IP 维度互补）；成功清零；LoginThrottleTest 锁 |
| 存档自愈（第六批） | SaveService.decodeOrRecover | 主档 JSON 损坏回退快照解码，双坏报 500 SAVE_CORRUPT；rollback 快照坏按无快照重开；SaveRecoverTest 锁 |
| 击杀预估（第六批） | fullView mob.estPunches/estSeconds | GameEngine.expectedPerHit 与 dealDamage 前段同口径（暴击期望/防御/词条/巨人乘区，不含蓄力），改伤害公式必须同步 |
| 装备套装（第七批） | Sets.java + fullView.sets / meta.sets | 六套区域 4 件套按不同成员 code 计数（同名重复不刷数），2/4 件两档叠加；PanelCalculator 装备循环后并入、沉默不压制（装备来源）；前端已穿/背包 🧩 徽章 + 套装图鉴弹窗；SetParityTest 锁成员在 SQL 且 min_zone 对应 |
| 装备淬炼（第七批） | POST /{slot}/temper {index} | Item.temper 0–5，底子乘区 ×(1+4%/级) 与强化分层（BULLET_MP 排除），equipScore 同乘；成本 = Item.zone 区域材料 2×(级+1) + 金币 300×(级+1)×(品质+1)，锁定件可淬炼 |
| 洗炼保底/双锁（第七批） | PlayerState.rerollPity + POST /{slot}/reroll | 连 9 次未出史诗第 10 次强制（rerollAffixes forceEpic，锁定位不换）；双锁需 ≥3 条词条、成本 ×1/×3/×5；响应带 pity/pityTriggered |
| 深渊裂隙（第七批） | zone abyss（ZONES 末位） | 门槛 maxHP 200 万/双防 150/ATK 4000；4 怪 + Boss 无光君王（30M）；材料 abyss_crystal；深度 7 词条率 28%、全 14 词条池；autoAdvance 链尾自动到达（nextZoneId 按序） |
| 宝石镶嵌（第八批） | GameConst.GEMS + POST /{slot}/socket·unsocket·fuse-gem | 12 种（一阶 6：物攻/魔攻/生命/暴击/攻速/双防 flat；二阶 = 一阶 ×2.5、code 后缀 _2）；Item.gems 每件 3 孔，flat 并入装备区（沉默不压制，天赋宝石亲和再 ×1.25）；掉落：Boss 必掉、词条怪 10%（宝石嗅觉被动 ×1.5）；融合 3 颗同种一阶 → 二阶；出售 500–800/二阶 ×3、直购 = 售价 ×4（仅一阶） |
| 周常任务（第八批） | WeeklyQuests + POST /{slot}/weekly-claim | ISO 周键（2026-W45 式）签发，5 模板（猎杀/Boss/锻造/出拳/总伤害）抽 3、基线口径复用 DailyQuests.counterOf；全清 +50,000 金 + 随机宝石 ×2；ensure 在 runLocked 落库前（与每日同纪律），fullView.weeklyTasks 下发 |
| 星界（第八批） | zone star（ZONES 末位，序 8） | 门槛 maxHP 2000 万/双防 400/ATK 12000；4 怪 + Boss 星核主宰（3 亿）；材料 star_shard 售 650；词条率 31%、专属池 17 条（含星蚀）；装备 6+2 件 tier 4、星辰套装（第 7 套）、委托/配方各 +2、图鉴总数 28 |
| 怪物词条·星蚀（第八批） | GameConst.MOB_TRAITS star_eclipse + GameEngine.effCritD | 🌑 暴伤超额部分（CRITD−1）减半，dealDamage 与 expectedPerHit 同口径；仅星界池（traitPool(8)），其余池不含；击杀计数 Stats.eclipseKills |
| 怪物词条·再生/坚韧（第八批） | GameEngine advance 主流程 / effCrit | 🌿 再生：怪每秒回上限 3%（结算点在怪物反击之前，不放 mobAttack）；🪨 坚韧：暴击率减半（effCrit 单点），仅最深池（序 ≥5 的 else 池与星界池） |
| 商店三件（第八批） | POST /{slot}/buy-material·buy-gem + sell-material 扩 | 材料买入 = 售价 ×3（仅区域材料、单次 ≤999）；宝石直购 = 售价 ×4（仅一阶）；图纸出售 = 300 + 产物区域深度 ×250（查不到配方不予定价）；全部买贵卖贱防套利，EconomyLinkageTest 锁 |
| 一键穿套装（第八批） | POST /{slot}/equip-set {id} | 按成员 code 从背包挑评分最高件逐件走 equip 换装，已穿跳过、缺件回执列明；前端套装图鉴弹窗每套一钮 |
| 成就里程碑（第八批） | GameService.settleAchMilestone | 累计已领成就达 15/30/45/60 项一次性奖金 1万/3万/8万/20万，Stats.achMilestonePaid 掩码防重发；单领与一键全领都结算，回执 milestoneBonus |
| health 观测（第八批） | GET /api/health | 在 perf/content 之外加 cache（StateCache 命中/未命中/写入/命中率）、frames（advance 帧数/平均/慢帧 ≥50ms/最大）、save（存档 JSON 字节最近/平均/最大 + 带备份环存档数，计数与探活共用同一连接） |
| 写端点限流（第八批） | GameController.checkWriteRate | 账号级滑动窗口 10 秒 60 次写动作，超限 429；纯轮询（action==null 的 GET /state）不计；检查在 saves.load 之前 |
| 每日任务（第七批） | DailyQuests + POST /{slot}/daily-claim | 7 模板每日抽 3、随机档位；进度 = 终身计数 − 签发基线（DailyTask.baseline 字符串存大数），跨世保留；三任务全领全清奖 +5000（dailyBonusGiven 日切重置） |
| 每日黑市（第七批） | BlackMarket + POST /{slot}/market-buy | 16 种增益药池每日抽 3、折扣 30–50%、单品限购 5（marketBought 日切清零）；种子 = 日期 hash×31 + born，同日同号同单；购买不写 potionBought 不影响商店阶梯价 |
| 材料出售/补给包（第七批） | POST /{slot}/sell-material、/{slot}/buyfill-kit | MAT_SELL_PRICE 仅七种区域材料（图纸/卷轴不可卖），count≤0 全卖、超量封顶；补给包 = hp_s 10/mp 5/cure 3/food 3 走 buyFill 同口径、单件失败不拖垮整包 |
| Boss 二阶段/弱点打击（第七批） | MobState.phase2 / PlayerState.punchAcc | 二阶段血线 50%：攻速 ×1.3 攻击 ×1.2 + events 播报，Stats.bossPhaseKills；弱点打击每第 10 拳按 (CRITD/期望倍率) 放大差额（CRITD≤期望不计），Stats.weakCrits；estPunches 口径不含弱点差额 |
| 荆棘反扎口径（第七批修复） | GameEngine.dealDamage 帧首快照 | 反扎基数 = min(整帧伤害, 开帧目标剩余血)，溢出不再反杀玩家；deathCause 追加词条标注（如 ·🌵荆棘）；TraitThornsTest 锁 |
| 怪物词条诅咒/寒霜（第七批） | MOB_TRAITS curse/frost | 诅咒：交战回血减半（GameEngine.healMul 单点覆盖吸血/回复 tick/自动喝药/手动喝药），沼泽起；寒霜：命中 30% 挂 frost 5s 攻速 ×0.85（与时滞叠乘），雪原起；净化与解毒剂可清寒霜 |
| 存档备份环（第七批） | game_save.backup_ring + SaveService.rotateBackupRing | persist 每 ≥10 分钟轮转一份主档 JSON 进环（[{t,j}] 留 3 份）；decodeOrRecover 自愈链：主档 → 备份环（新→旧）→ 存档点快照 → SAVE_CORRUPT；BackupRingTest 锁 |
| 经济审计（第七批） | GameController.run 审计标签重载 | 18 个经济端点传标签，runLocked 在 advance 后取金币基线、落库后按变动记 economy-audit 日志（uid/slot/action/delta/gold 全精度）；无变动与挂机帧不记 |
| 过期帧守卫（第七批） | fullView.serverTime + app.js applyView | 服务端单时钟戳，客户端丢弃 serverTime 小于已应用帧的乱序旧帧；与 lastWriteAt 写后守卫互补（慢网/多标签页防 UI 回退） |
| 慢请求指标（第七批） | GET /api/health 的 perf 段 | TimingInterceptor 静态原子计数（requests/slow/slowMs/maxMs，record 单点判定），阈值默认 500ms |
| 怪物词条·时滞（第六批） | MOB_TRAITS slow（沼泽起） | 🐌 命中概率上 slow 4s：面板 ASPD ×0.8（PanelCalculator slowMul，减益不查沉默）；净化技能/解毒可清；SlowTraitTest 锁 |
| 内容计数（第六批终版） | /api/health content 指纹 | 药剂 34/词条 22/装备 52/怪物词条 12/技能 23/委托 14/配方 16/成就 34；计数锁分布于 PotionsV314/Affix*/DataParity*/EconomySanity/Achievements* 测试与 deploy.sh 验收行，改内容必须全链同步 |
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
5. **实体改动三同步**：实体类 + gongpu-full.sql（§1 表结构 + §2 守卫加列）；deploy.sh 已改单一全量脚本入口，无清单要维护。
6. **每轮修改后必跑**：`mvn -o test`（165 项）+ `python -I server/deploy/smoke_test.py`（66 项），双绿才算完成。
7. **kills/gold 等基线类字段**：死亡重开/转生清零时，引用它的派生数据（如委托基线）必须同步清理。
8. **前端渲染加签名守卫**：applyView 每秒全量跑，任何新 renderXXX 必须挂 `_sig` 内容签名（范式见 renderPotions/renderBag），否则每秒销毁重建 DOM=掉帧+点击丢失。
9. **浮层一律不透明底**：`--panel/--panel-2` 是近乎全透的白色叠层(靠暗底撑住)，浮层底下有内容会透——用 `--bg1/--bg2` 渐变(statCard/item-pop/talent-pop 三处先例)；新弹窗优先复用 pickModal/death-mask 体系。
10. **改前端必升 `?v=N`**；改数值语义先想"感知层 vs 数值层"——显示口径变化只动 affixText/fmt 等展示函数，结算不动(率型词条先例)。
11. **多智能体协作**：小批即提交、`git add <具体文件>` 绝不 add -A；开工前 git log/status 探测并行会话改动；发现别人半成品先 adopt+审计而非覆盖。
12. **前端写请求必须走 `call()`**：它内置等轮询让锁(≤1.5s)+双击合并(<400ms)+写串行队列(_writeChain)+409 静默重试——绕过 call 直接打 API 会吃 409"操作太快"且绕过期帧丢弃。
13. **改动"按天/按次数解锁"类玩法前先想无限模型**：无限装备栏先例=openExtraSlots 返回"已用数+1",永远恰有一个空位可操作,UI 不渲染无穷格;旧档积累字段保留兼容但不参与计算。
7. **kills/gold 等基线类字段**：死亡重开/转生清零时，引用它的派生数据（如委托基线）必须同步清理。
