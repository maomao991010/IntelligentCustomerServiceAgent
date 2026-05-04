-- ============================================================
-- MySQL 慢查询监控配置
-- 说明: 在MySQL服务器上执行以下配置，开启慢查询日志
-- 注意: 需要SUPER权限，建议在MySQL配置文件中持久化
-- ============================================================

-- 1. 开启慢查询日志
SET GLOBAL slow_query_log = 'ON';

-- 2. 设置慢查询阈值（秒），超过此时间的查询将被记录
-- 生产环境建议 1-2 秒，开发环境可设为 0.5 秒
SET GLOBAL long_query_time = 1;

-- 3. 记录未使用索引的查询
SET GLOBAL log_queries_not_using_indexes = 'ON';

-- 4. 设置慢查询日志文件路径（根据实际服务器路径调整）
-- Linux 默认: /var/log/mysql/mysql-slow.log
-- Windows 默认: C:/ProgramData/MySQL/MySQL Server 8.0/Data/mysql-slow.log
SET GLOBAL slow_query_log_file = '/var/log/mysql/mysql-slow.log';

-- 5. 设置每分钟记录未使用索引查询的最大数量（防止日志过大）
SET GLOBAL log_throttle_queries_not_using_indexes = 60;

-- 6. 慢查询日志输出方式（FILE/TABLE/NONE）
-- TABLE 方式便于SQL查询分析
SET GLOBAL log_output = 'FILE,TABLE';

-- ============================================================
-- 验证配置
-- ============================================================
-- SHOW VARIABLES LIKE 'slow_query_log%';
-- SHOW VARIABLES LIKE 'long_query_time';
-- SHOW VARIABLES LIKE 'log_queries_not_using_indexes';

-- ============================================================
-- 分析慢查询（TABLE模式）
-- ============================================================
-- SELECT * FROM mysql.slow_log ORDER BY start_time DESC LIMIT 20;
-- SELECT query_time, sql_text FROM mysql.slow_log WHERE sql_text LIKE '%order%' ORDER BY query_time DESC LIMIT 10;

-- ============================================================
-- 使用 mysqldumpslow 工具分析慢查询日志（FILE模式）
-- ============================================================
-- 按查询时间排序，显示前10条
-- mysqldumpslow -s t -t 10 /var/log/mysql/mysql-slow.log
-- 按查询次数排序，显示前10条
-- mysqldumpslow -s c -t 10 /var/log/mysql/mysql-slow.log
-- 按平均查询时间排序
-- mysqldumpslow -s at -t 10 /var/log/mysql/mysql-slow.log

-- ============================================================
-- MySQL 配置文件持久化（my.cnf / my.ini）
-- 将以下内容添加到 [mysqld] 段
-- ============================================================
-- [mysqld]
-- slow_query_log = 1
-- long_query_time = 1
-- log_queries_not_using_indexes = 1
-- slow_query_log_file = /var/log/mysql/mysql-slow.log
-- log_output = FILE,TABLE
-- log_throttle_queries_not_using_indexes = 60
