#!/bin/bash

# example: sh init-tar.sh
# install tar
if [ $UID -ne 0 ]; then
  echo Non root user. Please run as root.
  exit 1
fi
if [ -L $0 ]; then
  BASE_DIR=$(dirname $(readlink $0))
else
  BASE_DIR=$(dirname $0)
fi
BASE_PATH=$(
  cd ${BASE_DIR}
  pwd
)
INIT_PATH=$(dirname "${BASE_PATH}")
echo "INIT_PATH: ${INIT_PATH}"
PACKAGES_PATH=${INIT_PATH}/packages
echo "PACKAGES_PATH: ${PACKAGES_PATH}"

which tar
if [ $? -eq 0 ]; then
  echo "tar installed.............................."
  exit 1
fi

echo "tar not installed.............................."
rpm -ivh ${PACKAGES_PATH}/tar-*.rpm
if [ $? -ne 0 ]; then
  echo "tar install failed"
  exit 1
fi

echo "INIT-init-tar.sh finished."
echo "Done."