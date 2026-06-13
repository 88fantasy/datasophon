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

/** 批量操作角色实例（启动/停止/重启） */
export async function execRoleCommand(
  clusterId: number,
  instanceId: number,
  params: { commandType: string; serviceRoleInstancesIds: string },
) {
  return request<DATASOPHON.ApiResponse<{ data: string }>>(
    `/cluster/${clusterId}/service/instance/${instanceId}/role/command`,
    { method: 'POST', params },
  );
}

/** 删除角色实例 */
export async function deleteRoleInstances(
  clusterId: number,
  instanceId: number,
  serviceRoleInstancesIds: string,
) {
  return request<DATASOPHON.ApiResponse<void>>(
    `/cluster/${clusterId}/service/instance/${instanceId}/role/delete`,
    { method: 'POST', params: { serviceRoleInstancesIds } },
  );
}

/** 查看角色实例日志 */
export async function getRoleInstanceLog(
  clusterId: number,
  instanceId: number,
  roleInstanceId: number,
) {
  return request<DATASOPHON.ApiResponse<{ data: string }>>(
    `/cluster/${clusterId}/service/instance/${instanceId}/role/${roleInstanceId}/log`,
    { method: 'GET' },
  );
}

/** 添加角色组 */
export async function saveRoleGroup(
  clusterId: number,
  instanceId: number,
  params: { roleGroupId?: number; roleGroupName: string },
) {
  return request<DATASOPHON.ApiResponse<void>>(
    `/cluster/${clusterId}/service/instance/${instanceId}/role/group/save`,
    { method: 'POST', params },
  );
}

/** 批量分配角色组 */
export async function bindRoleGroup(
  clusterId: number,
  instanceId: number,
  params: { roleInstanceIds: string; roleGroupId: number },
) {
  return request<DATASOPHON.ApiResponse<void>>(
    `/cluster/${clusterId}/service/instance/${instanceId}/role/group/bind`,
    { method: 'POST', params },
  );
}
