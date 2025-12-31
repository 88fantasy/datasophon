package com.datasophon.worker.utils;

import com.datasophon.common.command.BaseCommand;
import org.apache.commons.lang3.StringUtils;

/**
 * @author zhanghuangbin
 */
public class SoftLinkUtils {

    public static String getLinkDirName(BaseCommand cmd) {
        return StringUtils.lowerCase(cmd.getServiceName());
    }
}
