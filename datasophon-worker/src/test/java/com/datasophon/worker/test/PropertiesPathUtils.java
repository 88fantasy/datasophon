package com.datasophon.worker.test;

import com.datasophon.common.utils.PathUtils;

import java.io.File;

/**
 * @author zhanghuangbin
 */
public class PropertiesPathUtils {

    public static void resetPropertyFile() {
        System.setProperty("devMode", "local");
        File workspace = new File("./");
        String path = PathUtils.join(workspace.getAbsolutePath(), "../conf/common.properties").toFile().getAbsolutePath();
        System.setProperty("commonPropertiesLocation", path);
    }
}
