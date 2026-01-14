package com.datasophon.api.dag;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author zhanghuangbin
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CancelException extends RuntimeException {

    private String nodeId;

    public CancelException(String message) {
        super(message);
    }

    public CancelException(Throwable cause) {
        super(cause);
    }

    public CancelException(String message, Throwable cause) {
        super(message, cause);
    }

    public CancelException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
