#!/bin/bash

BASE_DIR=$(dirname $0)

BASE_PATH=$(
  cd ${BASE_DIR}
  pwd
)
source "${BASE_PATH}/common-env.sh"

if [ ! -d "INIT_LOG_PATH" ]; then
  mkdir -p $INIT_LOG_PATH
fi

source /etc/profile
java -jar ${INIT_SBIN_PATH}/datasophon-cli-cli.jar create cluster -p /data/datasophon -pwd ${PASSWORD} -a initSingleNode > ${INIT_LOG_PATH}/initSingleNode.log