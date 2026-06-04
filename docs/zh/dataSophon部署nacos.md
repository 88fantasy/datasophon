### 1、构建安装包

下载安装包

把安装包放在/data/datasophon/package/packages目录下

```shell
# 解压安装包 解压后的目录为nacos
unzip nacos-server-2.4.2.1.tar.gz
# 修改目录名为nacos-server-2.4.2.1
mv nacos nacos-server-2.4.2.1
# 压缩安装包
tar czvf nacos-server-2.4.2.1.tar.gz nacos-server-2.4.2.1
md5sum nacos-server-2.4.2.1.tar.gz
echo 'xxx' > acos-server-2.4.2.1.tar.gz.md5
```

### 2、元数据文件

api节点新增：

```shell
cd /opt/apps/datasophon-manager-1.2.0/conf/meta/SY-3.6.0
mkdir NACOS
cd NACOS
touch service_ddl.json

#增加脚本（如有）
cd /opt/apps/datasophon-manager-1.2.0/conf/meta/SY-3.6.0/NACOS
mkdir scripts
touch control_nacos.sh
```

各worker节点新增：

```shell
cd /data/install_datasophon/datasophon-worker/conf/templates
touch nacos-server-master.ftl
```

添加prometheus.ftl

```shell
- job_name: 'nacos'
  file_sd_configs:
  - files:
  - configs/nacos.json
```

### 4、nacos数据库表初始化代码

添加类`NacosMasterHandlerStrategy`
设置`ServiceRoleStrategyContext` 增加

```shell
map.put("NacosServer", new NacosMasterHandlerStrategy("NACOS", "NacosServer"));
```

### 5、重启

各节点worker重启

```shell
sh /data/install_datasophon/datasophon-worker/bin/datasophon-worker.sh restart worker
```

主节点重启api

```shell
sh /opt/apps/datasophon-manager-1.2.0/bin/datasophon-api.sh restart api
```

### 6、安装服务

配置host

```shell
xx.xx.xx.xx mysql-node-1 kyuubiServer
```

初始化数据库

```sql
-- 创建nacos数据库
CREATE DATABASE nacos DEFAULT CHARACTER SET utf8 DEFAULT COLLATE utf8_general_ci;
--建议生产环境赋值nacos用户（使用root请忽略）
create user 'nacos'@'%' identified by 'BALB4g6hNsWJAw94E3mFJA';
grant all privileges on nacos.* to 'nacos'@'%' with grant option;
flush privileges;
      
-- 初始化nacos数据库
sh /data/install_datasophon/datasophon-worker/bin/init_nacos_db.sh
```

安装服务
