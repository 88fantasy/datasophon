alter table `t_ddh_cluster_info`
    add column `arch_type` varchar(20) comment '集群架构，物理机:physical K8S集群:k8s';

create table `t_ddh_k8s_cluster_config`
(

    `id`          int(11)       NOT NULL auto_increment COMMENT 'id',
    `cluster_id`  int(11)       not null comment '集群id',
    `type`        varchar(100)  NOT null comment '连接集群方式, config_file: config配置文件, token:使用token方式',
    `server_host` varchar(100)  null comment 'k8s主机名称，type=token有效',
    `server_cert` text          null comment 'k8s证书, type=token有效',
    `token`       varchar(1000) null comment 'serviceAccount的token, type=token有效',
    `username`    varchar(50)   null comment '用户名, type=password有效',
    `password`    varchar(50)   null comment '密码, type=password有效',
    `kube_config` text          NOT NULL comment '配置文件内容',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 comment 'k8s集群连接信息';


create table `t_ddh_frame_k8s_service`
(
    `id`                int(11) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `frame_id`          int(11)       DEFAULT NULL COMMENT '版本id',
    `service_name`      varchar(32)   DEFAULT NULL COMMENT '服务名称',
    `service_version`   varchar(32)   DEFAULT NULL COMMENT '服务版本',
    `service_desc`      varchar(1024) DEFAULT NULL COMMENT '服务描述',
    `dependencies`      varchar(255)  DEFAULT NULL COMMENT '服务依赖',
    `artifact`          text          DEFAULT NULL COMMENT '制品信息',
    `support_artifacts` varchar(10)   DEFAULT NULL COMMENT '支持的制品类型',
    `manifest_json`     text          DEFAULT NULL COMMENT '定义的内容',
    `manifest_json_md5` text          DEFAULT NULL COMMENT '定义md5',
    `type` varchar(50) null comment '服务分类,ENVIRONMENT=基础环境, MIDDLEWARE=中间件, APPLICATION=应用',
    `runtime` varchar(100) null comment '运行时的values.yaml位置',

    PRIMARY KEY (`id`)
) DEFAULT CHARSET=utf8mb4 COMMENT = 'K8s集群框架定义';



create table `t_ddh_k8s_cluster_namespace`
(
    `id`        int(11)      NOT NULL AUTO_INCREMENT COMMENT 'id',
    `cluster_id` int(11)      DEFAULT NULL COMMENT '集群 ID',
    `state`     tinyint(4)   DEFAULT NULL COMMENT '管理状态 -1未知状态(即vos之前管理过这个数据，但是，后来找不到了) 0->namespace的状态为inactive 1namespace的状态为active',
    `namespace` varchar(100) DEFAULT NULL COMMENT '名空间的信息',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = 'k8s 集群名空间信息';

create table `t_ddh_k8s_service_instance`
(
    `id`           int(11)      NOT NULL AUTO_INCREMENT COMMENT 'id',
    `cluster_id`   int(11)      DEFAULT NULL COMMENT '集群',
    `namespace_id` int(11)      DEFAULT NULL COMMENT '名空间 ID',
    `service_id`   int(11)      DEFAULT NULL COMMENT '服务 ID',
    `state`        tinyint(4)   DEFAULT NULL COMMENT '管理状态 0初始化 1成功 2失败',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = 'k8s 服务实例信息';

create table `t_ddh_k8s_service_instance_values`
(
    `id`           int(11)      NOT NULL AUTO_INCREMENT COMMENT 'id',
    `cluster_id`   int(11)      DEFAULT NULL COMMENT '集群',
    `namespace_id` int(11)      DEFAULT NULL COMMENT '名空间 ID',
    `service_id`   int(11)      DEFAULT NULL COMMENT '服务 ID',
    `instance_id`  int(11)      DEFAULT NULL COMMENT '实例 ID',
    `values`       text         DEFAULT NULL COMMENT 'yaml 的文本',
    `version`      int          DEFAULT 1 COMMENT '版本',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = 'k8s 服务实例变量信息';
