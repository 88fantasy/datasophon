package com.datasophon.dao.mapper.cluster;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author zhanghuangbin
 */
@Mapper
public interface K8sClusterConfigMapper extends BaseMapper<K8sClusterConfig> {
}
