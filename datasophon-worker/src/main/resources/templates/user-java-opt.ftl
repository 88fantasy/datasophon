## set java options

readonly jasypt_encryptor_password="${portalJasyptEncryptorPassword}"
readonly java_memory="${portalJavaMemoryOpt}"
export JAVA_HOME=${portalJdkHomePath}


<#noparse>
export PATH=$JAVA_HOME/bin:$PATH



if [[ -z $java_memory ]]; then
    java_memory="-Xms256M -Xmx1G -XX:MetaspaceSize=256M -XX:MaxMetaspaceSize=512M"
fi


JAVA_OPT="${java_memory}  -Dlog.level=info"

if [[ -n  $jasypt_encryptor_password ]]; then
JAVA_OPT="${JAVA_OPT} -Djasypt.encryptor.password=${jasypt_encryptor_password}"
fi

export JAVA_OPT

</#noparse>