package com.datasophon.api.strategy;

import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;

import java.util.Map;

/**
 * 客户端服务角色策略接口
 * 比如 Spark Flink 等客户端服务, 不需要检查服务角色状态
 **/
public interface ClientServiceRoleStrategy extends ServiceRoleStrategy {

    @Override
    default void handlerServiceRoleCheck(ClusterServiceRoleInstanceEntity roleInstanceEntity, Map<String, ClusterServiceRoleInstanceEntity> map) {
        // 客户端不启动, 因此不需要检查服务角色状态

    }
}
