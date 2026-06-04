/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.worker.utils;

public class TaskConstants {
    
    private TaskConstants() {
        throw new IllegalStateException("Utility class");
    }
    
    public static final String YARN_APPLICATION_REGEX = "application_\\d+_\\d+";
    
    public static final String FLINK_APPLICATION_REGEX = "JobID \\w+";
    
    public static final String SETVALUE_REGEX = "[\\$#]\\{setValue\\(([^)]*)\\)}";
    
    /**
     * string false
     */
    public static final String STRING_FALSE = "false";
    
    /**
     * exit code kill
     */
    public static final int EXIT_CODE_KILL = 137;
    public static final String PID = "pid";
    
    /**
     * QUESTION ?
     */
    public static final String QUESTION = "?";
    
    /**
     * comma ,
     */
    public static final String COMMA = ",";
    
    /**
     * hyphen
     */
    public static final String HYPHEN = "-";
    
    /**
     * slash /
     */
    public static final String SLASH = "/";
    
    /**
     * COLON :
     */
    public static final String COLON = ":";
    
    /**
     * SPACE " "
     */
    public static final String SPACE = " ";
    
    /**
     * SINGLE_SLASH /
     */
    public static final String SINGLE_SLASH = "/";
    
    /**
     * pstree, get pud and sub pid
     */
    public static final String PSTREE = "pstree";
    
    public static final String RWXR_XR_X = "rwxr-xr-x";
    
    /**
     * task log info format
     */
    public static final String TASK_LOG_LOGGER_NAME = "app.TaskLogLogger";
    
    /**
     * task log logger name format
     */
    public static final String TASK_LOG_LOGGER_NAME_FORMAT = TASK_LOG_LOGGER_NAME + "-%s";
    
    /**
     * Task Logger's prefix
     */
    public static final String TASK_LOGGER_INFO_PREFIX = "TASK";
    
    /**
     * Task Logger Thread's name
     */
    public static final String TASK_APPID_LOG_FORMAT = "taskAppId";
    
    /**
     * get output log service
     */
    public static final String GET_OUTPUT_LOG_SERVICE = "-getOutputLogService";
    
    public static String createLoggerName(String serviceName, String serviceRoleName, Class<?> handler) {
        return String.format("%s-%s-%s-%s", TASK_LOG_LOGGER_NAME, serviceName, serviceRoleName, handler.getSimpleName());
        
    }
}
