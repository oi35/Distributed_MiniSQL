#!/bin/bash

# MiniSQL RegionServer停止脚本

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

PID_FILE="$SCRIPT_DIR/logs/regionserver.pid"

if [ ! -f "$PID_FILE" ]; then
    # 尝试通过进程名查找
    PID=$(pgrep -f "minisql-regionserver.*jar-with-dependencies" 2>/dev/null | head -1)
    if [ -z "$PID" ]; then
        echo "RegionServer is not running (PID file not found)."
        exit 0
    fi
else
    PID=$(cat "$PID_FILE")
fi

echo "Stopping MiniSQL RegionServer (PID: $PID)..."
kill "$PID" 2>/dev/null

# 等待进程退出
for i in $(seq 1 10); do
    if ! kill -0 "$PID" 2>/dev/null; then
        break
    fi
    echo "Waiting for graceful shutdown... ($i/10)"
    sleep 1
done

# 强制终止
if kill -0 "$PID" 2>/dev/null; then
    echo "Force stopping RegionServer..."
    kill -9 "$PID" 2>/dev/null
fi

rm -f "$PID_FILE"
echo "RegionServer stopped."
