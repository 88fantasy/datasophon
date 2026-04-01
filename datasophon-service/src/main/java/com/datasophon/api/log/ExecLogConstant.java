package com.datasophon.api.log;

/**
 * @author zhanghuangbin
 */
public class ExecLogConstant {

    /**
     * task log info format
     */
    public static final String TASK_LOG_LOGGER_NAME = "app.K8sExecLogLogger";

    public static final String LOGGER_FILE_PREFIX = "datasophon-k8s-";

    public static String createLoggerName(String serviceName, String namespace, Class<?> handler) {
        return String.format("%s-%s-%s-%s", TASK_LOG_LOGGER_NAME, serviceName, namespace, handler.getSimpleName());

    }
}
