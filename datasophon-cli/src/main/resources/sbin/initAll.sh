#!/bin/bash

BASE_DIR=$(dirname $0)

BASE_PATH=$(
  cd ${BASE_DIR}
  pwd
)
echo "Bash Path: ${BASE_PATH}"
INIT_PATH=$(dirname "${BASE_PATH}")
INIT_LOG_PATH=${INIT_PATH}/logs
INIT_BIN_PATH=${INIT_PATH}/bin
INIT_SBIN_PATH=${INIT_PATH}/sbin
#密钥
export PASSWORD=5bWx3KT7vM7pJUjBf9GtSA==

if [ ! -d "INIT_LOG_PATH" ]; then
  mkdir -p $INIT_LOG_PATH
fi

echo "ini tar"
bash ${INIT_BIN_PATH}/init-tar.sh > ${INIT_LOG_PATH}/init-tar.log

echo "ini jdk"
bash ${INIT_BIN_PATH}/init-jdk.sh > ${INIT_LOG_PATH}/init-jdk.log

echo "init registryDecode"
bash ${INIT_BIN_PATH}/init-registryDecode.sh > ${INIT_LOG_PATH}/init-registryDecode.log

source /etc/profile
echo "ini create cluster"
java -jar ${INIT_SBIN_PATH}/datasophon-cli-cli.jar create cluster --enableRegistry -p /data/datasophon -a initALL > ${INIT_LOG_PATH}/initAll.log
