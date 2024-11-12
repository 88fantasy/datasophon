#!/bin/bash

BASE_DIR=$(dirname $0)

BASE_PATH=$(
  cd ${BASE_DIR}
  pwd
)
echo "Bash Path: ${BASE_PATH}"
INIT_PATH=$(dirname "${BASE_PATH}")
INIT_LOG_PATH=${INIT_PATH}/logs

echo "datasophon-init:${INIT_PATH}"
if [ ! -d "INIT_LOG_PATH" ]; then
  mkdir -p $INIT_LOG_PATH
fi

nohup java -jar datasophon-cli-cli.jar create cluster -p /data/datasophon -a initALL > ${INIT_LOG_PATH}/cli.log 2>&1 &
