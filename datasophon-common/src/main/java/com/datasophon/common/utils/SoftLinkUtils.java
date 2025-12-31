package com.datasophon.common.utils;

import com.datasophon.common.command.BaseCommand;
import com.datasophon.common.model.ServiceInfo;
import org.apache.commons.lang3.StringUtils;

/**
 * @author zhanghuangbin
 */
public class SoftLinkUtils {

    public static String getLinkDirName(BaseCommand cmd) {
        return StringUtils.lowerCase(cmd.getServiceName());
    }


    public static String getLinkDirName(ServiceInfo serviceInfo) {
        BaseCommand cmd = new BaseCommand();
        cmd.setServiceName(serviceInfo.getName());
        cmd.setPackageName(serviceInfo.getPackageName());
        return getLinkDirName(cmd);
    }
}
