#!/bin/bash

# MiniSQL RegionServer启动脚本

# 设置Java路径
JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-11}
JAVA=$JAVA_HOME/bin/java

# 设置工作目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

# 设置日志目录
LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"

# 设置JVM参数
JVM_OPTS="-Xms512m -Xmx2g"
JVM_OPTS="$JVM_OPTS -XX:+UseG1GC"
JVM_OPTS="$JVM_OPTS -XX:MaxGCPauseMillis=200"
JVM_OPTS="$JVM_OPTS -XX:+HeapDumpOnOutOfMemoryError"
JVM_OPTS="$JVM_OPTS -XX:HeapDumpPath=$LOG_DIR/heap_dump.hprof"

# 设置环境变量
export REGIONSERVER_ID=${REGIONSERVER_ID:-rs-001}
export REGIONSERVER_PORT=${REGIONSERVER_PORT:-8001}

# 查找jar文件
JAR_FILE=$(find target -name "minisql-regionserver-*-jar-with-dependencies.jar" | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo "Error: JAR file not found. Please run 'mvn package' first."
    exit 1
fi

echo "Starting MiniSQL RegionServer..."
echo "ID: $REGIONSERVER_ID"
echo "Port: $REGIONSERVER_PORT"
echo "JAR: $JAR_FILE"
echo "Logs: $LOG_DIR"

# 启动服务器
$JAVA $JVM_OPTS -jar "$JAR_FILE" "$REGIONSERVER_ID" "$REGIONSERVER_PORT"