package com.datasophon.api.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.storage.impl.NexusImageStorage;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.common.utils.PlaceholderUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * @author zhanghuangbin
 */
public class HelmValueUtils {


    public static Map<String, String> getExtraValues() {
        Map<String, String> map = new HashMap<>();
        map.put("nexus.repository", NexusImageStorage.newOptions().getRepository());
        return map;
    }


    public static File writeHelmValueTempFile(String values) {
        String value = PlaceholderUtils.replacePlaceholders(values, getExtraValues(), Constants.REGEX_VARIABLE);
        File tempValueFile = PathUtils.createTmpFile("sensitive/" + RandomUtil.randomString(12), "value.yaml");
        FileUtil.writeString(value, tempValueFile, StandardCharsets.UTF_8);
        return tempValueFile;
    }
}
