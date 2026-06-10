import { request } from '@umijs/max';

/** 集群服务实例列表 */
export async function listClusterServices(clusterId: number) {
  return request<{ data: DATASOPHON.ServiceInstanceInfo[] }>(
    `/cluster/${clusterId}/service/instance/list`,
    { method: 'GET' },
  );
}

/** 服务实例详情 */
export async function getServiceInstance(clusterId: number, instanceId: number) {
  return request<{ data: DATASOPHON.ServiceInstanceInfo }>(
    `/cluster/${clusterId}/service/instance/${instanceId}`,
    { method: 'GET' },
  );
}

/** 服务角色实例列表（分页） */
export async function listServiceRoleInstances(
  clusterId: number,
  instanceId: number,
  params: {
    page?: number;
    pageSize?: number;
    hostname?: string;
    serviceRoleState?: number;
    serviceRoleName?: string;
    roleGroupId?: number;
  },
) {
  return request<DATASOPHON.ApiResponse<DATASOPHON.ServiceRoleInstanceInfo[]>>(
    `/cluster/${clusterId}/service/instance/${instanceId}/role/list`,
    { method: 'GET', params },
  );
}

/** 服务角色类型列表 */
export async function getServiceRoleTypeList(clusterId: number, instanceId: number) {
  return request<DATASOPHON.ApiResponse<{ id: number; serviceRoleName: string }[]>>(
    `/cluster/${clusterId}/service/instance/${instanceId}/role/type-list`,
    { method: 'GET' },
  );
}

/** 服务角色组列表 */
export async function getServiceRoleGroupList(clusterId: number, instanceId: number) {
  return request<
    DATASOPHON.ApiResponse<
      { id: number; roleGroupName: string; serviceInstanceId: number }[]
    >
  >(`/cluster/${clusterId}/service/instance/${instanceId}/role/group-list`, {
    method: 'GET',
  });
}

/** 服务 WebUI 列表 */
export async function getServiceWebUis(clusterId: number, instanceId: number) {
  return request<DATASOPHON.ApiResponse<DATASOPHON.WebuiInfo[]>>(
    `/cluster/${clusterId}/service/instance/${instanceId}/role/webuis`,
    { method: 'GET' },
  );
}

/** 配置历史版本列表（仅物理集群，K8s 另有接口） */
export async function listConfigVersions(
  clusterId: number,
  instanceId: number,
  roleGroupId: number,
) {
  return request<DATASOPHON.ApiResponse<number[]>>(
    `/cluster/${clusterId}/service/instance/${instanceId}/config/versions`,
    { method: 'GET', params: { roleGroupId } },
  );
}

/** 按版本获取配置参数列表（version=undefined 时后端返回最新版） */
export async function getServiceConfig(
  clusterId: number,
  instanceId: number,
  roleGroupId: number,
  version?: number,
) {
  return request<DATASOPHON.ApiResponse<DATASOPHON.ConfigField[]>>(
    `/cluster/${clusterId}/service/instance/${instanceId}/config`,
    { method: 'GET', params: { roleGroupId, version } },
  );
}

/** 保存配置（自动版本递增 + needRestart 打标） */
export async function saveServiceConfig(
  clusterId: number,
  instanceId: number,
  body: { roleGroupId: number; serviceConfig: DATASOPHON.ConfigField[] },
) {
  return request<DATASOPHON.ApiResponse<void>>(
    `/cluster/${clusterId}/service/instance/${instanceId}/config`,
    { method: 'POST', data: body },
  );
}

// ── K8s 集群链路 API ──────────────────────────────────────────────────────

/** K8s 集群 namespace 列表（同时触发与 K8s 集群对账更新） */
export async function listK8sNamespaces(clusterId: number) {
  return request<DATASOPHON.ApiResponse<DATASOPHON.K8sNamespace[]>>(
    `/cluster/${clusterId}/k8s/namespace/list`,
    { method: 'GET' },
  );
}

/** 指定 namespace 下的服务实例列表 */
export async function listK8sInstances(clusterId: number, namespace: string) {
  return request<DATASOPHON.ApiResponse<DATASOPHON.K8sServiceInstanceVO[]>>(
    `/cluster/${clusterId}/k8s/namespace/${namespace}/instance/list`,
    { method: 'GET' },
  );
}

/** 服务实例支持的资源类型列表（Pod / Service / Deployment / Ingress / ConfigMap 等） */
export async function listK8sResourceTypes(clusterId: number, instanceId: number) {
  return request<DATASOPHON.ApiResponse<string[]>>(
    `/cluster/${clusterId}/k8s/instance/${instanceId}/resource-types`,
    { method: 'GET' },
  );
}

/** 服务实例指定资源类型的资源列表 */
export async function listK8sResources(
  clusterId: number,
  instanceId: number,
  resourceType: string,
) {
  return request<DATASOPHON.ApiResponse<Record<string, unknown>[]>>(
    `/cluster/${clusterId}/k8s/instance/${instanceId}/resource`,
    { method: 'GET', params: { resourceType } },
  );
}

/** K8s Helm values 版本列表（仅含 id / version，降序） */
export async function listK8sConfigVersions(clusterId: number, instanceId: number) {
  return request<DATASOPHON.ApiResponse<DATASOPHON.K8sInstanceValuesSimple[]>>(
    `/cluster/${clusterId}/k8s/instance/${instanceId}/config/versions`,
    { method: 'GET' },
  );
}

/** 读取指定版本的完整 Helm values（含 values / deltaValues / metaFileType） */
export async function getK8sConfig(clusterId: number, instanceId: number, valueId: number) {
  return request<DATASOPHON.ApiResponse<DATASOPHON.K8sInstanceValues>>(
    `/cluster/${clusterId}/k8s/instance/${instanceId}/config/${valueId}`,
    { method: 'GET' },
  );
}

/** 保存用户编辑的 deltaValues（仅更新当前版本，不升版、不打 needRestart） */
export async function saveK8sConfig(
  clusterId: number,
  instanceId: number,
  body: { id: number; deltaValues: string },
) {
  return request<DATASOPHON.ApiResponse<void>>(
    `/cluster/${clusterId}/k8s/instance/${instanceId}/config`,
    { method: 'POST', data: body },
  );
}
