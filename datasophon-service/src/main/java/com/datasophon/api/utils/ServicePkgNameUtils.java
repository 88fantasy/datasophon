package com.datasophon.api.utils;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.model.ArchInfo;
import com.datasophon.dao.entity.FrameServiceEntity;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author zhanghuangbin
 */
public class ServicePkgNameUtils {

    public static Map<String, ArchInfo> getArchInfo(FrameServiceEntity frameService) {
        String arch = frameService.getArch();
        if (StringUtils.isNotEmpty(arch)) {
            return JSONObject.parseObject(arch, new TypeReference<Map<String, ArchInfo>>() {
            });
        } else {
            return getDefaultArchInfo(frameService.getPackageName(), frameService.getDecompressPackageName());
        }
    }

    public static Map<String, ArchInfo> getDefaultArchInfo(String packageName, String decompressPackageName) {
        // x86与arm默认一致
        Map<String, ArchInfo> arch = new ConcurrentHashMap<>();
        ArchInfo x86 = new ArchInfo();
        x86.setPackageName(packageName);
        arch.put(ArchType.X86_64.getArch(), x86);

        ArchInfo arm = new ArchInfo();
        arm.setPackageName(packageName);
        arch.put(ArchType.AARCH64.getArch(), arm);
        return arch;
    }
}
