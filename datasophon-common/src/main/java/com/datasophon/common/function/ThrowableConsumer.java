package com.datasophon.common.function;

/**
 * @author zhanghuangbin
 */
public interface ThrowableConsumer<T> {
    
    void accept(T payload) throws Exception;
}
