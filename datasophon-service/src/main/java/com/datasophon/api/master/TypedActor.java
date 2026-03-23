package com.datasophon.api.master;

import akka.actor.UntypedActor;
import com.datasophon.api.utils.SpringTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 用于特定类型消息的actor的基类
 *
 * @param <T> 消息类型
 * @author zhanghuangbin
 */
@Slf4j
public abstract class TypedActor<T> extends UntypedActor {


    private final Class<T> clazz;

    protected TypedActor() {
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
        } else if (TypedActor.class == parent) {
            return child;
        } else {
            return findParameterizedTypeReferenceSubclass(parent);
        }
    }

    @Override
    public void preStart() throws Exception {
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


    protected <E> E getBean(Class<E> clazz) {
        return SpringTool.getApplicationContext().getBean(clazz);
    }
}
