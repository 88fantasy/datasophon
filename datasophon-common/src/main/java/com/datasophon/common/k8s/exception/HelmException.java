package com.datasophon.common.k8s.exception;

/**
 * @author zhanghuangbin
 */
public class HelmException extends RuntimeException{

    public HelmException() {
        super();
    }

    public HelmException(Throwable cause) {
        super(cause);
    }

    public HelmException(String message) {
        super(message);
    }

    public HelmException(String message, Throwable cause) {
        super(message, cause);
    }

    protected HelmException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
