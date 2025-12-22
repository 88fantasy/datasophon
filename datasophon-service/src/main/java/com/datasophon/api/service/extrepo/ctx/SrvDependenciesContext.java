package com.datasophon.api.service.extrepo.ctx;

import cn.hutool.core.util.StrUtil;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.model.extrepo.ServiceMeta;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author zhanghuangbin
 * @date 2025/11/12
 */
public class SrvDependenciesContext {



    private Set<String> srvSet = new HashSet<>();


    public void addService(List<FrameServiceEntity> services) {
        services.forEach(srv -> addService(srv.getFrameCode(), srv.getServiceName()));
    }

    public void addService(String frameCode, String serviceName) {
        srvSet.add(generateKey(frameCode, serviceName));
    }

    private String generateKey(String frameCode, String serviceName) {
        return String.format("%s_%s", frameCode, serviceName);
    }

    public List<String> validDependency(ServiceMeta srv) {
        Set<String> lackSrv = new HashSet<>();

        srv.getDependencies().forEach(dep -> {
            if (!srvSet.contains(generateKey(srv.getFrameCode(), dep))) {
                lackSrv.add(dep);
            }
        });
        if (!lackSrv.isEmpty()) {
            return Collections.singletonList(
                    String.format("框架%s服务%s的依赖%s不存在", srv.getFrameCode(), srv.getName(), StrUtil.join(",", lackSrv))
            );
        }
        return Collections.emptyList();
    }
}
