@echo off
REM MiniSQL Master Server启动脚本 (Windows)

REM 设置Java路径
if "%JAVA_HOME%"=="" (
    set JAVA=java
) else (
    set JAVA=%JAVA_HOME%\bin\java
)

REM 设置工作目录
cd /d %~dp0

REM 设置日志目录
set LOG_DIR=%~dp0logs
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

REM 设置JVM参数
set JVM_OPTS=-Xms512m -Xmx2g
set JVM_OPTS=%JVM_OPTS% -XX:+UseG1GC
set JVM_OPTS=%JVM_OPTS% -XX:MaxGCPauseMillis=200
set JVM_OPTS=%JVM_OPTS% -XX:+HeapDumpOnOutOfMemoryError
set JVM_OPTS=%JVM_OPTS% -XX:HeapDumpPath=%LOG_DIR%\heap_dump.hprof

REM 设置环境变量
if "%MASTER_PORT%"=="" set MASTER_PORT=8000

REM 查找jar文件
for %%f in (target\minisql-master-*-jar-with-dependencies.jar) do set JAR_FILE=%%f

if "%JAR_FILE%"=="" (
    echo Error: JAR file not found. Please run 'mvn package' first.
    exit /b 1
)

echo Starting MiniSQL Master Server...
echo Port: %MASTER_PORT%
echo JAR: %JAR_FILE%
echo Logs: %LOG_DIR%

REM 启动服务器
%JAVA% %JVM_OPTS% -jar "%JAR_FILE%" %MASTER_PORT%
