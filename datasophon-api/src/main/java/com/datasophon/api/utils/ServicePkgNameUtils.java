package com.datasophon.api.utils;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.datasophon.common.model.ArchInfo;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.dao.entity.FrameServiceEntity;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.Map;

/**
 * @author zhanghuangbin
 */
public class ServicePkgNameUtils {

    public static final String COMMON_ARCH = "common";

    /**
     * 从角色的 archInfoMap 中按主机架构取包信息；找不到时回退到 "common" 条目。
     * archInfoMap 为 null（旧数据 arch 列未填充）时返回 null。
     */
    public static ArchInfo getArchInfo(ServiceRoleInfo role, String arch) {
        Map<String, ArchInfo> archInfoMap = role.getArchInfoMap();
        if (archInfoMap == null) {
            return null;
        }
        ArchInfo info = archInfoMap.get(arch);
        if (info == null) {
            info = archInfoMap.get(COMMON_ARCH);
        }
        return info;
    }

    /**
     * 解析 FrameServiceEntity.arch JSON 为 arch-map。
     * arch 列为空（迁移前旧数据）时返回空 map，调用方将按架构无匹配处理。
     */
    public static Map<String, ArchInfo> getArchInfo(FrameServiceEntity frameService) {
        if (StringUtils.isBlank(frameService.getArch())) {
            return Collections.emptyMap();
        }
        return JSONObject.parseObject(frameService.getArch(), new TypeReference<Map<String, ArchInfo>>() {
        });
    }
}
