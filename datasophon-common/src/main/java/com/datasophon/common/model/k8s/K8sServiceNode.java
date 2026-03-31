package com.datasophon.common.model.k8s;

import com.datasophon.common.enums.CommandType;
import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class K8sServiceNode {

    private Integer clusterId;

    private String commandId;

    private CommandType commandType;

    private String serviceName;

    private Integer serviceInstanceId;

    private String namespace;

    private String metaFileType;

    private Integer valueId;



}
