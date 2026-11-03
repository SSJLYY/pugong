#!/usr/bin/env bash
# ============================================================
# 《普攻永久增加生命上限》v2.0 部署脚本（在 136 服务器上执行）
# 用法：bash deploy.sh [--skip-build]
# 流程：构建 → jar 就位 → 部署前 DB 备份 → DB 迁移（幂等）→ systemd → 健康检查
# ============================================================
set -euo pipefail

APP_HOME=/data/gongpu
SRC_DIR=$APP_HOME/app
JAR=$APP_HOME/dist/gongpu-server.jar
SERVICE=gongpu-server
JAVA_HOME_21=/usr/lib/jvm/java-21-openjdk

log() { printf '\033[32m[deploy]\033[0m %s\n' "$*"; }
err() { printf '\033[31m[deploy]\033[0m %s\n' "$*" >&2; }

mkdir -p "$APP_HOME"/{app,dist,logs}

# ---------- 1. 构建 ----------
if [[ "${1:-}" != "--skip-build" ]]; then
  log "Maven 构建中（离线优先，缺失再联网）..."
  cd "$SRC_DIR"
  export JAVA_HOME=$JAVA_HOME_21
  export PATH=$JAVA_HOME/bin:$PATH
  mvn -q -B -DskipTests -o package 2>/tmp/mvn_offline.log || {
    log "离线依赖不全，改走在线构建"
    mvn -q -B -DskipTests package
  }
  ls -lh target/gongpu-server.jar
  log "构建完成"
fi

# jar 就位（--skip-build 时也执行，保证 dist 与 target 一致）
if [[ ! -f "$SRC_DIR/target/gongpu-server.jar" ]]; then
  err "找不到 $SRC_DIR/target/gongpu-server.jar，请先构建"
  exit 1
fi
# 备份旧 jar（失败可回退：mv dist/gongpu-server.jar.bak dist/gongpu-server.jar）
[[ -f "$JAR" ]] && cp -f "$JAR" "$JAR.bak"
cp -f "$SRC_DIR/target/gongpu-server.jar" "$JAR"
log "已部署 jar：$JAR"

# ---------- 1.5 部署前 DB 备份（jar 已就位，赶在 DB 迁移/重启之前留一份可回滚数据点）----------
# 失败仅告警不阻断：备份脚本自身有 gzip 完整性校验，跑挂多半是磁盘满/权限问题，
# 此时部署照常推进，由 stderr 提醒人工补备份（bash $SRC_DIR/deploy/backup-db.sh）。
if [[ -f "$SRC_DIR/deploy/backup-db.sh" ]]; then
  if bash "$SRC_DIR/deploy/backup-db.sh"; then
    log "部署前备份完成（/data/gongpu/backups）"
  else
    err "部署前备份失败——继续部署，但请人工补一份备份再观察"
  fi
fi

# ---------- 2. 环境变量 ----------
if [[ ! -f /etc/gongpu/app.env ]]; then
  err "/etc/gongpu/app.env 不存在，请先创建 DB_PASSWORD"
  exit 1
fi
chmod 600 /etc/gongpu/app.env
# JWT 密钥（首次生成后固定，重启不掉登录）
if ! grep -q '^JWT_SECRET=' /etc/gongpu/app.env; then
  echo "JWT_SECRET=$(openssl rand -hex 32)" >> /etc/gongpu/app.env
fi
chmod 600 /etc/gongpu/app.env

# ---------- 3. DB 迁移（幂等：缺列才加）----------
# schema.sql 全部为 CREATE TABLE IF NOT EXISTS，可安全重复执行（新装/升级统一入口）
mysql --default-character-set=utf8mb4 gongpu < "$SRC_DIR/src/main/resources/schema.sql" 2>/dev/null || \
  mysql --default-character-set=utf8mb4 < "$SRC_DIR/src/main/resources/schema.sql" 2>/dev/null || true
# seed-v3.sql 幂等（先 DELETE 再 INSERT）：新装 DB 的 cfg_* 基线数据（装备/词条/药剂/技能/状态/爆率）
if [[ -f "$SRC_DIR/src/main/resources/seed-v3.sql" ]]; then
  mysql --default-character-set=utf8mb4 gongpu < "$SRC_DIR/src/main/resources/seed-v3.sql" 2>/dev/null \
    && log "cfg 基线种子(seed-v3)完成" || err "seed-v3.sql 执行失败(检查 mysql 可用性)"
fi
migrate() {
  local tbl=$1 col=$2 ddl=$3
  mysql --default-character-set=utf8mb4 -e "SELECT 1 FROM information_schema.columns WHERE table_schema='gongpu' AND table_name='$tbl' AND column_name='$col'" 2>/dev/null | grep -q 1 || {
    log "迁移：$tbl + $col"
    mysql --default-character-set=utf8mb4 gongpu -e "ALTER TABLE $tbl ADD COLUMN $col $ddl"
  }
}
migrate game_save snapshot_json "LONGTEXT NULL COMMENT '存档点快照（死亡回档读它）' AFTER state_json"
migrate game_save saved_at "DATETIME(6) NULL COMMENT '存档点时间' AFTER snapshot_json"
migrate account_user incense "INT NOT NULL DEFAULT 0 COMMENT '香火（复活货币）' AFTER last_login_at"
migrate account_user pvp_wins "INT NOT NULL DEFAULT 0 AFTER incense"
migrate account_user pvp_losses "INT NOT NULL DEFAULT 0 AFTER pvp_wins"
migrate game_grave tributes "INT NOT NULL DEFAULT 0 COMMENT '被祭奠次数' AFTER epitaph"
migrate account_user last_claim_day "DATE NULL COMMENT '每日签到最近领取日' AFTER pvp_losses"
# v3.x 内容迁移(全部幂等,可重复执行,按序):
# v2.1/v2.2 的加列已由上面 migrate() 内联覆盖——那两个文件是裸 ALTER,重复跑会报错,勿挂。
# 缺这批文件时新装/重建 DB 会缺 枪械模板/修行铁壁药剂/腰带/委托/合成/词条flat(v3.9),玩法整体消失。
CONTENT_MIGRATIONS=(
  migration-v3.1-content.sql
  migration-v3.2-guns.sql
  migration-v3.3-belt.sql
  migration-v3.3-cooking.sql
  migration-v3.3-crafts.sql
  migration-v3.3-quests.sql
  migration-v3.9-affix-flat.sql
  migration-v3.13-affix-mp.sql
  migration-v3.14-potions.sql
  migration-v3.16-stats-cols.sql
  migration-v3.17-lifetime-stats.sql
  migration-v3.18-potions2.sql
  migration-v3.19-potions3.sql
  migration-v3.20-potions4.sql
  migration-v3.21-cure.sql
  migration-v3.22-affixes.sql
  migration-v3.23-equipment2.sql
  migration-v3.24-equipment3.sql
)
for mf in "${CONTENT_MIGRATIONS[@]}"; do
  if [[ -f "$SRC_DIR/src/main/resources/$mf" ]]; then
    mysql --default-character-set=utf8mb4 gongpu < "$SRC_DIR/src/main/resources/$mf" 2>/dev/null \
      && log "迁移 $mf 完成" || err "迁移 $mf 执行失败(检查 mysql 可用性)"
  else
    err "缺迁移文件 $mf——检查 server/src 是否完整 scp 上传"
  fi
done
log "DB 迁移核对完成（验收口径:cfg_equipment 36/cfg_item 8/cfg_quest 9/cfg_craft 14/cfg_affix 13/cfg_skill 14）"

# ---------- 4. systemd ----------
cat > /etc/systemd/system/${SERVICE}.service <<UNIT
[Unit]
Description=Gongpu Game Server
After=network.target mysqld.service redis.service
Wants=mysqld.service redis.service

[Service]
Type=simple
User=root
WorkingDirectory=${APP_HOME}
EnvironmentFile=/etc/gongpu/app.env
Environment=JAVA_HOME=${JAVA_HOME_21}
ExecStart=${JAVA_HOME_21}/bin/java -Xms256m -Xmx768m -XX:+UseG1GC \\
  -XX:MaxGCPauseMillis=100 -Dfile.encoding=UTF-8 \\
  -jar ${JAR}
Restart=always
RestartSec=5
StartLimitIntervalSec=60
StartLimitBurst=5
StandardOutput=append:${APP_HOME}/logs/stdout.log
StandardError=append:${APP_HOME}/logs/stderr.log

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable "$SERVICE" >/dev/null 2>&1 || true
systemctl restart "$SERVICE"
log "服务已重启，等待健康检查..."

# ---------- 5. 健康检查（MetaController /api/health，见 application.yml:8081）----------
for i in $(seq 1 40); do
  sleep 2
  if curl -fsS -m 3 http://127.0.0.1:8081/api/health >/tmp/health.json 2>/dev/null; then
    log "健康检查通过: $(cat /tmp/health.json)"
    # ---------- 6. 全链路冒烟（直连后端 + nginx 反代两层;失败不回滚只报警,毕竟健康已过）----------
    if command -v python3 >/dev/null 2>&1 && [[ -f "$SRC_DIR/deploy/smoke_test.py" ]]; then
      # 运维端冒烟节需要 ADMIN_PASSWORD(未注入则脚本自动跳过该节)
      [[ -f /etc/gongpu/app.env ]] && source /etc/gongpu/app.env && export SMOKE_ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"
      if python3 "$SRC_DIR/deploy/smoke_test.py" http://127.0.0.1:8081/api; then
        log "后端冒烟全绿"
        # 反代层独立冒烟:历史上踩过 nginx 丢 /gongpu 前缀/双重 /api/api 坑,直连测不到
        if curl -fsS -m 3 http://127.0.0.1:8088/gongpu/api/health >/dev/null 2>&1; then
          if python3 "$SRC_DIR/deploy/smoke_test.py" http://127.0.0.1:8088/gongpu/api; then
            log "反代冒烟全绿,部署完成"
          else
            err "nginx 反代层冒烟有失败(后端正常)——查 /etc/nginx/conf.d/games.conf 与前缀拼接"
          fi
        else
          log "（8088 反代不可达,跳过反代冒烟——nginx 未起或配置变更）"
        fi
      else
        err "冒烟测试有失败项——服务能跑但功能有损,排查: journalctl -u $SERVICE -n 50"
      fi
    else
      log "（未找到 smoke_test.py 或 python3,跳过冒烟）"
    fi
    exit 0
  fi
done

err "健康检查失败，最近日志："
tail -40 "$APP_HOME/logs/stderr.log" 2>/dev/null || true
tail -40 "$APP_HOME/logs/gongpu-server.log" 2>/dev/null || true
err "回滚方法：cp -f $JAR.bak $JAR && systemctl restart $SERVICE"
exit 1
