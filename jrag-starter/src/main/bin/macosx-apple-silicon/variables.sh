#!/bin/bash

if [ -n "${BASH_SOURCE[0]}" ]; then
  THIS_SCRIPT="${BASH_SOURCE[0]}"
else
  # 兼容非 Bash 环境 (fallback)
  THIS_SCRIPT="$0"
fi

# 获取 bin 目录的绝对路径
BIN_DIR=$(cd "$(dirname "$THIS_SCRIPT")"; pwd)
# 获取项目根目录 (bin 的上一级)
PROGRAM_DIR=$(cd "$BIN_DIR/.."; pwd)

# 打印调试信息 (可选，确保路径正确)
# echo "DEBUG: BIN_DIR=$BIN_DIR"
# echo "DEBUG: PROGRAM_DIR=$PROGRAM_DIR"

# ================= 唯一标识与主类 =================
PROGRAM_TAG=28544c8f1d7d45068e82c77d469c3be5
MAIN_CLASS=io.github.jerryt92.jrag.JragStarterMain

# ================= Java 环境配置 (强制使用 Bundled JRE) =================
BUNDLED_JRE=$PROGRAM_DIR/middleware/jre
JAVA_EXEC="$BUNDLED_JRE/bin/java"

if [ ! -x "$JAVA_EXEC" ]; then
    echo "CRITICAL ERROR: Bundled JRE not found at $JAVA_EXEC"
    # 使用 return 兼容 source 调用
    return 1 2>/dev/null || exit 1
fi

# ================= Middleware (Milvus) 配置 =================
MIDDLEWARE_EXEC="$PROGRAM_DIR/middleware/milvus-lite/milvus-lite"
MIDDLEWARE_WORK_DIR="$PROGRAM_DIR/middleware/milvus-lite"
MIDDLEWARE_PID_FILE="$PROGRAM_DIR/middleware.${PROGRAM_TAG}.pid"

# ================= JVM 参数配置 =================
JAVA_OPTS="-Xms512m -Xmx2048m -XX:MetaspaceSize=80m -XX:MaxMetaspaceSize=128m"
JAVA_OPTS="${JAVA_OPTS} -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=utf-8"
JAVA_OPTS="${JAVA_OPTS} -Duser.language=zh -Duser.country=CN"
JAVA_OPTS="${JAVA_OPTS} -Dprogram.tag=${PROGRAM_TAG}"

# ================= Classpath & Args =================
APP_ARGS=""
WORK_DIR=${PROGRAM_DIR}
SVC_CP="${WORK_DIR}/classes:${WORK_DIR}/lib/*"