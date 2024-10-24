#!/bin/bash

if [ $UID -ne 0 ]; then
  echo Non root user. Please run as root.
  exit 1
fi

if [ $# -ne 1 ]; then
  echo "example:sh init-ssh ip1,ip2,ip3"
  exit 1
fi

#变量
port=22
user=root

IFS=',' read -ra ips <<< "$1"
sshDir="/${user}/.ssh"

removeSSHAsk() {
  echo "begin removeSSHAsk....."
  sed -i '/^#.*UseDNS no/s/^#//g' /etc/ssh/sshd_config
  sed -i '/^#.*StrictHostKeyChecking ask/s/^#//g' /etc/ssh/ssh_config
  sed -i '/StrictHostKeyChecking ask/s/ask/no/g' /etc/ssh/ssh_config
}


keygenConfig(){
sshPath=/root/.ssh
if [ ! -r "${sshPath}/id_rsa.pub" ]; then
echo "id_rsa.pub nonexistent  creating......"
ssh-keygen -t rsa
echo "Finish ssh-keygen -t rsa."
else
echo "id_rsa.pub exists"
fi
}

configAuthorizedKeys(){
    cat ${sshDir}/id_rsa.pub >> ${sshDir}/authorized_keys
}

echo "init-ssh-gen-key..."
removeSSHAsk
keygenConfig
configAuthorizedKeys
echo "init-ssh-gen-key Done"

echo "init-ssh-copy-key..."
for ip in "${ips[@]}"
do
   echo "copy key to ${ip}"
   scp -P${port} -r $sshDir/ $user@$ip:/${user}/
done
echo "init-ssh-copy-key Done"






