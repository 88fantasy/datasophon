CREATE TABLE IF NOT EXISTS `t_ddh_chat_conversation` (
    `id`          INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     INT          NOT NULL                COMMENT '用户 ID',
    `title`       VARCHAR(100) NOT NULL DEFAULT ''     COMMENT '会话标题（取首条消息前20字）',
    `cluster_id`  INT          NULL                    COMMENT '关联集群 ID（可空）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 助手会话';

CREATE TABLE IF NOT EXISTS `t_ddh_chat_message` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `conversation_id` INT          NOT NULL COMMENT '所属会话 ID',
    `role`            VARCHAR(20)  NOT NULL COMMENT 'user / assistant',
    `content`         LONGTEXT     NOT NULL COMMENT '消息内容',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 助手消息';
