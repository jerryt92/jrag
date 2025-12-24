#!/bin/bash

cd "$(dirname "$0")/.." || exit
WORK_DIR=$(pwd)
source "${WORK_DIR}/bin/variables.sh"

PID_FILE="${WORK_DIR}/run.pid"

# ================= 1. 停止 Java 主程序 =================
PID=$(ps -ef | grep "program.tag=${PROGRAM_TAG}" | grep -v grep | awk '{print $2}')

if [ -n "$PID" ]; then
    echo "Stopping Main Application (PID: $PID)..."
    kill "$PID"

    # 简单的等待逻辑
    COUNT=0
    while kill -0 "$PID" 2>/dev/null; do
        sleep 1
        COUNT=$((COUNT+1))
        if [ $COUNT -ge 10 ]; then
             kill -9 "$PID"
             break
        fi
    done
    echo "Main Application stopped."
    rm -f "$PID_FILE"
else
    echo "Main Application is not running."
    rm -f "$PID_FILE"
fi

echo "----------------------------------------------------------------"

# ================= 2. 停止中间件 =================
/bin/bash "${BIN_DIR}/middleware/stop_middleware.sh"