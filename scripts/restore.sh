#!/usr/bin/env bash
# ============================================================
# 「宇宙是旷野」恢复脚本（灾难演练/误删数据时用）
#
# 用法：
#   # 1) 先恢复 MySQL
#   bash scripts/restore.sh mysql /var/backups/wilderness/mysql/wilderness-20260903-030000.sql
#
#   # 2) 再恢复 ES（参数 = 快照名，见 backup 日志里的 "ES 快照: wild-xxx"）
#   bash scripts/restore.sh es wild-20260903-030000
#
# 注意：恢复会覆盖当前数据，执行前确认备份文件时间正确。
# 建议先恢复到一个临时文件里人工核对（见 MySQL 分支注释）。
# ============================================================
set -euo pipefail

cd "$(dirname "$0")/.."
COMPOSE="docker compose -f docker-compose.prod.yml"

MODE="${1:-}"
shift || true

case "$MODE" in
    mysql)
        SQL="${1:?用法: restore.sh mysql <备份.sql>}"
        [ -f "$SQL" ] || { echo "找不到备份文件: $SQL" >&2; exit 1; }
        echo "==> 恢复 MySQL：$SQL"
        echo "    将覆盖 wilderness 库当前数据，Ctrl-C 可取消（3 秒倒计时）"
        sleep 3
        $COMPOSE exec -T mysql sh -c \
            'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" wilderness' < "$SQL"
        echo "    MySQL 恢复完成 ✅"
        ;;

    es)
        SNAP="${1:?用法: restore.sh es <快照名>}"
        echo "==> 恢复 ES 快照：$SNAP"
        # 关闭目标索引再恢复（索引可重建，但知识库分析索引含用户数据，须整体回滚）
        for IDX in wilderness-knowledge wilderness-conversation; do
            $COMPOSE exec -T elasticsearch curl -fsS \
                -X POST "http://localhost:9200/$IDX/_close" >/dev/null 2>&1 || true
        done
        $COMPOSE exec -T elasticsearch curl -fsS \
            -X POST "http://localhost:9200/_snapshot/wilderness-backup/$SNAP/_restore?wait_for_completion=true" \
            -H 'Content-Type: application/json' \
            -d '{"indices":"wilderness-knowledge,wilderness-conversation","ignore_unavailable":true}' >/dev/null
        echo "    ES 恢复完成 ✅（如索引被占用会报错，先删除同名索引再重试）"
        ;;
    *)
        echo "用法:"
        echo "  bash scripts/restore.sh mysql <backup.sql>   # 恢复数据库"
        echo "  bash scripts/restore.sh es <snapshot-name>   # 恢复 ES 索引"
        echo "（备份见 scripts/backup.sh）"
        exit 1
        ;;
esac
