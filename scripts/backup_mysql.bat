@echo off
REM ============================================================
REM 票务系统 MySQL 数据库自动备份脚本 (Windows)
REM 建议配合 Windows 任务计划程序定时执行
REM   每天凌晨2点: schtasks /create /tn "MySQLBackup" /tr "D:\scripts\backup_mysql.bat" /sc daily /st 02:00
REM ============================================================

set DB_HOST=106.54.15.105
set DB_PORT=3306
set DB_USER=root
set DB_PASS=maomao123!
set DB_NAME=ticketing_system

set BACKUP_DIR=D:\backup\mysql
set RETAIN_DAYS=30

set DATE=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set DATE=%DATE: =0%
set BACKUP_FILE=%BACKUP_DIR%\%DB_NAME%_full_%DATE%.sql

if not exist %BACKUP_DIR% mkdir %BACKUP_DIR%

echo [%DATE%] 开始备份数据库 %DB_NAME%...

mysqldump -h%DB_HOST% -P%DB_PORT% -u%DB_USER% -p%DB_PASS% --single-transaction --routines --triggers --events --set-gtid-purged=OFF --quick --lock-tables=false %DB_NAME% > %BACKUP_FILE%

if %ERRORLEVEL% EQU 0 (
    echo [%DATE%] 备份成功: %BACKUP_FILE%
    for %%A in (%BACKUP_FILE%) do echo [%DATE%] 备份大小: %%~zA bytes

    REM 压缩备份文件（需要安装 7-Zip）
    if exist "C:\Program Files\7-Zip\7z.exe" (
        "C:\Program Files\7-Zip\7z.exe" a -tgzip %BACKUP_FILE%.gz %BACKUP_FILE%
        del %BACKUP_FILE%
        echo [%DATE%] 已压缩备份文件
    )

    REM 清理旧备份
    forfiles /p %BACKUP_DIR% /m %DB_NAME%_full_*.sql* /d -%RETAIN_DAYS% /c "cmd /c del @path" 2>nul
    echo [%DATE%] 已清理 %RETAIN_DAYS% 天前的旧备份
) else (
    echo [%DATE%] 备份失败!
    del %BACKUP_FILE% 2>nul
    exit /b 1
)

echo ----------------------------------------
