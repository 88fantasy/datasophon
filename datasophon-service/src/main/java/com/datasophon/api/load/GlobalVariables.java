package com.datasophon.api.load;

import com.datasophon.common.Constants;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.datasophon.api.load.Application.getProperty;

public class GlobalVariables {

  public static final String HOST = "__host__";

  public static final String HOST_IP = "__hostIp__";

  public static final String PORT = "__port__";

  // cluster variable
  private static final Map<Integer, Map<String, String>> clusterVariablesMap = new ConcurrentHashMap<>();

  public static void put(Integer clusterId, Map<String, String> value) {
    clusterVariablesMap.put(clusterId, new ConcurrentHashMap<>(value));
  }

  public static Map<String, String> getVariables(Integer clusterId) {
    return clusterVariablesMap.get(clusterId);
  }

  public static boolean containsValue(Integer clusterId, String key) {
    return clusterVariablesMap.containsKey(clusterId) && clusterVariablesMap.get(clusterId).containsKey(surroundVariable(key));
  }

  public static void putValue(Integer clusterId, String key, String value) {
    Map<String, String> valueMap;
    if (clusterVariablesMap.containsKey(clusterId)) {
      valueMap = clusterVariablesMap.get(clusterId);
    } else {
      valueMap = new ConcurrentHashMap<>();
    }
    valueMap.put(surroundVariable(key), value);
    clusterVariablesMap.put(clusterId, valueMap);
  }

  public static String getValue(Integer clusterId, String key) {
    if (!clusterVariablesMap.containsKey(clusterId)) {
      return null;
    }
    return clusterVariablesMap.get(clusterId).get(surroundVariable(key));
  }

  public static ConcurrentHashMap<String, String> genDefaultGlobalVariables() {
    ConcurrentHashMap<String, String> globalVariables = new ConcurrentHashMap<>();
    try {
      globalVariables.put("${ROOT.VosManager." + GlobalVariables.HOST + "}", InetAddress.getLocalHost().getHostName());
      globalVariables.put("${ROOT.VosManager." + GlobalVariables.HOST_IP + "}", InetAddress.getLocalHost().getHostAddress());
    } catch (UnknownHostException ignored) {
    }
    globalVariables.put("${ROOT.VosManager.__port__}", getProperty("server.port", "8081"));
    globalVariables.put("${ROOT.VosManager.INSTALL_PATH}", Constants.INSTALL_PATH);
    String mysqlHostPort = extractMysqlHostPort(getProperty("spring.datasource.url"));
    globalVariables.put("${ROOT.Mysql.mysqlHostPort}", mysqlHostPort);
    String[] split = mysqlHostPort.split(":");
    globalVariables.put("${ROOT.Mysql." + GlobalVariables.HOST_IP + "}", split[0]);
    globalVariables.put("${ROOT.Mysql." + GlobalVariables.PORT + "}", split[1]);
    globalVariables.put("${ROOT.Rustfs." + GlobalVariables.HOST_IP + "}", getProperty("rustfs.ip"));
    globalVariables.put("${ROOT.Rustfs." + GlobalVariables.PORT + "}", getProperty("rustfs.port", "9000"));
    globalVariables.put("${ROOT.Rustfs.access_key}", getProperty("rustfs.access_key"));
    globalVariables.put("${ROOT.Rustfs.secret_key}", getProperty("rustfs.secret_key"));
    return globalVariables;
  }

  public static String surroundVariable(String variable) {
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
