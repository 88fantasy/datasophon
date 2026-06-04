package com.datasophon.worker.test.portal;

import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.worker.utils.FreemakerUtils;

import freemarker.template.TemplateException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * @author zhanghuangbin
 * @date 2024/12/25
 */
public class PortalTplGenerateTest {
    
    @Test
    public void generateCustomTemplate() throws IOException, TemplateException {
        Generators generators = new Generators();
        generators.setConfigFormat("custom");
        generators.setFilename("gateway-mvc.yaml");
        generators.setTemplateName("portal-gateway-mvc.ftl");
        generators.setOutputDirectory("output");
        
        Map<String, Object> map1 = new HashMap<>();
        map1.put("replaceReg", "/$\\{segment}");
        map1.put("predicateUrl", "/gateway/portal/**");
        map1.put("id", "portal");
        map1.put("uri", "lb://portal");
        map1.put("rewriteReg", "/gateway/portal/(?<segment>.*)");
        
        Map<String, Object> map2 = new HashMap<>();
        map2.put("replaceReg", "/$\\{segment}");
        map2.put("predicateUrl", "/gateway/portal/**");
        map2.put("id", "portal");
        map2.put("uri", "lb://portal");
        map2.put("rewriteReg", "/gateway/portal/(?<segment>.*)");
        
        ServiceConfig serviceConfig = new ServiceConfig();
        serviceConfig.setName("portalRoutes");
        serviceConfig.setValue(Arrays.asList(map1, map2));
        serviceConfig.setConfigType("map");
        
        // ServiceConfig serviceConfig2 = new ServiceConfig();
        // serviceConfig2.setName("apiPort");
        // serviceConfig2.setValue("8081");
        
        ArrayList<ServiceConfig> serviceConfigs = new ArrayList<>();
        serviceConfigs.add(serviceConfig);
        
        FreemakerUtils.generateConfigFile(generators, serviceConfigs, "");
    }
}
