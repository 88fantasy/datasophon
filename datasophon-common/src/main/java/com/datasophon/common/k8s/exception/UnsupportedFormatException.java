package com.datasophon.common.k8s.exception;

/**
 * @author zhanghuangbin
 */
public class UnsupportedFormatException extends RuntimeException {
    private static final long serialVersionUID = -212885450196281161L;

    public UnsupportedFormatException(String message) {
        super(message);
    }
}
