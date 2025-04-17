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

if [ ! -d "INIT_LOG_PATH" ]; then
  mkdir -p $INIT_LOG_PATH
fi

echo "ini tar"
bash ${INIT_BIN_PATH}/init-tar.sh > ${INIT_LOG_PATH}/init-tar.log

echo "ini jdk"
bash ${INIT_BIN_PATH}/init-jdk.sh > ${INIT_LOG_PATH}/init-jdk.log

source /etc/profile
echo "ini create cluster"
java -jar datasophon-cli-cli.jar create cluster -p /data/datasophon -a initALL > ${INIT_LOG_PATH}/cli.log
