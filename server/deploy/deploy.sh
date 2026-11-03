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

# ---------- 3. DB 初始化/迁移（单一全量脚本，全程幂等）----------
# gongpu-full.sql = 原 schema.sql + seed-v3.sql + 全部 migration-*.sql 的整合版（唯一入口）：
# §1 表结构（CREATE TABLE IF NOT EXISTS）→ §2 历史加列（information_schema 守卫，缺列才加，
# 含原 deploy.sh migrate() 内联的 7 列）→ §3 基线种子 → §4 内容迁移（按版本序）。
# 新装一次到位、存量升级、重复执行三种场景同一文件；原 22 个 SQL 文件已删除，勿再引用。
if [[ -f "$SRC_DIR/src/main/resources/gongpu-full.sql" ]]; then
  mysql --default-character-set=utf8mb4 gongpu < "$SRC_DIR/src/main/resources/gongpu-full.sql" 2>/dev/null \
    && log "DB 全量脚本(gongpu-full.sql)执行完成" || err "gongpu-full.sql 执行失败(检查 mysql 可用性)"
else
  err "缺 gongpu-full.sql——检查 server/src 是否完整 scp 上传"
fi
log "DB 迁移核对完成（验收口径:cfg_equipment_template 52/cfg_item 34/cfg_quest 14/cfg_craft 16/cfg_affix 21/cfg_skill 22）"

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
