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
