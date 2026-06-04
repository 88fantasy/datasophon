package com.datasophon.common.k8s.exception;

/**
 * @author zhanghuangbin
 */
public class HelmifyException extends RuntimeException {
    
    public HelmifyException() {
        super();
    }
    
    public HelmifyException(Throwable cause) {
        super(cause);
    }
    
    public HelmifyException(String message) {
        super(message);
    }
    
    public HelmifyException(String message, Throwable cause) {
        super(message, cause);
    }
    
    protected HelmifyException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
