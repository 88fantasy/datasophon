package com.datasophon.dao.mapper.instance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datasophon.dao.entity.instance.K8sServiceInstance;
import com.datasophon.dao.vo.instance.K8sServiceInstanceVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author zhanghuangbin
 */
@Mapper
public interface K8sServiceInstanceMapper extends BaseMapper<K8sServiceInstance> {

    List<K8sServiceInstanceVO> selectInstanceList(@Param("clusterId") Integer clusterId, @Param("namespace") String namespace);

    List<K8sServiceInstanceVO> selectByIds(@Param("instanceIds") List<Integer> instanceIds);
}
