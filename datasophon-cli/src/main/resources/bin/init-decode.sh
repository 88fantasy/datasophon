#!/bin/bash

BASE_DIR=$(dirname $0)

BASE_PATH=$(
  cd ${BASE_DIR}
  pwd
)
echo "Bash Path: ${BASE_PATH}"
INIT_PATH=$(dirname "${BASE_PATH}")
INIT_LOG_PATH=${INIT_PATH}/logs
INIT_SBIN_PATH=${INIT_PATH}/sbin
PASSWORD=${PASSWORD:-5bWx3KT7vM7pJUjBf9GtSA==}

if [ ! -d "INIT_LOG_PATH" ]; then
  mkdir -p $INIT_LOG_PATH
fi

source /etc/profile
echo "解密元数据包"
java -jar ${INIT_SBIN_PATH}/datasophon-cli-cli.jar init configDecode -e -pp /data -cn config.tar.gz -p ${PASSWORD}
