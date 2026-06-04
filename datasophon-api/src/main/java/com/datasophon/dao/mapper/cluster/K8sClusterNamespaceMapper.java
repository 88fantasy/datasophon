package com.datasophon.dao.mapper.cluster;

import com.datasophon.dao.entity.cluster.K8sClusterNamespace;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * @author zhanghuangbin
 */
@Mapper
public interface K8sClusterNamespaceMapper extends BaseMapper<K8sClusterNamespace> {
}
