/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datasophon.common.model;

import com.datasophon.common.command.ServiceRoleResource;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.enums.HookType;
import com.datasophon.common.enums.ServiceRoleType;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class ServiceRoleInfo implements Serializable, ServiceRoleResource {


    private String name;
    
    private ServiceRoleType roleType;
    
    private String cardinality;
    
    private Integer sortNum;
    
    private ServiceRoleRunner startRunner;
    
    private ServiceRoleRunner stopRunner;
    
    private ServiceRoleRunner statusRunner;
    
    private ExternalLink externalLink;
    
    private String hostname;
    
    private String hostCommandId;
    
    private Integer clusterId;
    
    private String parentName;
    
    private String frameCode;
    
    private String packageName;
    
    private Map<String, ArchInfo> archInfoMap;
    
    private String decompressPackageName;

    /**
     * 创建解压目录
     */
    private Boolean createDecompressDir;
    
    private Map<Generators, List<ServiceConfig>> configFileMap;
    
    private String logFile;
    
    private String jmxPort;
    
    private List<Map<String, Object>> resourceStrategies;
    
    private boolean isSlave = false;
    
    private CommandType commandType;
    
    private String masterHost;
    
    private Boolean enableRangerPlugin;
    
    private Integer serviceInstanceId;
    
    private RunAs runAs;

    private List<HookConfig> hooks;

    @Override
    public String getServiceName() {
        return parentName;
    }

    @Override
    public String getServiceRoleName() {
        return name;
    }

    public List<HookConfig> getMatchedHooks(HookType...types) {
        List<HookType> typeList = Arrays.asList(types);
        List<HookConfig> tmp = hooks == null ? new ArrayList<>(0) : hooks;
        return tmp.stream().filter(hook-> typeList.contains(hook.getType())).collect(Collectors.toList());
    }
}
