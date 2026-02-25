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

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Data
public class ServiceInfo {

    @NotBlank(message = "name字段不能为空")
    private String name;
    
    private String label;

    @NotBlank(message = "version字段不能为空")
    private String version;
    
    private String description;

    @NotEmpty(message = "roles字段不能为空")
    private List<ServiceRoleInfo> roles;
    
    private List<ServiceConfig> parameters;

    @NotNull(message = "dependencies字段不能为空")
    private List<String> dependencies;
    
    private ConfigWriter configWriter;
    
    private String packageName;

    @NotEmpty(message = "decompressPackageName字段不能为空")
    private String decompressPackageName;

    /**
     * 创建解压目录
     */
    private Boolean createDecompressDir;
    
    private Map<String, ArchInfo> arch;
    
    private ExternalLink externalLink;
    
    private Integer sortNum;

    private String type;
    
}
