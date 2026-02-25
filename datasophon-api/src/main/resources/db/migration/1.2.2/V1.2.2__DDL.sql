ALTER TABLE `t_ddh_cluster_variable`
    ADD `service_name` VARCHAR(128) DEFAULT '' COMMENT '服务名';
ALTER TABLE `t_ddh_frame_service`
    ADD COLUMN `arch` text NULL COMMENT '架构信息' AFTER `package_name`;

ALTER TABLE t_ddh_cluster_host
    ADD ssh_user varchar(100) NULL COMMENT 'ssh用户';
ALTER TABLE t_ddh_cluster_host
    ADD ssh_password varchar(100) NULL COMMENT 'ssh密码';
ALTER TABLE t_ddh_cluster_host
    ADD ssh_port BIGINT NULL COMMENT 'ssh端口';



