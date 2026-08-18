package com.datasophon.dao.enums.k8s;

/**
 * K8s 服务实例来源。
 *
 * <p>INSTALLED：由 Datasophon 安装 DAG 产生。
 * <p>IMPORTED：接管扫描发现并登记，禁止任何写操作。
 */
public enum InstanceSource {

    INSTALLED,
    IMPORTED
}
