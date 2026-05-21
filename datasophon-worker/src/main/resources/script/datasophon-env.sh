export JAVA_HOME=/usr/local/jdk17
export JAVA_HOME

export KYUUBI_HOME=/data/install_datasophon/kyuubi
export SPARK_HOME=/data/install_datasophon/spark3
export PYSPARK_ALLOW_INSECURE_GATEWAY=1
export HIVE_HOME=/data/install_datasophon/hive
export KAFKA_HOME=/data/install_datasophon/kafka
export HBASE_HOME=/data/install_datasophon/hbase
export HBASE_PID_PATH_MK=/data/install_datasophon/hbase/pid
export FLINK_HOME=/data/install_datasophon/flink
export HADOOP_HOME=/data/install_datasophon/hadoop
export HADOOP_CONF_DIR=/data/install_datasophon/hadoop/etc/hadoop
export PATH=$PATH:$JAVA_HOME/bin:$SPARK_HOME/bin:$HADOOP_HOME/bin:$HIVE_HOME/bin:$FLINK_HOME/bin:$KAFKA_HOME/bin:$HBASE_HOME/bin
#export HADOOP_CLASSPATH=`hadoop classpath` #非hadoop

