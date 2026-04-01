/*
/*
 *
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
 *
 */

import { VUE_APP_PUBLIC_PATH } from "../../config";
import paths from "../baseUrl"; // 后台服务地址

let path = VUE_APP_PUBLIC_PATH + paths.path();
export default {
  getServiceList: path + "/frame/service/list", // 选择服务的列表
  listNewest: path + "/frame/service/listNewest", // 选择服务的列表
  listBasicFrameService: path + "/frame/service/listBasicFrameService", // 选择服务的列表
  listNewestByDeployment: path + "/extrepo/listNewestByDeployment", // 选择服务的列表
  deleteService: path + "/frame/service/delete", // 删除框架服务
  getServiceDdl: path + "/frame/service/getServiceDdl", // 删除框架服务
  updateDdl2: path + "/frame/service/updateDdl2", // 删除框架服务
  listSimpleK8sInstanceValuesByInstanceId: path + "/frame/k8sInstanceValues/listSimpleByInstanceId", // 新增 k8s 实例值简化列表
  getK8sInstanceValuesById: path + "/frame/k8sInstanceValues/getById", // 新增 k8s 实例值根据ID查询
  updateK8sInstanceValues: path + "/frame/k8sInstanceValues/update", // 新增 k8s 实例值更新
  listNewestByDeploymentK8s: path + "/extrepo/k8s/listNewestByDeployment", // k8s 部署列表中获取最新服务
  saveServiceNamespaceMapping: path + "/extrepo/k8s/saveServiceNamespaceMapping", // k8s 命名空间映射保存
  saveConfigValuesK8s: path + "/extrepo/k8s/saveConfigValues", // k8s 配置值保存
  getValueFromRepo: path + "/frame/k8sInstanceValues/getValueFromRepo", // k8s 实例值从仓库获取
  getServiceConfigOption: path + "/service/install/getServiceConfigOption", // 查询服务配置
  getServiceConfigFromDdl: path + "/service/install/getServiceConfigFromDdl", // 查询服务配置
  getServiceRoleList: path + "/frame/service/role/getServiceRoleList", // 查询服务对应的服务角色
  getServiceRoleListByDeployment:
    path + "/extrepo/getServiceRoleListByDeployment", // 查询服务对应的服务角色
  getAllHost: path + "/cluster/host/all", // 查询集群所有主机
  saveServiceRoleHostMapping:
    path + "/service/install/saveServiceRoleHostMapping", // 保存服务角色与主机对应关系
  getNonMasterRoleList: path + "/frame/service/role/getNonMasterRoleList", // 查询服务对应的非Master角色
  getNonMasterRoleListByDeployment:
    path + "/extrepo/getNonMasterRoleListByDeployment", // 查询服务对应的非Master角色
  saveServiceConfig: path + "/service/install/saveServiceConfig", // 保存服务配置
  startExecuteCommand: path + "/cluster/service/command/startExecuteCommand", // 启动执行指令
  redeploy: path + "/extrepo/redeploy", // 启动执行指令
  generateCommand: path + "/cluster/service/command/generateCommand", // 生成服务操作指令
  getServiceCommandlist:
    path + "/cluster/service/command/getServiceCommandlist", // 查询服务安装指令列表1
  getServiceHostList: path + "/cluster/service/command/host/list", // 查询服务安装对应主机列表
  getServiceRoleOrderList: path + "/cluster/service/command/host/command/list", // 查询主机上服务角色指令列表3
  getLog: path + "/cluster/service/role/instance/getLog", // 服务实例-查看日志
  getHostCommandLog:
    path + "/cluster/service/command/host/command/getHostCommandLog", // 查询主机上服务角色指令3日志
  getQueueList: path + "/cluster/yarn/queue/list", // 队列列表
  getScheduleLog: path + "/log/getScheduleLog", // 队列列表
  getCapacityList: path + "/cluster/queue/capacity/list", // 容量队列列表
  saveQueue: path + "/cluster/yarn/queue/save", // 队列保存
  deleteQueue: path + "/cluster/yarn/queue/delete", // 队列删除
  updateQueue: path + "/cluster/yarn/queue/update", // 更新队列
  refreshQueues: path + "/cluster/yarn/queue/refreshQueues", // 刷新队列到Yarn
  refreshQueuesYARN: path + "/cluster/queue/capacity/refreshToYarn",
  checkServiceDependency: path + "/service/install/checkServiceDependency",
  validDeploymentFile: path + "/extrepo/validDeploymentFile",
};
