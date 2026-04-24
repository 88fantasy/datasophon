#!/bin/bash


BASE_DIR=$(dirname $0)

BASE_PATH=$(
  cd ${BASE_DIR}
  pwd
)

source "${BASE_PATH}/bin/common-env.sh"

if [ ! -d "INIT_LOG_PATH" ]; then
  mkdir -p $INIT_LOG_PATH
fi

source /etc/profile

${JAVA_HOME}/bin/java -jar ${INIT_SBIN_PATH}/datasophon-cli-cli.jar init registryUpload --enableRegistry --registryPath=/data --webHost 172.30.122.13 --webPort 8091 --username admin --password u4Gkp19TRcKKlTCLNA1pyA== -c /data/datasophon/datasophon-init/config/cluster-sample.yml -cpwd yii3D0Rc1RZBDJugWCBOcA==