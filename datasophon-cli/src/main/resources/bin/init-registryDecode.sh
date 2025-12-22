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
PASSWORD=${PASSWORD}


if [ ! -d "INIT_LOG_PATH" ]; then
  mkdir -p $INIT_LOG_PATH
fi

source /etc/profile
echo "制品包解压解密, PASSWORD:${PASSWORD}"
java -jar ${INIT_SBIN_PATH}/datasophon-cli-cli.jar init registryDecode --enable --datasophonHomePath /data/datasophon --initPath /data/datasophon/datasophon-init -pp /data -cn config.tar.gz -pn packages.tar.gz -p ${PASSWORD}
