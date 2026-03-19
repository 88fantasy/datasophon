package com.datasophon.api.service.extrepo.ctx;

import cn.hutool.core.util.StrUtil;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.model.extrepo.K8sDdLServiceMeta;
import com.datasophon.dao.model.extrepo.VosDdLServiceMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author zhanghuangbin
 * @date 2025/11/12
 */
public class SrvDependenciesContext {


    private final Set<String> srvSet = new HashSet<>();
    private final Set<String> k8sSrvSet = new HashSet<>();

    public void addService(List<FrameServiceEntity> services) {
        services.forEach(srv -> addVosService(srv.getFrameCode(), srv.getServiceName()));
    }


    public void addVosService(String frameCode, String serviceName) {
        srvSet.add(generateKey(frameCode, serviceName));
    }

    public void addK8sService(String code, String serviceName) {
        k8sSrvSet.add(generateKey(code, serviceName));
    }

    private String generateKey(String frameCode, String serviceName) {
        return String.format("%s_%s", frameCode, serviceName);
    }

    public List<String> validVosDdlDependency(VosDdLServiceMeta srv) {
        return validDependency(srvSet, srv.getFrameCode(), srv.getName(), srv.getDependencies());
    }

    public List<String> validK8sDependency(K8sDdLServiceMeta srv) {
        return validDependency(k8sSrvSet, srv.getFrameCode(), srv.getName(), srv.getDependencies());
    }



    public List<String> validDependency(Set<String> srvSet, String code, String srvName, List<String> dependencies) {
        Set<String> lackSrv = new HashSet<>();

        Optional.of(dependencies)
                .orElse(new ArrayList<>(0))
                .forEach(dep -> {
                    if (!srvSet.contains(generateKey(code, dep))) {
                        lackSrv.add(dep);
                    }
                });
        if (!lackSrv.isEmpty()) {
            return Collections.singletonList(
                    String.format("框架%s服务%s的依赖%s不存在", code, srvName, StrUtil.join(",", lackSrv))
            );
        }
        return Collections.emptyList();
    }
}
