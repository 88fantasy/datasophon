#!/bin/bash


BASE_DIR=$(dirname $0)

BASE_PATH=$(
  cd ${BASE_DIR}/..
  pwd
)

source "${BASE_PATH}/bin/common-env.sh"

if [ ! -d "INIT_LOG_PATH" ]; then
  mkdir -p $INIT_LOG_PATH
fi

source /etc/profile

${JAVA_HOME}/bin/java -jar ${INIT_SBIN_PATH}/datasophon-cli-cli.jar init mysql --password "MjRm>Tk#ZjU3N)z6M==" --packagePath /data/datasophon/datasophon-init/packages -in /data/install_datasophon --x86Tar mysql-8.0.28-1.el8.x86_64.rpm-bundle.tar --aarch64Tar mysql-8.0.28-1.el8.aarch64.rpm-bundle.tar --mysqlPort 3306