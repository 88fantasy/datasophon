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

package com.datasophon.dao.mapper.cmd;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datasophon.dao.entity.cmd.ClusterK8sServiceCommandEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * K8s 服务命令执行记录
 *
 * @author zhanghuangbin
 * @date 2026-03-30
 */
@Mapper
public interface ClusterK8sServiceCommandMapper extends BaseMapper<ClusterK8sServiceCommandEntity> {


}
