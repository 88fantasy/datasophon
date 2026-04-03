package com.datasophon.common.k8s.exception;

/**
 * Docker 命令执行异常
 */
public class DockerException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DockerException(String message) {
        super(message);
    }

    public DockerException(String message, Throwable cause) {
        super(message, cause);
    }
}
