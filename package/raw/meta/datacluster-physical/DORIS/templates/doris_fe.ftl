# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

#####################################################################
## The uppercase properties are read and exported by bin/start_fe.sh.
## To see all Frontend configurations,
## see fe/src/org/apache/doris/common/Config.java
#####################################################################

export JAVA_HOME=$JAVA_HOME17

# the output dir of stderr and stdout
LOG_DIR = ${r"${DORIS_HOME}/log"}

DATE = `date +%Y%m%d-%H%M%S`
JAVA_OPTS="-Xmx${fe_heap_size}G -XX:+UseMembar -XX:SurvivorRatio=8 -XX:MaxTenuringThreshold=7 -XX:+PrintGCDateStamps -XX:+PrintGCDetails -XX:+UseConcMarkSweepGC -XX:+UseParNewGC -XX:+CMSClassUnloadingEnabled -XX:-CMSParallelRemarkEnabled -XX:CMSInitiatingOccupancyFraction=80 -XX:SoftRefLRUPolicyMSPerMB=0 -Xloggc:$DORIS_HOME/log/fe.gc.log.$DATE"

# For jdk 9+, this JAVA_OPTS will be used as default JVM options
JAVA_OPTS_FOR_JDK_9="-Xmx${fe_heap_size}G -XX:SurvivorRatio=8 -XX:MaxTenuringThreshold=7 -XX:+CMSClassUnloadingEnabled -XX:-CMSParallelRemarkEnabled -XX:CMSInitiatingOccupancyFraction=80 -XX:SoftRefLRUPolicyMSPerMB=0 -Xlog:gc*:$DORIS_HOME/log/fe.gc.log.$DATE:time"

# For jdk 17, start_fe.sh requires this to be explicitly set, otherwise it exits 1
JAVA_OPTS_FOR_JDK_17="-Dfile.encoding=UTF-8 -Djavax.security.auth.useSubjectCredsOnly=false -Xmx${fe_heap_size}G -Xms${fe_heap_size}G -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$DORIS_HOME/log -Xlog:gc*:$DORIS_HOME/log/fe.gc.log.$DATE:time,uptime:filecount=10,filesize=50M -Darrow.enable_null_check_for_get=false --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/sun.nio.cs=ALL-UNNAMED --add-opens=java.base/sun.security.action=ALL-UNNAMED --add-opens=java.base/sun.util.calendar=ALL-UNNAMED --add-opens=java.security.jgss/sun.security.krb5=ALL-UNNAMED --add-opens=java.management/sun.management=ALL-UNNAMED --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED --add-opens=java.xml/com.sun.org.apache.xerces.internal.jaxp=ALL-UNNAMED"

##
## the lowercase properties are read by main program.
##

# INFO, WARN, ERROR, FATAL
sys_log_level = INFO

mysql_service_nio_enabled = true
edit_log_port = 9010
<#list itemList as item>
${item.name} = ${item.value}
</#list>
