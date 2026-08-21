package com.datasophon.common.k8s.util;

import com.datasophon.common.k8s.vo.k8s.K8sNode;

/** K8s Node 响应中的地址读取工具。 */
public final class K8sNodeUtils {

    private K8sNodeUtils() {
    }

    public static String findAddress(K8sNode.NodeStatus status, String type) {
        if (status == null || status.getAddresses() == null) {
            return null;
        }
        return status.getAddresses().stream()
                .filter(address -> type.equals(address.getType()))
                .map(K8sNode.NodeAddress::getAddress)
                .findFirst()
                .orElse(null);
    }
}
