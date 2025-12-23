package com.datasophon.api.load;

import com.datasophon.common.model.Host;
import com.datasophon.common.model.uni.NexusRegistry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class Application implements ApplicationContextAware {

  @Getter
  private static ApplicationContext context;


  @Override
  public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
    Application.context = applicationContext;
  }

  public static <T> T getBean(Class<T> t) {
    return context.getBean(t);
  }

  public static <T> T getBean(String beanName, Class<T> t) {
    return context.getBean(beanName, t);
  }

  public static String getProperty(String key) {
    return context.getEnvironment().getProperty(key);
  }

  public static String getProperty(String key, String defaultVal) {
    return context.getEnvironment().getProperty(key, defaultVal);
  }

  public static String getProperty(ApplicationContext context, String key, String defaultVal) {
    return context.getEnvironment().getProperty(key, defaultVal);
  }


  public static String userDir() {
    return StringUtils.removeEnd(System.getProperty("user.dir"), "/");
  }


  public static boolean swagger() {
    return Boolean.parseBoolean(getProperty("springdoc.api-docs.enabled=", "false"));
  }

  public static String getApiPrefix() {
    return getProperty("datasophon.server.path-prefix");
  }

  public static String getServerPrefix() {
    return getProperty("server.servlet.context-path", "/");
  }

  public static NexusRegistry getNexus() {
    NexusRegistry nexusRegistry = new NexusRegistry();
    nexusRegistry.setEnable(Boolean.parseBoolean(getProperty("nexus.enable", "true")));
    Host host = new Host();
    host.setIp(getProperty("nexus.ip"));
    NexusRegistry.Config config = new NexusRegistry.Config();
    config.setWebPort(getProperty("nexus.port"));
    config.setUser(getProperty("nexus.user"));
    config.setPassword(getProperty("nexus.password"));
    nexusRegistry.setHost(host);
    nexusRegistry.setConfig(config);
    return nexusRegistry;
  }


}
