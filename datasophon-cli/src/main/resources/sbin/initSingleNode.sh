
#!/bin/bash

BASE_DIR=$(dirname $0)

BASE_PATH=$(
  cd ${BASE_DIR}
  pwd
)
echo "Bash Path: ${BASE_PATH}"
INIT_PATH=$(dirname "${BASE_PATH}")
INIT_LOG_PATH=${INIT_PATH}/logs

if [ ! -d "INIT_LOG_PATH" ]; then
  mkdir -p $INIT_LOG_PATH
fi

source /etc/profile
java -jar datasophon-cli-cli.jar create cluster -p /data/datasophon -a initSingleNode > ${INIT_LOG_PATH}/cliSingle.log