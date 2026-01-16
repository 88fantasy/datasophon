#!/bin/bash

# 解压密码
if [ $# -eq 0 ]; then
    echo "用法: $0 <password>"
    exit 1
fi
PASSWORD=$1
echo "PASSWORD:${PASSWORD}"

BASE_DIR=$(dirname $0)

BASE_PATH=$(
  cd ${BASE_DIR}
  pwd
)
echo "Bash Path: ${BASE_PATH}"
INIT_PATH=$(dirname "${BASE_PATH}")
INIT_LOG_PATH=${INIT_PATH}/logs
INIT_SBIN_PATH=${INIT_PATH}/sbin

if [ ! -d "INIT_LOG_PATH" ]; then
  mkdir -p $INIT_LOG_PATH
fi

source /etc/profile

echo "制品包解压解密"
java -jar ${INIT_SBIN_PATH}/datasophon-cli-cli.jar init registryDecode --enable --datasophonHomePath /data/datasophon -pwd ${PASSWORD} --initPath /data/datasophon/datasophon-init -pp /data -cn config.tar.gz -pn packages.tar.gz
