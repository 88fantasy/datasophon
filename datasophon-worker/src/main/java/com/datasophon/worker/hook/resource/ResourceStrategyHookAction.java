package com.datasophon.worker.hook.resource;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.ServiceLoaderUtil;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.worker.hook.HookAction;
import com.datasophon.worker.hook.HookContext;
import com.datasophon.worker.strategy.resource.EmptyStrategy;
import com.datasophon.worker.strategy.resource.ResourceStrategy;
import com.datasophon.worker.utils.TaskConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 桥接旧的资源策略功能
 * @author zhanghuangbin
 */
public class ResourceStrategyHookAction implements HookAction {


    public static final Map<String, Class<? extends ResourceStrategy>> cache = new ConcurrentHashMap<>();

    static {
        List<ResourceStrategy> strategies = ServiceLoaderUtil.loadList(ResourceStrategy.class);
        for (ResourceStrategy strategy : strategies) {
            cache.put(strategy.type(), strategy.getClass());
        }
    }

    @Override
    public String getType() {
        return "resourceStrategy";
    }


    @Override
    public ExecResult invoke(HookContext context) throws Exception {
        Map<String, Object> params = context.getParams();
        String type = (String) params.get(ResourceStrategy.TYPE_KEY);
        Class<? extends ResourceStrategy> clazz = cache.getOrDefault(type, EmptyStrategy.class);
        ResourceStrategy rs = BeanUtil.toBean(params, clazz, CopyOptions.create().ignoreError());

        Logger logger = LoggerFactory.getLogger(
                TaskConstants.createLoggerName(context.getServiceName(), context.getServiceRoleName(), rs.getClass())
        );
        rs.setLogger(logger);
        rs.setFrameCode(context.getGlobalVariables().get("${__frameCode__}"));
        rs.setService(context.getServiceName());
        rs.setServiceRole(context.getServiceRoleName());
        rs.setBasePath(PkgInstallPathUtils.getInstallHome(context));
        rs.setVariables(context.getGlobalVariables());
        ExecResult exec = rs.exec();
        return exec;
    }
}
