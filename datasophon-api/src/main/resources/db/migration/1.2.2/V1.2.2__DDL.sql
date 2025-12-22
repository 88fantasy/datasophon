ALTER TABLE `t_ddh_cluster_variable` ADD `service_name` VARCHAR(128) DEFAULT '' COMMENT '服务名';
ALTER TABLE `t_ddh_frame_service` ADD COLUMN `arch` text NULL COMMENT '架构信息' AFTER `package_name`;

ALTER TABLE t_ddh_cluster_host ADD ssh_user varchar(100) NULL COMMENT 'ssh用户';
ALTER TABLE t_ddh_cluster_host ADD ssh_password varchar(100) NULL COMMENT 'ssh密码';
ALTER TABLE t_ddh_cluster_host ADD ssh_port BIGINT NULL COMMENT 'ssh端口';



create table t_ddh_upload_temp_file
(
    `id`           int(11) NOT NULL COMMENT 'id',
    `file_name`    varchar(255) DEFAULT NULL COMMENT '文件名',
    `content_type` varchar(255) DEFAULT NULL COMMENT 'content-type',
    `byte_cnt`     bigint(20)   DEFAULT NULL COMMENT '附件大小',
    `byte_desc`    varchar(255) DEFAULT NULL COMMENT '附件大小描述',
    `suffix`       varchar(255) DEFAULT NULL COMMENT '后缀',
    `path`         varchar(255) DEFAULT NULL COMMENT '相对于临时目录的位置',
    `create_time`  datetime     DEFAULT NULL COMMENT '创建时间',
    `status`       tinyint(1)   DEFAULT NULL COMMENT '上传状态 0表示附件未写入 1表示已经写入',
    `md5`          varchar(255) DEFAULT NULL COMMENT '文件md5',
    `upload_type`  int          DEFAULT NULL COMMENT '上传方式 0整体上传 1分片上传',
    `chunk`        int          DEFAULT NULL COMMENT '分片大小',

    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 comment 't_ddh_upload_temp_file';


create table t_ddh_upload_temp_file_chunk
(
    `id`        int(11) NOT NULL auto_increment COMMENT 'id',
    `attach_id` bigint(20)   DEFAULT NULL COMMENT '附件ID',
    `chunk_no`  int          DEFAULT NULL COMMENT '分片序号',
    `md5`       varchar(255) DEFAULT NULL COMMENT '分片MD5',


    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 comment 't_ddh_upload_temp_file_chunk';