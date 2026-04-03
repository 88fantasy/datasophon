#!/bin/bash

# example: sh init-jdk8.sh
# instal and config jdk env
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

source "${BASE_PATH}/common-env.sh"

PACKAGES_PATH=/data/packages/raw/packages
echo "PACKAGES_PATH: ${PACKAGES_PATH}"
JDK_FOLDER_PATH=/usr/local
source /etc/profile
mkdir -p ${JDK_FOLDER_PATH}
JDK_PATH_NAME="jdk-17.0.1"
BASH_PROFILE_PATH="/root/.bash_profile"
BASHRC_PATH="/root/.bashrc"
ETC_PROFILE_PATH="/etc/profile"
JDK_TAR_NAME="openjdk-17.0.1_linux-x64_bin.tar.gz"
JAVA17_HOME="${JDK_FOLDER_PATH}/${JDK_PATH_NAME}"
#JRE_HOME="${JDK_FOLDER_PATH}/${JDK_PATH_NAME}/jre"
arch=$(arch)
echo arch:$arch
if [ $arch = "aarch64" ]; then
  JDK_TAR_NAME="openjdk-17.0.1_linux-aarch64_bin.tar.gz"
fi

if [[ -d ${JAVA17_HOME} ]]; then
  echo "JDK 17 installed.............................."
else
  echo "JDK 17 not installed.............................."
  echo "JDK 17 environment already sets"
  pid="sed -i '/export JAVA17_HOME/d' /etc/profile"
  eval $pid
  pid="sed -i '/export CLASSPATH/d' /etc/profile"
  eval $pid
  pid="sed -i '/source \/etc\/profile/d' /root/.bash_profile"
  eval $pid
  pid="sed -i '/source \/etc\/profile/d' /root/.bashrc"
  eval $pid
  #pid="sed -i '/source \/etc\/profile/d' /home/hadoop/.bash_profile"
  #eval $pid
  #pid="sed -i '/source \/etc\/profile/d' /home/hadoop/.bashrc"
  #eval $pid
  echo "Prepare to Install JDK..."
  sleep 2s
  mkdir -p ${JDK_FOLDER_PATH}
  tar -zxvf ${PACKAGES_PATH}/${JDK_TAR_NAME} -C ${JDK_FOLDER_PATH}
  JAVA_SOURCE_ENV="source /etc/profile"
  echo "export JAVA17_HOME=$JAVA17_HOME" >>/etc/profile
  echo ${JAVA_SOURCE_ENV} >>~/.bash_profile
  echo ${JAVA_SOURCE_ENV} >>~/.bashrc

  echo "If you need to effect the environment variable in the current session, do it manually: "
  source ${BASH_PROFILE_PATH}
  source ${BASHRC_PATH}
  source ${ETC_PROFILE_PATH}
  #jdk2=$(grep -n "export JAVA17_HOME=.*" /home/hadoop/.bash_profile | cut -f1 -d':')
  #if [ -n "$jdk2" ]; then
  #  echo "JDK HADOOP environment exists"
  #else
    #echo ${JAVA_SOURCE_ENV} >>/home/hadoop/.bash_profile
    #echo ${JAVA_SOURCE_ENV} >>/home/hadoop/.bashrc
  #  echo "JDK HADOOP environment sets skip"
  #fi
  echo "JDK install successfully"
  source /etc/profile
fi
echo "INIT-init-jdk.sh finished."
echo "Done."
