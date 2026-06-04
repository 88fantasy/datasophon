### 1、构建安装包

下载安装包
[https://github.com/apache/incubator-amoro/releases/download/v0.6.1/amoro-0.6.1-bin.zip]

```shell
unzip amoro-0.6.1-bin.zip 
cd amoro-0.6.1
# 复制mysql驱动
cp mysql-connector-java-8.0.16.jar lib/
# 打包
tar czvf amoro-0.6.1.tar.gz amoro-0.6.1
md5sum amoro-0.6.1.tar.gz
echo 'xxx' > amoro-0.6.1.tar.gz.tar.gz.md5
```

### 2、元数据文件

api节点新增：

```shell
cd /opt/apps/datasophon-manager-1.2.0/conf/meta/SY-3.6.0
mkdir AMORO
cd AMORO
touch service_ddl.json
```

各worker节点新增：

```shell
cd /data/install_datasophon/datasophon-worker/conf/templates
touch amoro-config-yaml.ftl
```

添加prometheus.ftl

```shell
- job_name: 'amoro'
  file_sd_configs:
  - files:
  - configs/amoro.json
```

### 4、amoro初始化代码

无，数据库表自动初始化

### 5、重启

各节点worker重启

```shell
sh /data/install_datasophon/datasophon-worker/bin/datasophon-worker.sh restart worker
```

主节点重启api

```shell
sh /opt/apps/datasophon-manager-1.2.0/bin/datasophon-api.sh restart api
```

### 4、安装服务

配置host

```shell
xx.xx.xx.xx mysql-node-1 kyuubiServer
```

初始化数据库

```sql
-- 创建amoro数据库
CREATE DATABASE ustream DEFAULT CHARACTER SET utf8 DEFAULT COLLATE utf8_general_ci;
--建议生产环境赋值amoro用户（使用root请忽略）
create user 'amoro'@'%' identified by 'BALB4g6hNsWJAw94E3mFJA';
grant all privileges on ustream.* to 'amoro'@'%' with grant option;
flush privileges;
```

安装服务
