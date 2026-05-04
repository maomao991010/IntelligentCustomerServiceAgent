#!/bin/bash
# ============================================================
# 票务系统 MySQL 数据库恢复脚本
# 用法: ./restore_mysql.sh <备份文件路径>
# 示例: ./restore_mysql.sh /opt/backup/mysql/ticketing_system_full_20260418_020000.sql.gz
# 警告: 恢复操作会覆盖现有数据，请谨慎执行!
# ============================================================

if [ -z "$1" ]; then
    echo "用法: $0 <备份文件路径>"
    echo "示例: $0 /opt/backup/mysql/ticketing_system_full_20260418_020000.sql.gz"
    exit 1
fi

BACKUP_FILE=$1

if [ ! -f "$BACKUP_FILE" ]; then
    echo "错误: 备份文件不存在: $BACKUP_FILE"
    exit 1
fi

DB_HOST="106.54.15.105"
DB_PORT="3306"
DB_USER="root"
DB_PASS="maomao123!"
DB_NAME="ticketing_system"

echo "============================================"
echo "警告: 此操作将覆盖数据库 ${DB_NAME} 的现有数据!"
echo "备份文件: ${BACKUP_FILE}"
echo "============================================"
read -p "确认恢复? (输入 YES 继续): " CONFIRM

if [ "$CONFIRM" != "YES" ]; then
    echo "已取消恢复操作"
    exit 0
fi

echo "开始恢复数据库 ${DB_NAME}..."

if [[ "$BACKUP_FILE" == *.gz ]]; then
    gunzip -c "$BACKUP_FILE" | mysql -h${DB_HOST} -P${DB_PORT} -u${DB_USER} -p${DB_PASS} ${DB_NAME}
else
    mysql -h${DB_HOST} -P${DB_PORT} -u${DB_USER} -p${DB_PASS} ${DB_NAME} < "$BACKUP_FILE"
fi

if [ $? -eq 0 ]; then
    echo "数据库恢复成功!"
else
    echo "数据库恢复失败!"
    exit 1
fi
