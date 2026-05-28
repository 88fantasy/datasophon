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


package com.datasophon.api.strategy;

import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.utils.PlaceholderUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class ServiceHandlerAbstract {
    
    public void removeConfigWithKerberos(List<ServiceConfig> list, Map<String, ServiceConfig> map,
                                         List<ServiceConfig> configs) {
        for (ServiceConfig serviceConfig : configs) {
            if (serviceConfig.isConfigWithKerberos()) {
                if (map.containsKey(serviceConfig.getName())) {
                    list.remove(map.get(serviceConfig.getName()));
                }
            }
        }
    }
    
    public void removeConfigWithHA(List<ServiceConfig> list, Map<String, ServiceConfig> map,
                                   List<ServiceConfig> configs) {
        for (ServiceConfig serviceConfig : configs) {
            if (serviceConfig.isConfigWithHA()) {
                if (map.containsKey(serviceConfig.getName())) {
                    list.remove(map.get(serviceConfig.getName()));
                }
            }
        }
    }
    
    public void removeConfigWithRack(List<ServiceConfig> list, Map<String, ServiceConfig> map,
                                     List<ServiceConfig> configs) {
        for (ServiceConfig serviceConfig : configs) {
            if (serviceConfig.isConfigWithRack()) {
                if (map.containsKey(serviceConfig.getName())) {
                    list.remove(map.get(serviceConfig.getName()));
                }
            }
        }
    }
    
    public void addConfigWithKerberos(Map<String, String> globalVariables, Map<String, ServiceConfig> map,
                                      List<ServiceConfig> configs, ArrayList<ServiceConfig> kbConfigs) {
        for (ServiceConfig serviceConfig : configs) {
            if (serviceConfig.isConfigWithKerberos()) {
                addConfig(globalVariables, map, kbConfigs, serviceConfig);
            }
        }
    }
    
    public void addConfigWithHA(Map<String, String> globalVariables, Map<String, ServiceConfig> map,
                                List<ServiceConfig> configs, ArrayList<ServiceConfig> kbConfigs) {
        for (ServiceConfig serviceConfig : configs) {
            if (serviceConfig.isConfigWithHA()) {
                addConfig(globalVariables, map, kbConfigs, serviceConfig);
            }
        }
    }
    
    public void addConfigWithRack(Map<String, String> globalVariables, Map<String, ServiceConfig> map,
                                  List<ServiceConfig> configs, List<ServiceConfig> rackConfigs) {
        for (ServiceConfig serviceConfig : configs) {
            if (serviceConfig.isConfigWithRack()) {
                addConfig(globalVariables, map, rackConfigs, serviceConfig);
            }
        }
    }
    
    private void addConfig(Map<String, String> globalVariables, Map<String, ServiceConfig> map,
                           List<ServiceConfig> rackConfigs, ServiceConfig serviceConfig) {
        if (map.containsKey(serviceConfig.getName())) {
            ServiceConfig config = map.get(serviceConfig.getName());
            config.setRequired(true);
            config.setEnabled(true);
            config.setHidden(false);
            if (Constants.INPUT.equals(config.getType())) {
                String value = PlaceholderUtils.replacePlaceholders((String) config.getValue(), globalVariables,
                        Constants.REGEX_VARIABLE);
                config.setValue(value);
            }
        } else {
            serviceConfig.setRequired(true);
            serviceConfig.setEnabled(true);
            serviceConfig.setHidden(false);
            if (Constants.INPUT.equals(serviceConfig.getType())) {
                String value = PlaceholderUtils.replacePlaceholders((String) serviceConfig.getValue(), globalVariables,
                        Constants.REGEX_VARIABLE);
                serviceConfig.setValue(value);
            }
            rackConfigs.add(serviceConfig);
        }
    }

    /**
     * TODO 将废弃
     * @return
     */
    public boolean decideEnableKerberos(Integer clusterId, boolean defaultVal, ServiceConfig config, String serviceName) {
        boolean enableKerberos = defaultVal;
        if (config.getValue() != null) {
            enableKerberos = Boolean.TRUE.equals(config.getValue());
        }
        ProcessUtils.generateClusterVariable(clusterId, serviceName, "enable" + serviceName + "Kerberos", enableKerberos ? "true" : "false");
        return enableKerberos;
    }
    
    public boolean decideEnableHA(Integer clusterId, boolean defaultVal, ServiceConfig config, String serviceName) {
        boolean enableHA = defaultVal;
        if (config.getValue() != null) {
            enableHA = Boolean.TRUE.equals(config.getValue());
        }
        ProcessUtils.generateClusterVariable(clusterId, serviceName, "enable" + serviceName + "HA",  enableHA ? "true" : "false");
        return enableHA;
    }
    
    public boolean isEnableRack(ServiceConfig config, boolean defaultVal) {
        if (config.getValue() == null) {
            return defaultVal;
        }
        return (boolean) config.getValue();
    }
}
