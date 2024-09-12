### 1、构建安装包

编译获取安装包

```shell
tar -zxvf ustream-3.2.3-SNAPSHOT.tar.gz

# 打包
md5sum ustream-server-3.2.3-SNAPSHOT.tar.gz
echo 'xxx' > ustream-3.2.3-SNAPSHOT.tar.gz.md5
```

### 2、元数据文件

api节点新增：

```shell
cd /opt/apps/datasophon-manager-1.2.0/conf/meta/SY-3.6.0
mkdir USTREAM
cd USTREAM
touch service_ddl.json
```
参数样例：
```shell
{
    "name": "loginUsername",
    "label": "登录用户",
    "description": "",
    "configType": "map", --map类型
    "required": true,    --必填
    "type": "input",
    "value": "",
    "configurableInWizard": true,
    "hidden": false,
    "defaultValue": "admin"
}
```

各worker节点新增：

```shell
cd /opt/datasophon/datasophon-worker/conf/templates
touch ustream-yaml.ftl
```

添加prometheus.ftl
```shell
  - job_name: 'ustream'
    file_sd_configs:
    - files:
      - configs/ustreamserver.json
```

### 4、ustream初始化代码
添加类`UstreamMasterHandlerStrategy`
设置`ServiceRoleStrategyContext`

### 5、重启

各节点worker重启

```shell
sh /opt/datasophon/datasophon-worker/bin/datasophon-worker.sh restart worker
```

主节点重启api

```shell
sh /opt/apps/datasophon-manager-1.2.0/bin/datasophon-api.sh restart api
```

### 4、安装服务
初始化数据库
```sql
CREATE DATABASE ustream DEFAULT CHARACTER SET utf8 DEFAULT COLLATE utf8_general_ci;
create user 'ustream'@'%' identified by 'BALB4g6hNsWJAw94E3mFJA';
grant all privileges on ustream.* to 'ustream'@'%' with grant option;
flush privileges;
```

安装服务
