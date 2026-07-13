/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api.hook;

import com.datasophon.common.enums.HookType;
import com.datasophon.common.model.HookConfig;
import com.datasophon.common.model.ServiceNode;
import com.datasophon.common.model.ServiceRoleInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceHookDispatcher {

    private final List<ServiceHook> hooks;

    private final Map<String, ServiceHook> byAction = new HashMap<>();

    @PostConstruct
    void initialize() {
        for (ServiceHook hook : hooks) {
            ServiceHook previous = byAction.putIfAbsent(hook.getType(), hook);
            if (previous != null) {
                throw new IllegalStateException("重复的 serviceHook action: " + hook.getType());
            }
        }
    }

    public void dispatch(ServiceNode node, HookType type) {
        List<HookConfig> matched = node.getMatchedServiceHooks(type);
        if (matched.isEmpty()) {
            return;
        }
        Integer clusterId = firstClusterId(node);
        for (HookConfig config : matched) {
            try {
                ServiceHookContext context = buildContext(node, clusterId, config);
                if (!isEnabled(config.getCondition(), context.toConditionMap())) {
                    continue;
                }
                ServiceHook hook = byAction.get(config.getAction());
                if (hook == null) {
                    log.warn("未知 serviceHook action: {}", config.getAction());
                    continue;
                }
                hook.invoke(context);
            } catch (Exception e) {
                log.warn("服务级 hook 执行失败 service={} action={} type={}，不影响 DAG 状态",
                        node.getServiceName(), config.getAction(), type, e);
            }
        }
    }

    private Integer firstClusterId(ServiceNode node) {
        return Stream.of(node.getMasterRoles(), node.getWorkerRoles(), node.getClientRoles())
                .filter(roles -> roles != null)
                .flatMap(List::stream)
                .map(ServiceRoleInfo::getClusterId)
                .filter(clusterId -> clusterId != null)
                .findFirst()
                .orElse(null);
    }

    private boolean isEnabled(String condition, Map<String, Object> conditionMap) {
        if (StrUtil.isBlank(condition)) {
            return true;
        }
        StandardEvaluationContext context = new StandardEvaluationContext();
        conditionMap.forEach(context::setVariable);
        context.setRootObject(conditionMap);
        Expression expression = new SpelExpressionParser().parseExpression(condition);
        return Boolean.TRUE.equals(expression.getValue(context, Boolean.class));
    }

    private ServiceHookContext buildContext(ServiceNode node, Integer clusterId, HookConfig config) {
        ServiceHookContext context = new ServiceHookContext();
        context.setServiceName(node.getServiceName());
        context.setClusterId(clusterId);
        context.setCommandType(node.getCommandType());
        context.setCommandId(node.getCommandId());
        context.setParams(config.getParams());
        return context;
    }
}
