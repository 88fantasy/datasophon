package com.datasophon.common.utils;

import com.datasophon.common.Constants;
import com.datasophon.common.command.ServiceRoleResource;

import org.apache.commons.lang3.StringUtils;

/**
 * @author zhanghuangbin
 */
public class PkgInstallPathUtils {
    
    public static String getLinkDirName(ServiceRoleResource resource) {
        return StringUtils.lowerCase(resource.getServiceName());
    }
    
    public static String getInstallHomeName(ServiceRoleResource resource) {
        return resource.getDecompressPackageName();
    }
    
    /**
     * 获取软件安装包路径
     * @param resource
     * @return
     */
    public static String getInstallHome(ServiceRoleResource resource) {
        return Constants.INSTALL_PATH + Constants.SLASH + getInstallHomeName(resource);
    }
    
    /**
     * 获取软件对外路径
     * @param resource
     * @return
     */
    public static String getInstallUniHome(ServiceRoleResource resource) {
        return Constants.INSTALL_PATH + Constants.SLASH + getLinkDirName(resource);
    }
    
    /**
     * 服务角色安装目录的key
     * @param resource
     * @return
     */
    public static String getRoleInstallHomeKey(ServiceRoleResource resource) {
        return String.format("${%s.%s.INSTALL_PATH}", resource.getServiceName(), resource.getServiceRoleName());
    }
    
    /**
     * 软件安装目录的key
     * @param resource
     * @return
     */
    public static String getInstallHomeKey(ServiceRoleResource resource) {
        return String.format("${ROOT.%s.INSTALL_PATH}", resource.getServiceName());
    }
}
