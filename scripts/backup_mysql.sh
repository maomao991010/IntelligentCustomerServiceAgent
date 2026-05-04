#!/bin/bash
# ============================================================
# 票务系统 MySQL 数据库自动备份脚本
# 用法: ./backup_mysql.sh
# 建议配合 crontab 定时执行
#   每天凌晨2点全量备份: 0 2 * * * /opt/scripts/backup_mysql.sh >> /var/log/mysql-backup.log 2>&1
# ============================================================

DB_HOST="106.54.15.105"
DB_PORT="3306"
DB_USER="root"
DB_PASS="maomao123!"
DB_NAME="ticketing_system"

BACKUP_DIR="/opt/backup/mysql"
RETAIN_DAYS=30

DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/${DB_NAME}_full_${DATE}.sql.gz"
LATEST_LINK="${BACKUP_DIR}/${DB_NAME}_latest.sql.gz"

mkdir -p ${BACKUP_DIR}

echo "[${DATE}] 开始备份数据库 ${DB_NAME}..."

mysqldump -h${DB_HOST} -P${DB_PORT} -u${DB_USER} -p${DB_PASS} \
    --single-transaction \
    --routines \
    --triggers \
    --events \
    --set-gtid-purged=OFF \
    --quick \
    --lock-tables=false \
    ${DB_NAME} | gzip > ${BACKUP_FILE}

if [ $? -eq 0 ]; then
    echo "[${DATE}] 备份成功: ${BACKUP_FILE}"
    echo "[${DATE}] 备份大小: $(du -h ${BACKUP_FILE} | cut -f1)"

    ln -sf ${BACKUP_FILE} ${LATEST_LINK}

    find ${BACKUP_DIR} -name "${DB_NAME}_full_*.sql.gz" -type f -mtime +${RETAIN_DAYS} -delete
    echo "[${DATE}] 已清理 ${RETAIN_DAYS} 天前的旧备份"
else
    echo "[${DATE}] 备份失败!"
    exit 1
fi

echo "----------------------------------------"
