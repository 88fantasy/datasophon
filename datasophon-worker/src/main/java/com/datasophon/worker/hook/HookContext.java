package com.datasophon.worker.hook;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ReflectUtil;
import com.datasophon.common.command.ServiceRoleResource;
import com.datasophon.common.enums.HookType;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zhanghuangbin
 */
@Data
public class HookContext implements ServiceRoleResource {

    private String serviceName;

    private String serviceRoleName;

    private String packageName;

    private String decompressPackageName;

    private HookType type;

    private String action;

    private String path;

    private Map<String, Object> params;

    private Map<String, String> globalVariables;

    public <T> T getParamsAs(Class<T> clazz) {
        if (params == null) {
            return ReflectUtil.newInstance(clazz);
        }
        return BeanUtil.toBean(params, clazz);
    }

    public Map<String, Object> getAllInfoAsMap() {
        Map<String, Object> result = new HashMap<>(globalVariables);
        result.put("_type", getType());
        result.put("_action", getAction());
        result.put("_path", path);
        result.put("serviceName", serviceName);
        result.put("serviceRoleName", serviceRoleName);
        result.put("packageName", packageName);
        result.put("decompressPackageName", decompressPackageName);
        result.putAll(getParams());
        return result;
    }


}
