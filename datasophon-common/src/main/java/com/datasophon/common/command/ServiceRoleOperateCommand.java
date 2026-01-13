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

package com.datasophon.common.command;

import com.datasophon.common.enums.CommandType;
import com.datasophon.common.model.HookConfig;
import com.datasophon.common.model.RunAs;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class ServiceRoleOperateCommand extends BaseCommand implements Serializable {
    
    private static final long serialVersionUID = 6454341380133032878L;
    private Integer serviceRoleInstanceId;
    
    private CommandType commandType;
    
    private Long deliveryId;


    /**
     * 创建解压目录
     */
    private Boolean createDecompressDir;
    
    private boolean isSlave;
    
    private String masterHost;
    
    private String managerHost;
    
    private Boolean enableRangerPlugin;
    
    private RunAs runAs;
    
    private Boolean enableKerberos;

    private List<HookConfig> hooks;

    private Map<String,String> variables;
    
    public ServiceRoleOperateCommand() {
        this.enableRangerPlugin = false;
        this.enableKerberos = false;
    }
}
