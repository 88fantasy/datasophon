package com.datasophon.api.load;

import static com.datasophon.api.load.Application.getProperty;

import com.datasophon.common.Constants;
import com.datasophon.common.utils.PropertyUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 集群全局变量缓存。
 *
 * <p>线程安全策略：内部以 clusterId 为粒度持有 live map；所有写操作
 * （{@link #put} / {@link #putValue}）对同一 live map 加锁互斥，
 * {@link #getVariables} 在锁内返回不可变快照，避免 live 引用逃逸到
 * gRPC 序列化等读取路径导致 CME / 半填充脏读。</p>
 */
public class GlobalVariables {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalVariables.class);
    
    public static final String ROOT = "ROOT";
    
    public static final String HOST = "__host__";
    public static final String NAMESPACE = "__namespace__";
    
    public static final String HOST_IP = "__hostIp__";
    
    public static final String PORT = "__port__";
    
    public static final String WEB_PORT = "__webPort__";
    
    public static final String CLUSTER_CODE = "__frameCode__";
    
    public static final String INSTALL_PATH = "INSTALL_PATH";
    // cluster variable
    // notes: 必须保证clusterId对应的map，始终都同一个对象
    // @see ProcessUtils#generateClusterVariable的说明
    private static final Map<Integer, Map<String, String>> clusterVariablesMap = new ConcurrentHashMap<>();
    
    public static void put(Integer clusterId, Map<String, String> value) {
        Map<String, String> vars = clusterVariablesMap.computeIfAbsent(clusterId, key -> new ConcurrentHashMap<>());
        synchronized (vars) {
            vars.clear();
            vars.putAll(value);
        }
    }
    
    /**
     * 返回指定集群变量的快照副本；集群不存在时返回空 map。
     * 调用方可安全遍历/序列化/修改返回值，不会影响全局状态。
     */
    public static Map<String, String> getVariables(Integer clusterId) {
        Map<String, String> vars = clusterVariablesMap.get(clusterId);
        if (vars == null) {
            return new HashMap<>();
        }
        synchronized (vars) {
            return new HashMap<>(vars);
        }
    }
    
    public static boolean containsValue(Integer clusterId, String key) {
        return clusterVariablesMap.containsKey(clusterId) && clusterVariablesMap.get(clusterId).containsKey(surroundKey(key));
    }
    
    public static boolean containsValueByServerce(Integer clusterId, String serviceName, String variableName) {
        return clusterVariablesMap.containsKey(clusterId) && clusterVariablesMap.get(clusterId).containsKey(surroundKey(serviceName + "." + variableName));
    }
    
    public static void putValue(Integer clusterId, String key, String value) {
        Map<String, String> valueMap = clusterVariablesMap.computeIfAbsent(clusterId, k -> new ConcurrentHashMap<>());
        synchronized (valueMap) {
            valueMap.put(surroundKey(key), value);
        }
    }
    
    public static void putValue(Integer clusterId, String serviceName, String variableName, String value) {
        putValue(clusterId, serviceName + "." + variableName, value);
    }
    
    public static String getValue(Integer clusterId, String key) {
        if (!clusterVariablesMap.containsKey(clusterId)) {
            return null;
        }
        return clusterVariablesMap.get(clusterId).get(surroundKey(key));
    }
    
    public static String getValueByService(Integer clusterId, String serviceName, String variableName) {
        if (!clusterVariablesMap.containsKey(clusterId)) {
            return null;
        }
        return clusterVariablesMap.get(clusterId).get(surroundKey(serviceName + "." + variableName));
    }
    
    public static ConcurrentHashMap<String, String> genDefaultGlobalVariables() {
        ConcurrentHashMap<String, String> globalVariables = new ConcurrentHashMap<>();
        try {
            globalVariables.put("${ROOT.VosManager." + GlobalVariables.HOST + "}", InetAddress.getLocalHost().getHostName());
            globalVariables.put("${ROOT.VosManager." + GlobalVariables.HOST_IP + "}", InetAddress.getLocalHost().getHostAddress());
        } catch (UnknownHostException ignored) {
        }
        globalVariables.put("${ROOT.VosManager.__port__}", getProperty("server.port", "8080"));
        globalVariables.put("${ROOT.VosManager.INSTALL_PATH}", Constants.INSTALL_PATH);
        String mysqlHostPort = extractMysqlHostPort(getProperty("spring.datasource.url"));
        globalVariables.put("${ROOT.Mysql.mysqlHostPort}", mysqlHostPort);
        String[] split = mysqlHostPort.split(":");
        globalVariables.put("${ROOT.Mysql." + GlobalVariables.HOST_IP + "}", split[0]);
        globalVariables.put("${ROOT.Mysql." + GlobalVariables.PORT + "}", split[1]);
        globalVariables.put("${ROOT.Rustfs." + GlobalVariables.HOST_IP + "}", PropertyUtils.getString("rustfs.ip"));
        globalVariables.put("${ROOT.Rustfs." + GlobalVariables.WEB_PORT + "}", PropertyUtils.getString("rustfs.webPort", "9001"));
        globalVariables.put("${ROOT.Rustfs." + GlobalVariables.PORT + "}", PropertyUtils.getString("rustfs.port", "9000"));
        globalVariables.put("${ROOT.Rustfs.access_key}", PropertyUtils.getString("rustfs.access_key"));
        globalVariables.put("${ROOT.Rustfs.secret_key}", PropertyUtils.getString("rustfs.secret_key"));
        // 读取系统变量并且进行注册
        if (System.getenv("JAVA_HOME") != null) {
            globalVariables.put("${ROOT.Jdk.INSTALL_PATH}", System.getenv("JAVA_HOME"));
        }
        if (System.getenv("JAVA8_HOME") != null) {
            globalVariables.put("${ROOT.Jdk8.INSTALL_PATH}", System.getenv("JAVA8_HOME"));
        }
        if (System.getenv("JAVA17_HOME") != null) {
            globalVariables.put("${ROOT.Jdk17.INSTALL_PATH}", System.getenv("JAVA17_HOME"));
        }
        
        return globalVariables;
    }
    
    public static String surroundKey(String variable) {
        return "${" + variable + "}";
    }
    
    private static String extractMysqlHostPort(String jdbcUrl) {
        String regex = "jdbc:mysql://([^:/]+:\\d+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(jdbcUrl);
        
        if (matcher.find()) {
            // 返回匹配的hostIp:port
            return matcher.group(1);
        } else {
            throw new IllegalArgumentException("jdbcUrl格式不正确,无法提取host和port,jdbcUrl:" + jdbcUrl);
        }
    }
}
