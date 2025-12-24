#!/bin/bash

cd "$(dirname "$0")/.." || exit
source "./variables.sh"

echo "Stopping Middleware..."

# 修正：优先读取 PID 文件
MID_PID=""
if [ -f "$MIDDLEWARE_PID_FILE" ]; then
    MID_PID=$(cat "$MIDDLEWARE_PID_FILE")
fi

# 如果 PID 文件不存在，尝试通过路径查找
if [ -z "$MID_PID" ]; then
    MID_PID=$(ps -ef | grep "$MIDDLEWARE_EXEC" | grep -v grep | awk '{print $2}')
fi

if [ -n "$MID_PID" ]; then
    echo "Killing Middleware PID: $MID_PID"
    kill "$MID_PID"

    COUNT=0
    while ps -p "$MID_PID" > /dev/null 2>&1; do
        echo -e ".\c"
        sleep 1
        COUNT=$((COUNT+1))
        if [ $COUNT -gt 10 ]; then
            echo "Force killing Middleware..."
            kill -9 "$MID_PID"
            break
        fi
    done
    echo "Middleware stopped."
    rm -f "$MIDDLEWARE_PID_FILE"
else
    echo "Middleware process not found (Checked PID file and process list)."
    # 即使没找到进程，也要清理残留的 PID 文件
    rm -f "$MIDDLEWARE_PID_FILE"
fi