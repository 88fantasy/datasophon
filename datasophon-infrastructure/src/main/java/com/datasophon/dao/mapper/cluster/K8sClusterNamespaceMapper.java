package com.datasophon.dao.mapper.cluster;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datasophon.dao.entity.cluster.K8sClusterNamespace;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author zhanghuangbin
 */
@Mapper
public interface K8sClusterNamespaceMapper extends BaseMapper<K8sClusterNamespace> {
}
