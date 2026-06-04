package com.datasophon.common.function;

/**
 * @author zhanghuangbin
 */
public interface ThrowableSupplier<T> {
    
    T get() throws Exception;
}
