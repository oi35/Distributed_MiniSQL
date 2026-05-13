@echo off
REM MiniSQL RegionServer启动脚本 (Windows)

REM 设置工作目录
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

REM 设置日志目录
set LOG_DIR=%SCRIPT_DIR%logs
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

REM 设置JVM参数
set JVM_OPTS=-Xms512m -Xmx2g
set JVM_OPTS=%JVM_OPTS% -XX:+UseG1GC
set JVM_OPTS=%JVM_OPTS% -XX:MaxGCPauseMillis=200
set JVM_OPTS=%JVM_OPTS% -XX:+HeapDumpOnOutOfMemoryError
set JVM_OPTS=%JVM_OPTS% -XX:HeapDumpPath="%LOG_DIR%\heap_dump.hprof"

REM 设置环境变量
if "%REGIONSERVER_ID%"=="" set REGIONSERVER_ID=rs-001
if "%REGIONSERVER_PORT%"=="" set REGIONSERVER_PORT=8001

REM 查找jar文件
for %%f in (target\minisql-regionserver-*-jar-with-dependencies.jar) do set JAR_FILE=%%f

if "%JAR_FILE%"=="" (
    echo Error: JAR file not found. Please run 'mvn package' first.
    pause
    exit /b 1
)

echo Starting MiniSQL RegionServer...
echo ID: %REGIONSERVER_ID%
echo Port: %REGIONSERVER_PORT%
echo JAR: %JAR_FILE%
echo Logs: %LOG_DIR%

REM 启动服务器（后台运行）
start "MiniSQL-RegionServer" "%JAVA%" %JVM_OPTS% -jar "%JAR_FILE%" "%REGIONSERVER_ID%" "%REGIONSERVER_PORT%"

REM 写入PID文件
for /f "tokens=2 delims=," %%a in ('wmic process where "name='java.exe' and commandline like '%%MiniSQL-RegionServer%%'" get processid /format:csv 2^>nul') do (
    if not "%%a"=="" echo %%a > "%LOG_DIR%\regionserver.pid"
)

echo RegionServer started.
