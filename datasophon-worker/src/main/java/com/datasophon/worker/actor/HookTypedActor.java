package com.datasophon.worker.actor;

import akka.actor.UntypedActor;
import com.datasophon.common.Constants;
import com.datasophon.common.command.ServiceRoleResource;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.enums.HookType;
import com.datasophon.common.function.ThrowableSupplier;
import com.datasophon.common.model.HookConfig;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.worker.hook.HookContext;
import com.datasophon.worker.hook.HookUtils;
import com.datasophon.worker.utils.TaskConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * @author zhanghuangbin
 */
@Slf4j
public abstract class HookTypedActor<T> extends UntypedActor {


    private final Class<T> clazz;

    protected HookTypedActor() {
        Class<?> parameterizedTypeReferenceSubclass = findParameterizedTypeReferenceSubclass(getClass());
        Type type = parameterizedTypeReferenceSubclass.getGenericSuperclass();
        Assert.isInstanceOf(ParameterizedType.class, type, "Type must be a parameterized type");
        ParameterizedType parameterizedType = (ParameterizedType) type;
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        Assert.isTrue(actualTypeArguments.length == 1, "Number of type arguments must be 1");
        this.clazz = (Class) actualTypeArguments[0];
    }

    private static Class<?> findParameterizedTypeReferenceSubclass(Class<?> child) {
        Class<?> parent = child.getSuperclass();
        if (Object.class == parent) {
            throw new IllegalStateException("Expected ParameterizedTypeReference superclass");
        } else if (HookTypedActor.class == parent) {
            return child;
        } else {
            return findParameterizedTypeReferenceSubclass(parent);
        }
    }

    @Override
    public void preStart() {
        log.info("{} service actor start before handle message", getSelf().path().toString());
    }

    @Override
    public void postStop() {
        log.info("{} service actor stopped after handle message", getSelf().path().toString());
    }


    @Override
    public void onReceive(Object message) throws Throwable {
        try {
            boolean match = message != null && clazz.isAssignableFrom(message.getClass());
            if (match) {
                doOnReceive((T) message);
            } else {
                unhandled(message);
            }
        } catch (Throwable throwable) {
            onError(message, throwable);
        }
    }


    protected abstract void doOnReceive(T message) throws Throwable;


    protected void onError(Object message, Throwable throwable) throws Throwable {
        log.error("{} receive messageType: {}, but handle fail, ", this.getClass().getSimpleName(), message == null ? "null" : message.getClass().getSimpleName(), throwable);
        throw throwable;
    }

    protected void doWithTellResult(CommandType commandType, ServiceRoleResource resource, ThrowableSupplier<ExecResult> task) {
        Logger logger = LoggerFactory.getLogger(TaskConstants.createLoggerName(resource.getServiceName(), resource.getServiceRoleName(), StartServiceActor.class));
        ExecResult result = null;
        try {
            result = task.get();
        } catch (Throwable throwable) {
            String hint = commandType == null ? "处理" : commandType.getCommandName(Constants.CN);
            logger.error("{}服务{} {}失败，原因: {}", hint, resource.getServiceName(), resource.getServiceRoleName(), throwable.getMessage(), throwable);
            result = ExecResult.error(String.format("%s服务%s %s失败，原因: %s", hint, resource.getServiceName(), resource.getServiceRoleName(), throwable.getMessage()));
        } finally {
            getSender().tell(result, getSelf());
        }
    }

    protected ExecResult invokeHook(List<HookConfig> hooks, HookType type, ServiceRoleResource resource, Map<String, String> globalVariables) {
        ExecResult result = ExecResult.success();
        List<HookConfig> hookList = HookUtils.getMatchedHooks(hooks, type);

        int i = -1;
        try {

            Logger logger = LoggerFactory.getLogger(TaskConstants.createLoggerName(resource.getServiceName(), resource.getServiceRoleName(), HookTypedActor.class));
            for (i = 0; i < hookList.size(); i++) {
                HookConfig hook = hookList.get(i);
                HookContext ctx = HookUtils.createContext(hook, resource, globalVariables);
                if (HookUtils.isHookEnable(hook.getCondition(), ctx.getAllInfoAsMap())) {
                    logger.info("开始执行服务{} {}第{}个Hook，类型: {}, 动作：{}", resource.getServiceName(), resource.getServiceRoleName(),
                            hook.getType(), i, hook.getAction());
                    log.info("{}.{} invoke {} hook, index:{}, action: {}", resource.getServiceName(), resource.getServiceRoleName(),
                            hook.getType(), i, hook.getAction());
                    result = HookUtils.invokeHook(hook, ctx);
                    log.info("{}.{} invoke {} hook {}, index:{}, action: {}", resource.getServiceName(), resource.getServiceRoleName(),
                            hook.getType(), result.isSuccess() ? "success" : "fail", i, hook.getAction());

                    logger.info("执行服务{}.{} 第{}个Hook {}，信息: {}", resource.getServiceName(), resource.getServiceRoleName(), i, result.isSuccess() ? "成功" : "失败", result.getExecOut());
                    if (!result.isSuccess()) {
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            log.error("invoke {} hook(index:{}) fail, ", type, i, ex);
            result = ExecResult.error(ex.getMessage());
        }
        return result;
    }

    @SafeVarargs
    protected final ExecResult invokeFunctions(ThrowableSupplier<ExecResult>... actions) throws Exception {
        ExecResult result = ExecResult.error("no task called");
        for (ThrowableSupplier<ExecResult> supplier : actions) {
            result = supplier.get();
            if (!result.isSuccess()) {
                return result;
            }
        }
        return result;
    }
}
