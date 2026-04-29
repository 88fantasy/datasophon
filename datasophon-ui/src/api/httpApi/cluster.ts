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

let path =  VUE_APP_PUBLIC_PATH + paths.path();

export default {
  getColonyList: path +"/cluster/list", // 获取集群列表
  saveColony: path +"/cluster/save", // 集群保存
  updateColony: path +"/cluster/update", // 集群更新
  deleteColony: path +"/cluster/delete", // 集群删除
  authCluster: path +"/cluster/user/saveClusterManager", // 集群授权
  getFrameList: path +"/frame/list", // 获取服务框架列表
  runningClusterList: path +"/cluster/runningClusterList", // 正在运行状态集群列表
  getDashboardUrl: path + "/cluster/service/dashboard/getDashboardUrl", // 查询总览地址
  reNameGroup: path + "/cluster/service/instance/role/group/rename",
  delGroup: path + "/cluster/service/instance/role/group/delete",
  saveLabel: path + "/cluster/node/label/save",
  assginLabel: path + "/cluster/node/label/assign",
  deleteLabel: path + "/cluster/node/label/delete",
  getLabelList: path + "/cluster/node/label/list",
  saveRack: path + "/cluster/rack/save",
  assginRack: path +"/cluster/host/assignRack",
  deleteRack: path + "/cluster/rack/delete",
  deleteClusterRack: path + "/cluster/rack/delete",
  getRackList: path + "/cluster/rack/list",
  getParcelList: path + "/cluster/parcel/list",
  getParcelParse: path + "/cluster/parcel/parse",
  getParcelProcess: path + "/cluster/parcel/process",
  downloadComponent: path + "/cluster/parcel/download",
  installComponent: path + "/cluster/parcel/install",
  clusterGroupList: path + "/cluster/group/list",
  clusterUserCreate: path + "/cluster/user/create",
  clusterUserDelete: path + "/cluster/user/delete",
  clusterGroupDelete: path + "/cluster/group/delete",
  clusterGroupSave: path + "/cluster/group/save",
  clusterServiceInstanceDelete: path + "/cluster/service/instance/delete",
  clusterYarnSchedulerInfo: path + "/cluster/yarn/scheduler/info",
  clusterK8sConfigTestConnection: path + "/cluster/k8sConfig/testConnection",
  saveOrUpdateK8sConfig: path + "/cluster/k8sConfig/saveOrUpdateConfig",
  k8sNamespaceListByClusterId: path + "/cluster/k8sNamespace/listByClusterId",
  k8sInstanceQueryInstanceList: path + "/cluster/k8sInstance/queryInstanceList",
  k8sInstanceListResource: path + "/cluster/k8sInstance/listResource",
  k8sInstanceListResourceType: path + "/cluster/k8sInstance/listResourceType",
  k8sInstanceRemoveInstanceId: path + "/cluster/k8sInstance/removeInstanceId",
  k8sServiceCommandFindCommandByPage: path + "/cluster/k8sService/command/findCommandByPage",
  getConfigByClusterId: path + "/cluster/k8sConfig/getConfigByClusterId",
};
