package com.datasophon.api.service.extrepo.vo;

import cn.hutool.core.util.StrUtil;
import com.datasophon.api.dto.extrepo.K8sProductDeployMapping;
import com.datasophon.api.service.extrepo.ctx.ServiceDAGBuilder;
import com.datasophon.dao.entity.cmd.ClusterK8sServiceCommandEntity;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;
import lombok.Data;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author zhanghuangbin
 */
@Data
public class K8sCommandNode implements ServiceDAGBuilder.Node {

    private ClusterK8sServiceCommandEntity cmd;

    private FrameK8sServiceEntity service;

    private K8sProductDeployMapping mapping;

    private Integer valueId;


    @Override
    public String getNodeName() {
        return cmd.getServiceName();
    }

    @Override
    public Set<String> getDependencies() {
        return Optional.ofNullable(service.getDependencies())
                .orElse(new ArrayList<>())
                .stream().filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
    }
}
