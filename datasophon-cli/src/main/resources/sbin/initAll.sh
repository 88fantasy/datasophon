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

if [ ! -d "INIT_LOG_PATH" ]; then
  mkdir -p $INIT_LOG_PATH
fi

echo "ini tar"
bash ${INIT_BIN_PATH}/init-tar.sh > ${INIT_LOG_PATH}/init-tar.log

echo "ini jdk8"
bash ${INIT_BIN_PATH}/init-jdk8.sh > ${INIT_LOG_PATH}/init-jdk8.log
echo "ini jdk17"
bash ${INIT_BIN_PATH}/init-jdk17.sh > ${INIT_LOG_PATH}/init-jdk17.log

echo "init registryDecode"
bash ${INIT_BIN_PATH}/init-registryDecode.sh > ${INIT_LOG_PATH}/init-registryDecode.log

source /etc/profile
echo "ini create cluster"
java -jar ${INIT_SBIN_PATH}/datasophon-cli-cli.jar create cluster --enableRegistry -p /data/datasophon -a initALL > ${INIT_LOG_PATH}/initAll.log