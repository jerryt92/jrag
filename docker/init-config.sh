#!/bin/sh
set -e

BASE_DIR="/jrag"
MILVUS_DIR="${BASE_DIR}/milvus"
VOLUME_DIR="${MILVUS_DIR}/volumes/milvus"
EMBED_ETCD_FILE="${MILVUS_DIR}/embedEtcd.yaml"
USER_FILE="${MILVUS_DIR}/user.yaml"
JRAG_CONFIG_DIR="${BASE_DIR}/config"
LOG_DIR="${BASE_DIR}/logs"

mkdir -p "${VOLUME_DIR}"
mkdir -p "${MILVUS_DIR}"
mkdir -p "${JRAG_CONFIG_DIR}"
mkdir -p "${LOG_DIR}"

if [ -d "${EMBED_ETCD_FILE}" ]; then
    rm -rf "${EMBED_ETCD_FILE}"
fi

if [ -d "${USER_FILE}" ]; then
    rm -rf "${USER_FILE}"
fi

if [ ! -f "${EMBED_ETCD_FILE}" ]; then
    cat << EOF > "${EMBED_ETCD_FILE}"
listen-client-urls: http://0.0.0.0:2379
advertise-client-urls: http://0.0.0.0:2379
quota-backend-bytes: 4294967296
auto-compaction-mode: revision
auto-compaction-retention: '1000'
EOF
fi

if [ ! -f "${USER_FILE}" ]; then
    cat << EOF > "${USER_FILE}"
# Extra config to override default milvus.yaml
EOF
fi

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
            fi
            ;;
    esac
done
