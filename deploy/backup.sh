#!/bin/bash
# Forge 数据备份：SQLite 数据库 + 制品 + 加密密钥（缺一不可恢复）
# 建议 crontab 每日执行：30 2 * * * /opt/forge/backup.sh
set -e

DATA_DIR="${FORGE_DATA_DIR:-$HOME/.forge}"
BACKUP_DIR="${1:-$HOME/forge-backups}"
KEEP=14

if [ ! -d "$DATA_DIR" ]; then
  echo "数据目录不存在: $DATA_DIR" >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"
STAMP=$(date +%Y%m%d-%H%M%S)
OUT="$BACKUP_DIR/forge-backup-$STAMP.tar.gz"

tar -czf "$OUT" -C "$(dirname "$DATA_DIR")" "$(basename "$DATA_DIR")"

# 保留最近 KEEP 份
ls -1t "$BACKUP_DIR"/forge-backup-*.tar.gz | tail -n +$((KEEP + 1)) | xargs -r rm -f

echo "备份完成: $OUT ($(du -h "$OUT" | cut -f1))"
