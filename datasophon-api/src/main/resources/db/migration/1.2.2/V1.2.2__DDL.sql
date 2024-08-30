ALTER TABLE `t_ddh_cluster_variable` ADD `service_name` VARCHAR(128) DEFAULT '' COMMENT '服务名';
ALTER TABLE `t_ddh_frame_service` ADD COLUMN `arch` text NULL COMMENT '架构信息' AFTER `package_name`;