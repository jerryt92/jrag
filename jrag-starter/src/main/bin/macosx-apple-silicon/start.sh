#!/bin/bash

# 修正1：只回退一级，定位到项目根目录
cd "$(dirname "$0")/.." || exit
WORK_DIR=$(pwd)

# 修正2：正确加载变量 (variables.sh 在 bin 下)
source "${WORK_DIR}/bin/variables.sh"

PID_FILE="${WORK_DIR}/run.pid"

# ================= 1. 启动中间件 =================
# 这里的路径要与实际结构匹配
/bin/bash "${BIN_DIR}/middleware/start_middleware.sh"
if [ $? -ne 0 ]; then
    echo "ABORTING: Middleware failed to start."
    exit 1
fi

echo "----------------------------------------------------------------"

# ================= 2. 启动 Java 主程序 =================
# Java 可以保留 program.tag 逻辑，因为 Java 可以在参数里带 -D
PID=$(ps -ef | grep "program.tag=${PROGRAM_TAG}" | grep -v grep | awk '{print $2}')

if [ -n "$PID" ]; then
    echo "Main Application is ALREADY RUNNING with PID: $PID"
    exit 1
fi

echo "Starting Main Application..."
# 确保 logs 目录存在
mkdir -p "${WORK_DIR}/logs"
nohup "$JAVA_EXEC" -cp "$SVC_CP" $JAVA_OPTS "$MAIN_CLASS" $APP_ARGS > "${WORK_DIR}/logs/console.log" 2>&1 &
NEW_PID=$!
echo $NEW_PID > "$PID_FILE"

sleep 2 # 稍微多睡一秒
CHECK_PID=$(ps -p $NEW_PID -o pid=)

if [ -n "$CHECK_PID" ]; then
    echo "Main Application started SUCCESSFULLY. PID: $CHECK_PID"
    echo "Access : http://localhost:30110"
else
    echo "Main Application FAILED to start. Check logs/console.log."
fi