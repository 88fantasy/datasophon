#!/bin/bash

# 检查是否提供了密码参数
if [ $# -eq 0 ]; then
    echo "用法: $0 <password>"
    exit 1
fi
PASSWORD=$1

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

if [ ! -d "INIT_LOG_PATH" ]; then
  mkdir -p $INIT_LOG_PATH
fi

echo "ini jdk8"
bash ${INIT_BIN_PATH}/init-jdk8.sh > ${INIT_LOG_PATH}/init-jdk8.log
echo "ini jdk17"
bash ${INIT_BIN_PATH}/init-jdk17.sh > ${INIT_LOG_PATH}/init-jdk17.log

echo "init registryDecode"
bash ${INIT_BIN_PATH}/init-registryDecode.sh ${PASSWORD} > ${INIT_LOG_PATH}/init-registryDecode.log

source /etc/profile
echo "ini create cluster"
java -jar ${INIT_SBIN_PATH}/datasophon-cli-cli.jar create cluster --enableRegistry -pwd ${PASSWORD} -p /data/datasophon -in /data/install_datasophon -a initALL > ${INIT_LOG_PATH}/initAll.log