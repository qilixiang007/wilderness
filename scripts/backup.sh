#!/usr/bin/env bash
# ============================================================
# 「宇宙是旷野」整站备份
#
# 备份内容（两类数据都要，别只备 MySQL）：
#   1. MySQL 逻辑备份 -> 用户/收藏/知识库文件元数据（.sql）
#   2. Elasticsearch 快照 -> 对话历史 + 知识库分析索引（挂在 es-backups 卷）
#
# 用法：bash scripts/backup.sh
# 环境变量（可选）：BACKUP_DIR=/var/backups/wilderness  RETAIN_DAYS=7
#
# 建议放 crontab，每天 3 点执行（示例）：
#   17 3 * * * cd /root/wilderness && bash scripts/backup.sh >> /var/log/wilderness-backup.log 2>&1
# （避开整点：minute 用 17，见 CLAUDE 约定）
# ============================================================
set -euo pipefail

cd "$(dirname "$0")/.."                     # 仓库根目录
COMPOSE="docker compose -f docker-compose.prod.yml"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/wilderness}"
RETAIN_DAYS="${RETAIN_DAYS:-7}"
STAMP=$(date +%Y%m%d-%H%M%S)
mkdir -p "$BACKUP_DIR/mysql"

echo "==> [$STAMP] 开始备份"

# ---------- 1. MySQL ----------
echo "--- 导出 MySQL wilderness 库"
$COMPOSE exec -T mysql sh -c \
    'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers wilderness' \
    > "$BACKUP_DIR/mysql/wilderness-$STAMP.sql"
echo "    MySQL 备份: $BACKUP_DIR/mysql/wilderness-$STAMP.sql ($(du -h "$BACKUP_DIR/mysql/wilderness-$STAMP.sql" | cut -f1))"

# ---------- 2. Elasticsearch 快照 ----------
echo "--- ES 快照（先确保仓库已注册，再打当日快照）"
$COMPOSE exec -T elasticsearch curl -fsS \
    -X PUT "http://localhost:9200/_snapshot/wilderness-backup" \
    -H 'Content-Type: application/json' \
    -d '{"type":"fs","settings":{"location":"/usr/share/elasticsearch/backups"}}' >/dev/null \
    || true    # 仓库已存在会返回 400，忽略即可

SNAP="wild-$STAMP"
# 快照为异步任务，用 wait_for_status=SUCCESS 同步等待完成（超时 120s）
$COMPOSE exec -T elasticsearch curl -fsS \
    -X PUT "http://localhost:9200/_snapshot/wilderness-backup/$SNAP?wait_for_status=SUCCESS&wait_for_completion=true&timeout=120s" \
    >/dev/null
echo "    ES 快照: $SNAP"

# ---------- 3. 清理过期备份 ----------
echo "--- 清理 $RETAIN_DAYS 天前的备份"
find "$BACKUP_DIR/mysql" -name 'wilderness-*.sql' -mtime "+$RETAIN_DAYS" -delete
$COMPOSE exec -T elasticsearch curl -fsS \
    "http://localhost:9200/_snapshot/wilderness-backup/_all" \
    | tr ',' '\n' | grep -o '"snapshot":"wild-[0-9]*-[0-9]*"' \
    | cut -d'"' -f4 | sort | head -n -"$RETAIN_DAYS" | while read -r old; do
        $COMPOSE exec -T elasticsearch curl -fsS -X DELETE \
            "http://localhost:9200/_snapshot/wilderness-backup/$old" >/dev/null && echo "    删除旧快照 $old"
    done || true

echo "==> [$STAMP] 备份完成 ✅"
