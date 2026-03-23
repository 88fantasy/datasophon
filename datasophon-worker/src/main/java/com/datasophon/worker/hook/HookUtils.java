package com.datasophon.worker.hook;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.ServiceLoaderUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.common.command.ServiceRoleResource;
import com.datasophon.common.enums.HookType;
import com.datasophon.common.model.HookConfig;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import org.slf4j.Logger;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author zhanghuangbin
 */
public class HookUtils {

    private static final Map<String, Class<? extends HookAction>> HOOK_MAP = new HashMap<>();

    static {
        List<? extends HookAction> strategies = ServiceLoaderUtil.loadList(HookAction.class);
        for (HookAction strategy : strategies) {
            HOOK_MAP.put(strategy.getType(), strategy.getClass());
        }
    }

    public static List<HookConfig> getMatchedHooks(List<HookConfig> hooks, HookType type) {
        if (CollectionUtil.isEmpty(hooks)) {
            return new ArrayList<>(0);
        }
        return hooks.stream().filter(hook -> type.equals(hook.getType())).collect(Collectors.toList());
    }

    public static boolean isHookEnable(String condition, Map<String, Object> params) {
        if (StrUtil.isBlank(condition)) {
            return true;
        }
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        params.forEach(ctx::setVariable);
        ctx.setRootObject(params);
        SpelExpressionParser parser = new SpelExpressionParser();
        Expression expr = parser.parseExpression(condition);
        return Boolean.TRUE.equals(expr.getValue(ctx, Boolean.class));
    }

    public static HookContext createContext(HookConfig config, ServiceRoleResource resource, Map<String, String> global) {
        HookContext context = BeanUtil.toBean(config, HookContext.class);
        context.setServiceName(resource.getServiceName());
        context.setPackageName(resource.getPackageName());
        context.setDecompressPackageName(resource.getDecompressPackageName());
        context.setServiceRoleName(resource.getServiceRoleName());
        context.setGlobalVariables(global == null ? new HashMap<>() : global);
        context.setPath(PkgInstallPathUtils.getInstallHome(resource));
        return context;
    }

    public static ExecResult invokeHook(HookConfig cfg, HookContext ctx) throws Exception {
        Class<? extends HookAction> hookClass = HOOK_MAP.get(cfg.getAction());
        if (hookClass == null) {
            throw new IllegalStateException(String.format("unknown hook action %s", cfg.getAction()));
        }
        HookAction hook = ReflectUtil.newInstance(hookClass);
        return hook.invoke(ctx);
    }
}
