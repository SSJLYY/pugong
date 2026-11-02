#!/usr/bin/env bash
# ============================================================
# 数据库备份(136 服务器):mysqldump 全量 + 本地保留 7 份轮转
# 建议挂 cron(每天凌晨 3:30):
#   30 3 * * * bash /data/gongpu/app/deploy/backup-db.sh >> /data/gongpu/logs/backup.log 2>&1
# 恢复:
#   mysql -h127.0.0.1 -ugongpu_app -p gongpu < /data/gongpu/backups/gongpu_YYYYmmdd_HHMMSS.sql.gz
#   (恢复前先 systemctl stop gongpu-server,恢复后重启)
# ============================================================
set -euo pipefail

BACKUP_DIR="/data/gongpu/backups"
KEEP=7
STAMP="$(date +%Y%m%d_%H%M%S)"
OUT="$BACKUP_DIR/gongpu_$STAMP.sql.gz"

mkdir -p "$BACKUP_DIR"

# root 免密(读 /root/.my.cnf);业务库 gongpu 全量 + 例程
mysqldump --single-transaction --routines --triggers --events \
  --default-character-set=utf8mb4 gongpu | gzip > "$OUT"

# 轮转:只保留最近 KEEP 份
ls -1t "$BACKUP_DIR"/gongpu_*.sql.gz 2>/dev/null | tail -n +$((KEEP + 1)) | xargs -r rm -f

# 完整性校验:压缩流截断(磁盘满等)当场发现,而不是恢复时才发现备份是坏的
if ! gzip -t "$OUT" 2>/dev/null; then
  echo "[$(date '+%F %T')] ⚠️ 备份文件损坏(gzip 校验失败): $OUT" >&2
  rm -f "$OUT"
  exit 1
fi

SIZE=$(du -h "$OUT" | cut -f1)
echo "[$(date '+%F %T')] 备份完成: $OUT ($SIZE)"
