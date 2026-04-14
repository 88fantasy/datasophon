package com.datasophon.api.service.log;

import com.datasophon.api.dto.log.K8sRuntimeEventQueryDTO;
import com.datasophon.api.dto.log.K8sRuntimeLogQueryDTO;
import com.datasophon.api.vo.k8s.K8sEventInfo;

import java.util.List;

/**
 * @author zhanghuangbin
 */
public interface K8sProductService {

    String getK8sExecLog(String commandId, int rows);

    String getK8sRuntimeLog(K8sRuntimeLogQueryDTO dto);

    List<K8sEventInfo> getK8sEvents(K8sRuntimeEventQueryDTO query);
}
