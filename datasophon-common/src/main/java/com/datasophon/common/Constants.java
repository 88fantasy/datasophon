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

package com.datasophon.common;

import com.datasophon.common.utils.PropertyUtils;

import java.util.regex.Pattern;

/**
 * Constants
 */
public final class Constants {
    
    public static final String INSTALL_PATH = PropertyUtils.getString("install.path");
    public static final String MASTER_INSTALL_HOME = PropertyUtils.getString("MASTER_INSTALL_HOME", "/data/datasophon");
    public static final String INIT_HOME = PropertyUtils.getString("INIT_HOME");
    public static final String DATA = "data";
    public static final String INSTALL_TYPE = "install_type";
    public static final String TOTAL = "total";
    
    public static final String DATASOPHON = "datasophon";
    
    public static final String HOST_MAP = "_host_map";
    public static final String COMMAND_HOST_ID = "command_host_id";
    public static final String HOST_MD5 = "_host_md5";
    public static final String ID_RSA = PropertyUtils.getString("id_rsa", "/root/.ssh/id_rsa");
    public static final String HOSTNAME = "hostname";
    public static final String CPU_ARCH = "hostname";
    
    public static final String MASTER_MANAGE_PACKAGE_PATH = INIT_HOME + "/packages";
    public static final String PACKAGES_NAME = "packages";
    public static final String UNZIP_DDH_WORKER_CMD =
            "tar -zxf " + MASTER_MANAGE_PACKAGE_PATH + "/datasophon-worker.tar.gz -C " + INSTALL_PATH;
    public static final String START_DDH_WORKER_CMD = "service datasophon-worker restart";
    
    public static final String WORKER_PACKAGE_NAME = "datasophon-worker.tar.gz";
    public static final String WORKER_SCRIPT_PATH = INSTALL_PATH + "/datasophon-worker/script/";
    public static final String WORKER_PATH = INSTALL_PATH + "/datasophon-worker";
    public static final String SHELL_SCRIPT_PATH = "/scripts";
    
    public static final String FRAMEWORK_TPL = "frameworktpl";
    public static final String SERVICE_DDL = "service_ddl.json";
    public static final String MANIFEST_DDL = "manifest.yaml";
    
    public static final String CLUSTER_ID = "cluster_id";
    public static final String MANAGED = "managed";
    public static final String JSON = "json";
    public static final String CONFIG = "_config";
    public static final String SERVICE_ROLE_HOST_MAPPING = "service_role_host_mapping";
    public static final String UNDERLINE = "_";
    public static final String DETAILS_USER_ID = "user_id";
    public static final String MASTER = "master";
    public static final String CONFIG_FILE = "_config_file";
    public static final String QUERY = "query";
    public static final String SUCCESS = "success";
    public static final String SERVICE_NAME = "service_name";
    public static final String SERVICE_ROLE_STATE = "service_role_state";
    public static final String LOCALE_LANGUAGE = "language";
    public static final String CODE = "code";
    public static final String CLUSTER_CODE = "cluster_code";
    public static final String START_DISTRIBUTE_AGENT = "start_distribute_agent";
    public static final String CHECK_WORKER_MD5_CMD =
            "md5sum " + MASTER_MANAGE_PACKAGE_PATH + "/datasophon-worker.tar.gz | awk '{print $1}'";
    public static final String CREATE_TIME = "create_time";
    public static final String SERVICE_ROLE_NAME = "service_role_name";
    public static final String FRAME_CODE_1 = "frame_code";
    public static final String UPDATE_COMMON_CMD = "sh " + INSTALL_PATH + "/datasophon-worker/script/sed_common.sh ";
    public static final String MASTER_HOST = "masterHost";
    public static final String MASTER_WEB_PORT = "server.port";
    
    public static final String HOST_COMMAND_ID = "host_command_id";
    
    public static final String CONFIG_VERSION = "config_version";
    public static final String HAS_EN = ".*[a-zA-z].*";
    public static final String ALERT_TARGET_NAME = "alert_target_name";
    public static final String USER_INFO = "userInfo";
    public static final String CUSTOM = "custom";
    
    public static final String NACOS = "nacos";
    public static final String INPUT = "input";
    public static final String SERVICE_ROLE_JMX_MAP = "service_role_jmx_port";
    public static final String MULTIPLE = "multiple";
    public static final String MULTIPLE_WITH_MAP = "multipleWithMap";
    public static final String STRING_ARRAY = "stringArray";
    public static final String NUMBER = "number";
    public static final String CLUSTER_STATE = "cluster_state";
    public static final String PATH = "path";
    
    public static final String MV_PATH = "mv_path";
    public static final String SERVICE_INSTANCE_ID = "service_instance_id";
    public static final String IS_ENABLED = "is_enabled";
    public static final String SORT_NUM = "sort_num";
    public static final String CN = "chinese";
    public static final String NAME = "name";
    public static final String QUEUE_NAME = "queue_name";
    public static final String ROLE_TYPE = "role_type";
    public static final String CLUSTER_FRAME = "cluster_frame";
    public static final String VARIABLE_NAME = "variable_name";
    public static final String SERVICE_ROLE_INSTANCE_ID = "service_role_instance_id";
    public static final String X86JDK = "OpenJDK8U-jdk_x64_linux_hotspot_8u492b09.tar.gz";
    public static final String ARMJDK = "OpenJDK8U-jdk_aarch64_linux_hotspot_8u492b09.tar.gz";
    // Temurin JDK8 tar 包实际解压出的顶层目录名，解压到 INSTALL_PATH 后
    // 软链到 JDK8_HOME_ALIAS，hadoop-env.ftl / dolphinscheduler_env.ftl 等模板
    // 才能按固定路径找到 JAVA_HOME。
    public static final String JDK8_EXTRACT_DIR_NAME = "jdk8u492-b09";
    public static final String JDK8_HOME_ALIAS = "/usr/local/jdk8";
    // JDK17：datasophon-cli-go 的 init jdk17 使用同一套 tar 包/解压目录名，
    // 供部分 K8s 相关组件按需使用。
    public static final String JDK17_X86 = "OpenJDK17U-jdk_x64_linux_hotspot_17.0.19_10.tar.gz";
    public static final String JDK17_ARM = "OpenJDK17U-jdk_aarch64_linux_hotspot_17.0.19_10.tar.gz";
    public static final String JDK17_EXTRACT_DIR_NAME = "jdk-17.0.19+10";
    public static final String JDK17_HOME_ALIAS = "/usr/local/jdk17";
    // JDK21：Datasophon Manager（Master/Worker）平台自身运行时依赖，
    // Worker 进程本身即编译为 JDK21 字节码，缺失时 Worker 无法启动。
    public static final String JDK21_X86 = "OpenJDK21U-jdk_x64_linux_hotspot_21.0.11_10.tar.gz";
    public static final String JDK21_ARM = "OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.11_10.tar.gz";
    public static final String JDK21_EXTRACT_DIR_NAME = "jdk-21.0.11+10";
    public static final String JDK21_HOME_ALIAS = "/usr/local/jdk21";
    public static final String COMMAND_STATE = "command_state";
    public static final String ROLE_GROUP_ID = "role_group_id";
    public static final String ROLE_GROUP_TYPE = "role_group_type";
    public static final String NEET_RESTART = "need_restart";
    
    public static final String CPU_ARCHITECTURE = "cpu_architecture";
    public static final String HOST_STATE = "host_state";
    public static final String FAILED = "failed";
    public static final String SERVICE_CATEGORY = "service_category";
    public static final String ROLE_GROUP_NAME = "role_group_name";
    public static final String NODE_LABEL = "node_label";
    public static final String GROUP_ID = "group_id";
    public static final String USER_ID = "user_id";
    public static final String GROUP_NAME = "group_name";
    public static final String RACK = "rack";
    public static final String SERVICE_STATE = "service_state";
    public static final String EQUAL_SIGN = "=";
    
    public static final String OS_ARCH_CMD = "arch";
    public static final String OS_VERSION_CMD = "cat /etc/os-release";
    
    public static final String DB_USERNAME = "mysql.username";
    public static final String DB_PASSWORD = "mysql.password";
    public static final String DB_NAME = "mysql.database";
    public static final String DB_IP = "mysql.ip";
    public static final String DB_PORT = "mysql.port";
    
    private Constants() {
        throw new IllegalStateException("Constants Exception");
    }
    
    public static final String USERNAME = "username";
    
    public static final String PASSWORD = "password";
    /**
     * session user
     */
    public static final String SESSION_USER = "session.user";
    
    public static final String SESSION_ID = "sessionId";
    /**
     * session timeout
     */
    public static final int SESSION_TIME_OUT = 7200;
    
    /**
     * http header
     */
    public static final String HTTP_HEADER_UNKNOWN = "unKnown";
    
    /**
     * http X-Forwarded-For
     */
    public static final String HTTP_X_FORWARDED_FOR = "X-Forwarded-For";
    
    /**
     * http X-Real-IP
     */
    public static final String HTTP_X_REAL_IP = "X-Real-IP";
    
    /**
     * UTF-8
     */
    public static final String UTF_8 = "UTF-8";
    
    /**
     * user name regex
     */
    public static final Pattern REGEX_USER_NAME = Pattern.compile("^[a-zA-Z0-9._-]{3,39}$");
    /**
     * comma ,
     */
    public static final String COMMA = ",";
    
    /**
     * dot ,
     */
    public static final String DOT = ".";
    
    /**
     * slash /
     */
    public static final String SLASH = "/";
    
    /**
     * SPACE " "
     */
    public static final String SPACE = " ";
    
    /**
     * SINGLE_SLASH /
     */
    public static final String SINGLE_SLASH = "/";
    /**
     * status
     */
    public static final String STATUS = "status";
    
    /**
     * message
     */
    public static final String MSG = "msg";
    
    public static final String REGEX_VARIABLE = "\\$\\{(.*?)\\}";
    
    /**
     * email regex
     */
    public static final Pattern REGEX_MAIL_NAME = Pattern.compile("^([a-z0-9A-Z]+[_|\\-|\\.]?)+[a-z0-9A-Z]@([a-z0-9A-Z]+(-[a-z0-9A-Z]+)?\\.)+[a-zA-Z]{2,}$");
    
    /**
     * 常量-数值100
     */
    public static final int ONE_HUNDRRD = 100;
    
    /**
     * 常量-数值200
     */
    public static final int TWO_HUNDRRD = 200;
    
    /**
     * 常量-数值10
     */
    public static final int TEN = 10;
    
    /**
     * 常量-zkserver
     */
    public static final String ZKSERVER = "zkserver";
    
    public static final String CENTER_BRACKET_LEFT = "[";
    
    public static final String CENTER_BRACKET_RIGHT = "]";
    /**
     * 常量-连接号
     */
    public static final String HYPHEN = "-";
    
    public static final String TASK_MANAGER = "taskmanager";
    public static final String JOB_MANAGER = "jobmanager";
    public static final String X86_64 = "x86_64";
    
    public static final String XML = "xml";
    public static final String PROPERTIES = "properties";
    public static final String PROPERTIES2 = "properties2";
    public static final String PROPERTIES3 = "properties3";
    
    public static final String YAML = "yaml";
    
    /**
     * os name properties
     */
    public static final String OSNAME_PROPERTIES = "os.name";
    
    /**
     * windows os name
     */
    public static final String OSNAME_WINDOWS = "Windows";
    
    /**
     * windows hosts file basedir
     */
    public static final String WINDOWS_HOST_DIR = "C:/Windows/System32/drivers";
    /**
     * root user
     */
    public static final String ROOT = "root";
    
    public static final Integer PORT_DEFAULT = PropertyUtils.getInt("ssh_port");
    
    // public static final String DISPATCHER_WORK = "dispatcher-worker.sh";
    
    public static final String GRAFANA_PATH = "/grafana";
    
    public static final Boolean NEXUS_ENABLE = PropertyUtils.getBoolean("nexus.enable");
    public static final String NEXUS_IP = PropertyUtils.getString("nexus.ip");
    public static final Integer NEXUS_PORT = PropertyUtils.getInt("nexus.port");
    public static final String NEXUS_USERNAME = PropertyUtils.getString("nexus.username");
    public static final String NEXUS_PASSWORD = PropertyUtils.getString("nexus.password");
    
    public static final String MD5 = "md5";
    
    // public static final String SECRET_KEY = PropertyUtils.getString("secret.key");
    
    /**
     * CSRF token cookie name
     */
    public static final String CSRF_TOKEN = "XSRF-TOKEN";
    /**
     * CSRF token header name
     */
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";
}
