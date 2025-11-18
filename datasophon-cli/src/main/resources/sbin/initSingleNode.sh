
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

if [ ! -d "INIT_LOG_PATH" ]; then
  mkdir -p $INIT_LOG_PATH
fi

source /etc/profile
java -jar ${INIT_SBIN_PATH}/datasophon-cli-cli.jar create cluster -p /data/datasophon -a initSingleNode > ${INIT_LOG_PATH}/initSingleNode.log