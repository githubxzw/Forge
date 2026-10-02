#!/bin/bash
# Forge 数据备份：SQLite 一致性快照 + 制品 + 加密密钥（缺一不可恢复）
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
TMP_DB=$(mktemp -d)
trap 'rm -rf "$TMP_DB"' EXIT

# SQLite 开启 WAL 后直接拷贝可能不一致：优先用 .backup 命令做在线一致性快照
if command -v sqlite3 >/dev/null 2>&1; then
  sqlite3 "$DATA_DIR/forge.db" ".backup '$TMP_DB/forge.db'"
else
  echo "警告: 未安装 sqlite3，退化为直接拷贝（建议安装以获得一致性快照）" >&2
  cp "$DATA_DIR/forge.db" "$TMP_DB/forge.db"
fi

# WAL/SHM 文件不需要打包（快照已包含合并结果）
tar -czf "$OUT" -C "$TMP_DB" forge.db -C "$DATA_DIR" artifacts .secret.key .jwt.secret

# 保留最近 KEEP 份
ls -1t "$BACKUP_DIR"/forge-backup-*.tar.gz | tail -n +$((KEEP + 1)) | xargs -r rm -f

echo "备份完成: $OUT ($(du -h "$OUT" | cut -f1))"
