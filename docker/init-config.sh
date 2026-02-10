#!/bin/sh
# 注意：这里去掉了 set -e，改为手动检查错误

BASE_DIR="/jrag"
MILVUS_DIR="${BASE_DIR}/milvus"
VOLUME_DIR="${MILVUS_DIR}/volumes/milvus"
EMBED_ETCD_FILE="${MILVUS_DIR}/embedEtcd.yaml"
USER_FILE="${MILVUS_DIR}/user.yaml"
JRAG_CONFIG_DIR="${BASE_DIR}/config"
LOG_DIR="${BASE_DIR}/logs"

# 创建目录并检查结果
mkdir -p "${VOLUME_DIR}"
if [ $? -ne 0 ]; then
    echo "Error: Failed to create directory ${VOLUME_DIR}"
    exit 1
fi

mkdir -p "${MILVUS_DIR}"
if [ $? -ne 0 ]; then
    echo "Error: Failed to create directory ${MILVUS_DIR}"
    exit 1
fi

mkdir -p "${JRAG_CONFIG_DIR}"
if [ $? -ne 0 ]; then
    echo "Error: Failed to create directory ${JRAG_CONFIG_DIR}"
    exit 1
fi

mkdir -p "${LOG_DIR}"
if [ $? -ne 0 ]; then
    echo "Error: Failed to create directory ${LOG_DIR}"
    exit 1
fi

# 处理 embedEtcd.yaml
if [ -d "${EMBED_ETCD_FILE}" ]; then
    rm -rf "${EMBED_ETCD_FILE}"
    if [ $? -ne 0 ]; then
        echo "Error: Failed to remove directory ${EMBED_ETCD_FILE}"
        exit 1
    fi
fi

# 处理 user.yaml
if [ -d "${USER_FILE}" ]; then
    rm -rf "${USER_FILE}"
    if [ $? -ne 0 ]; then
        echo "Error: Failed to remove directory ${USER_FILE}"
        exit 1
    fi
fi

# 写入 embedEtcd.yaml
if [ ! -f "${EMBED_ETCD_FILE}" ]; then
    cat << EOF > "${EMBED_ETCD_FILE}"
listen-client-urls: http://0.0.0.0:2379
advertise-client-urls: http://0.0.0.0:2379
quota-backend-bytes: 4294967296
auto-compaction-mode: revision
auto-compaction-retention: '1000'
EOF
    # 检查写入是否成功
    if [ $? -ne 0 ]; then
        echo "Error: Failed to write to ${EMBED_ETCD_FILE}"
        exit 1
    fi
fi

# 写入 user.yaml
if [ ! -f "${USER_FILE}" ]; then
    cat << EOF > "${USER_FILE}"
# Extra config to override default milvus.yaml
EOF
    if [ $? -ne 0 ]; then
        echo "Error: Failed to write to ${USER_FILE}"
        exit 1
    fi
fi

# 循环复制模板文件
for item in /templates/*; do
    if [ ! -e "${item}" ]; then
        continue
    fi

    base_name="$(basename "${item}")"

    # 仅复制 yaml/txt/json 文件，且目标不存在时才复制
    case "${base_name}" in
        application-dev.yaml)
            # 排除 application-dev.yaml 文件
            ;;
        *.yaml|*.txt|*.json)
            # 处理其他 .yaml、.txt 和 .json 文件
            if [ ! -e "${JRAG_CONFIG_DIR}/${base_name}" ]; then
                cp "${item}" "${JRAG_CONFIG_DIR}"
                if [ $? -ne 0 ]; then
                    echo "Error: Failed to copy ${item} to ${JRAG_CONFIG_DIR}"
                    exit 1
                fi
            fi
            ;;
    esac
done

echo "Init config completed successfully."