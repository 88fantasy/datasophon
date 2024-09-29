package com.datasophon.cli.util;

import com.datasophon.cli.base.CliConstants;
import com.datasophon.common.Constants;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import cn.hutool.core.util.RuntimeUtil;

public final class CliUtil {
    
    public static boolean checkAndInstall(String component, OsType os, String dir, Map<String, Map<OsType, String>> packages) {
        System.out.printf("checking %s...%n", component);
        String checkStr = CliConstants.CHECK_PREFIX + component;
        List<String> checkResult = RuntimeUtil.execForLines(checkStr);
        if (checkResult.isEmpty()) {
            if (!packages.containsKey(component) && !packages.get(component).containsKey(os)) {
                System.out.printf("%s is undefined...%n", component);
                return false;
            }
            String packageFilePath = dir + Constants.SLASH + packages.get(component).get(os);
            System.out.printf("%s is not installed... going to install: %s %n", component, packageFilePath);
            ExecResult result = ShellUtils.execWithStatus(dir, Collections.singletonList(String.format("rpm -ivh %s", packageFilePath)), 180);
            if (result.getExecResult()) {
                checkResult = RuntimeUtil.execForLines(checkStr);
                if (!checkResult.isEmpty()) {
                    System.out.printf("%s install successfully. %n", component);
                } else {
                    System.out.printf("%s install failed, stop. %n", component);
                    return false;
                }
            }
        }
        System.out.printf("%s done...%n", component);
        return true;
    }
}
