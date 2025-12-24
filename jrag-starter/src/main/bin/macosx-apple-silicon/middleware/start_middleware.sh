#!/bin/bash

# =================================================================
# 1. 环境准备与路径定位
# =================================================================

# 获取当前脚本所在目录的上一级目录 (假设脚本在 jrag-core/middleware 下，上一级是 jrag-core)
cd "$(dirname "$0")/.." || exit
WORK_DIR=$(pwd)

# 尝试加载 variables.sh
# 优先查找 bin/variables.sh (标准结构)
if [ -f "${WORK_DIR}/bin/variables.sh" ]; then
    source "${WORK_DIR}/bin/variables.sh"
# 兼容开发环境或扁平结构
elif [ -f "${WORK_DIR}/variables.sh" ]; then
    source "${WORK_DIR}/variables.sh"
else
    echo "CRITICAL ERROR: variables.sh not found in ${WORK_DIR}/bin or ${WORK_DIR}"
    exit 1
fi

# =================================================================
# 2. 配置参数
# =================================================================
HEALTH_URL="http://localhost:29530/health"
EXPECTED_JSON='{"status":"ok"}'
MAX_WAIT_SECONDS=300
LOG_FILE="${WORK_DIR}/logs/middleware.log"

# 确保日志目录存在
mkdir -p "${WORK_DIR}/logs"

echo "Checking Middleware..."

# =================================================================
# 3. 检查是否已运行
# =================================================================
# 1. 检查二进制文件
if [ ! -f "$MIDDLEWARE_EXEC" ]; then
    echo "ERROR: Middleware binary not found at: $MIDDLEWARE_EXEC"
    exit 1
fi
chmod +x "$MIDDLEWARE_EXEC"

# 2. 检查 PID 文件及进程
MID_PID=""
if [ -f "$MIDDLEWARE_PID_FILE" ]; then
    READ_PID=$(cat "$MIDDLEWARE_PID_FILE")
    # 检查进程是否存在
    if ps -p "$READ_PID" > /dev/null 2>&1; then
        # 进一步检查：如果进程活着，端口/服务是否也正常？
        if curl -s -m 1 "$HEALTH_URL" | grep -Fq "$EXPECTED_JSON"; then
            echo "Middleware is ALREADY RUNNING and HEALTHY. PID: $READ_PID"
            exit 0
        else
            echo "WARNING: PID file exists and process is running, but service is not healthy yet."
            # 这里可以选择退出，或者继续下面的等待逻辑（如果只是刚启动还没这就绪）
            # 为了简单起见，如果进程在跑，我们认为它已经是启动状态，直接交给主程序判断
            echo "Assuming startup in progress. PID: $READ_PID"
            exit 0
        fi
    else
        # PID 文件存在但进程不在，视为异常关闭，清理文件
        echo "Found stale PID file. Cleaning up."
        rm -f "$MIDDLEWARE_PID_FILE"
    fi
fi

# =================================================================
# 4. 启动中间件
# =================================================================
echo "Starting Middleware (Milvus Lite)..."

# 重要：切换到中间件的工作目录，确保数据文件 (milvus_data) 生成在正确位置
if [ ! -d "$MIDDLEWARE_WORK_DIR" ]; then
    echo "Creating middleware directory: $MIDDLEWARE_WORK_DIR"
    mkdir -p "$MIDDLEWARE_WORK_DIR"
fi

cd "$MIDDLEWARE_WORK_DIR" || {
    echo "ERROR: Could not change directory to $MIDDLEWARE_WORK_DIR"
    exit 1
}

# 启动命令 (输出重定向到日志)
nohup "$MIDDLEWARE_EXEC" > "$LOG_FILE" 2>&1 &
NEW_PID=$!

# 记录 PID
echo $NEW_PID > "$MIDDLEWARE_PID_FILE"
echo "Middleware process launched. PID: $NEW_PID"

# =================================================================
# 5. 健康检查循环 (Health Check Loop)
# =================================================================
echo -e "Waiting for Middleware to be ready ...\c"

for ((i=1; i<=MAX_WAIT_SECONDS; i++)); do
    # A. 进程存活自检
    # 如果进程在启动过程中挂了（例如配置文件错误、端口被占用），立即报错退出
    if ! ps -p "$NEW_PID" > /dev/null; then
        echo ""
        echo "CRITICAL ERROR: Middleware process died during startup!"
        echo "----------------- LOG TAIL -----------------"
        tail -n 10 "$LOG_FILE"
        echo "--------------------------------------------"
        rm -f "$MIDDLEWARE_PID_FILE"
        exit 1
    fi

    # B. 发送 HTTP 请求检查状态
    # -s: 静默模式 (不显示进度条)
    # -m 1: 最大超时 1秒 (防止卡死)
    RESPONSE=$(curl -s -m 1 "$HEALTH_URL" || true)

    # C. 验证响应内容
    # grep -Fq: 查找固定字符串，静默输出
    if echo "$RESPONSE" | grep -Fq "$EXPECTED_JSON"; then
        echo ""
        echo "SUCCESS: Middleware is healthy. Response: $RESPONSE"

        # 额外缓冲 1 秒，确保 socket 极其稳定
        sleep 1

        # 恢复工作目录并退出
        cd "$WORK_DIR"
        exit 0
    fi

    # 打印进度点
    echo -e ".\c"
    sleep 1
done

# =================================================================
# 6. 超时处理
# =================================================================
echo ""
echo "ERROR: Middleware failed to become healthy within $MAX_WAIT_SECONDS seconds."
echo "Last response: $RESPONSE"
echo "Killing hung process..."
kill "$NEW_PID" 2>/dev/null
rm -f "$MIDDLEWARE_PID_FILE"
exit 1