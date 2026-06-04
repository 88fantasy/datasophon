package com.datasophon.common.k8s.exception;

/**
 * @author zhanghuangbin
 */
public class KubectlException extends RuntimeException {
    
    public KubectlException() {
        super();
    }
    
    public KubectlException(Throwable cause) {
        super(cause);
    }
    
    public KubectlException(String message) {
        super(message);
    }
    
    public KubectlException(String message, Throwable cause) {
        super(message, cause);
    }
    
    protected KubectlException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
