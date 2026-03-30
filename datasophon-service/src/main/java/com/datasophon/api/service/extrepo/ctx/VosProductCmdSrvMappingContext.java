package com.datasophon.api.service.extrepo.ctx;

import com.datasophon.dao.entity.cmd.ClusterServiceCommandEntity;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandHostCommandEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author zhanghuangbin
 * @date 2025/11/13
 */
public class VosProductCmdSrvMappingContext {


    private Map<String, ClusterServiceCommandEntity> srvCmdMap;

    private Map<String, List<ClusterServiceCommandHostCommandEntity>> cmdHostMap;


    public void setSrvCmd(List<ClusterServiceCommandEntity> commandList) {
        srvCmdMap = commandList.stream().collect(Collectors.toMap(
                ClusterServiceCommandEntity::getServiceName,
                a -> a,
                (a, b) -> a
        ));
    }

    public ClusterServiceCommandEntity getCmd(String srv) {
        return srvCmdMap.get(srv);
    }

    public void setCmdHost(List<ClusterServiceCommandHostCommandEntity> hostCommandList) {
        cmdHostMap = hostCommandList.stream().collect(Collectors.groupingBy(ClusterServiceCommandHostCommandEntity::getCommandId));
    }

    public List<ClusterServiceCommandHostCommandEntity> getCmdHostList(String cmdId) {
        return cmdHostMap.getOrDefault(cmdId, new ArrayList<>());
    }




}
