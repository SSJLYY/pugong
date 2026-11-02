#!/usr/bin/env bash
# ============================================================
# 本地一键起服(Windows Git Bash / Linux 通用)
#   1. 读 server/local.env 注入 DB_PASSWORD / JWT_SECRET / ADMIN_PASSWORD
#   2. JDK21 + Maven 离线打包(依赖已缓存)
#   3. java -jar 激活 local profile
# 用法: bash server/run-local.sh          # 构建+运行
#       bash server/run-local.sh --fast   # 跳过构建直接跑旧 jar
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"                      # server/

# ---- JDK21 / Maven(本地安装路径,找不到则用 PATH 上的) ----
# 必须 JDK21:类是 21 编译的(65.0),PATH 上的 Oracle Java 17 会 UnsupportedClassVersionError
if [ -d "/d/soft/java/jdk21" ]; then
  export JAVA_HOME="D:\\soft\\java\\jdk21"
  export PATH="/d/soft/java/jdk21/bin:$PATH"
fi
[ -f /d/soft/apache-maven-3.9.12/bin/mvn ] && export PATH="/d/soft/apache-maven-3.9.12/bin:$PATH"
[ "$(java -version 2>&1 | head -1 | grep -o '"[0-9]*' | tr -d '"')" = "21" ] || { echo "[run-local] 需要 JDK21,当前: $(java -version 2>&1 | head -1)"; exit 1; }

# ---- 密钥(绝不入库) ----
if [ ! -f local.env ]; then
  echo "[run-local] 缺少 server/local.env,先执行: cp local.env.example local.env"
  exit 1
fi
set -a; source local.env; set +a

# ---- 前置检查:MySQL / Redis 可达 ----
if ! (echo > /dev/tcp/127.0.0.1/3306) 2>/dev/null; then echo "[run-local] MySQL 127.0.0.1:3306 不可达"; exit 1; fi
if ! redis-cli ping 2>/dev/null | grep -q PONG; then echo "[run-local] Redis 127.0.0.1:6379 不可达"; exit 1; fi

JAR="target/gongpu-server.jar"
if [ "${1:-}" != "--fast" ] || [ ! -f "$JAR" ]; then
  echo "[run-local] Maven 打包(离线,跳过测试)…"
  mvn -o -q -DskipTests package
fi

echo "[run-local] 启动 gongpu-server (local profile) :8081  Ctrl+C 停止"
exec "${JAVA_HOME:-}/bin/java" -jar "$JAR" --spring.profiles.active=local
