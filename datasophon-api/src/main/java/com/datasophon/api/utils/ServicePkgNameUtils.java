package com.datasophon.api.utils;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.datasophon.common.model.ArchInfo;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.dao.entity.FrameServiceEntity;

import java.util.Map;

/**
 * @author zhanghuangbin
 */
public class ServicePkgNameUtils {

    public static final String COMMON_ARCH = "common";

    /**
     * 从角色的 archInfoMap 中按主机架构取包信息；找不到时回退到 "common" 条目。
     */
    public static ArchInfo getArchInfo(ServiceRoleInfo role, String arch) {
        Map<String, ArchInfo> archInfoMap = role.getArchInfoMap();
        ArchInfo info = archInfoMap.get(arch);
        if (info == null) {
            info = archInfoMap.get(COMMON_ARCH);
        }
        return info;
    }

    /**
     * 解析 FrameServiceEntity.arch JSON 为 arch-map。
     * arch 字段自 service_ddl.json 统一强制存在，不再有 null 兜底逻辑。
     */
    public static Map<String, ArchInfo> getArchInfo(FrameServiceEntity frameService) {
        return JSONObject.parseObject(frameService.getArch(), new TypeReference<Map<String, ArchInfo>>() {
        });
    }
}
