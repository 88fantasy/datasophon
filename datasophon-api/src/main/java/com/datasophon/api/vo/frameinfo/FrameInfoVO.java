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

package com.datasophon.api.vo.frameinfo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class FrameInfoVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 主键
     */
    @TableId
    private Integer id;
    /**
     * 框架名称
     */
    private String frameName;
    /**
     * 框架编码
     */
    private String frameCode;
    /**
     * 框架版本
     */
    private String frameVersion;
    

    private List<FrameServiceEntity> frameServiceList;

    private List<FrameK8sServiceEntity> frameK8sServiceList;
    
}
