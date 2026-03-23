#! /bin/bash

## 设置一些常见的变量

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

INIT_PATH="$(cd "${SCRIPT_DIR}"/.. && pwd )"
INIT_LOG_PATH=${INIT_PATH}/logs
INIT_BIN_PATH=${INIT_PATH}/bin
INIT_SBIN_PATH=${INIT_PATH}/sbin
DDH_HOME="$(dirname "${INIT_PATH}")"

echo "INIT_PATH is ${INIT_PATH}"
echo "INIT_LOG_PATH is ${INIT_LOG_PATH}"
echo "INIT_BIN_PATH is ${INIT_BIN_PATH}"
echo "INIT_SBIN_PATH is ${INIT_SBIN_PATH}"
echo "DDH_HOME is ${DDH_HOME}"

export INIT_PATH
export INIT_LOG_PATH
export INIT_BIN_PATH
export INIT_SBIN_PATH
export DDH_HOME