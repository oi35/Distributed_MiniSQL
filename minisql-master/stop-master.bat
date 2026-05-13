@echo off
REM MiniSQL Master Server停止脚本 (Windows)

set SCRIPT_DIR=%~dp0
set PID_FILE=%SCRIPT_DIR%logs\master.pid
set PID=

if exist "%PID_FILE%" (
    set /p PID=<"%PID_FILE%"
    echo Stopping MiniSQL Master Server (PID: %PID%)...
    if not "%PID%"=="" (
        taskkill /PID %PID% >nul 2>&1
    )
) else (
    REM 通过wmic查找进程
    for /f "tokens=2 delims=," %%a in ('wmic process where "commandline like '%%minisql-master%%jar-with-dependencies%%'" get processid /format:csv 2^>nul') do (
        if not "%%a"=="" set PID=%%a
    )
)

if "%PID%"=="" (
    echo Master Server is not running.
    exit /b 0
)

echo Stopping MiniSQL Master Server (PID: %PID%)...
taskkill /PID %PID% >nul 2>&1
timeout /t 3 /nobreak >nul

REM 检查是否仍在运行
tasklist /FI "PID eq %PID%" 2>nul | findstr /C:"%PID%" >nul
if errorlevel 1 (
    echo Master Server stopped gracefully.
) else (
    echo Force stopping Master Server...
    taskkill /F /PID %PID% >nul 2>&1
)

if exist "%PID_FILE%" del "%PID_FILE%"
echo Done.
