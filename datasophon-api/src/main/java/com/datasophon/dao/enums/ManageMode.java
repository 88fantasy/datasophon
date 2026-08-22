package com.datasophon.dao.enums;

/**
 * 集群管理模式。
 *
 * <p>MANAGED：由 Datasophon 安装并管理全生命周期。
 * <p>IMPORTED：接管已存在的外部集群，只读监控，禁止下发任何变更。
 */
public enum ManageMode {

    MANAGED,
    IMPORTED
}
