#!/bin/bash

# MiniSQL Master Server启动脚本

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
export MASTER_PORT=${MASTER_PORT:-8000}

# 查找jar文件
JAR_FILE=$(find target -name "minisql-master-*-jar-with-dependencies.jar" | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo "Error: JAR file not found. Please run 'mvn package' first."
    exit 1
fi

echo "Starting MiniSQL Master Server..."
echo "Port: $MASTER_PORT"
echo "JAR: $JAR_FILE"
echo "Logs: $LOG_DIR"

# 启动服务器
$JAVA $JVM_OPTS -jar "$JAR_FILE" "$MASTER_PORT"
