package com.datasophon.common.function;

/**
 * @author zhanghuangbin
 */
public interface ThrowableMapper<T, R> {
    
    R accept(T payload) throws Exception;
}
