import { request } from '@umijs/max';

// ─── 类型定义 ───────────────────────────────────────────────

export interface AssignRackParams {
  rack?: string;
  hostIds: number[];
}

// ─── API ─────────────────────────────────────────────────────

/** 分页主机列表 */
export function listClusterHosts(
  clusterId: number,
  params: {
    page: number;
    pageSize: number;
    hostname?: string;
    ip?: string;
    hostState?: number;
    sortField?: string;
    sortOrder?: string;
  },
) {
  return request<{ data: DATASOPHON.HostPageResponse }>(
    `/cluster/${clusterId}/host/list`,
    { method: 'GET', params },
  );
}

/** 主机详情 */
export function getClusterHost(clusterId: number, hostId: number) {
  return request<{ data: DATASOPHON.HostResponse }>(
    `/cluster/${clusterId}/host/${hostId}`,
    { method: 'GET' },
  );
}

/** 批量删除主机 */
export function deleteClusterHosts(clusterId: number, ids: number[]) {
  return request<{ data: void }>(`/cluster/${clusterId}/host`, {
    method: 'DELETE',
    data: ids,
  });
}

/** 按主机名查角色列表 */
export function getHostRoles(clusterId: number, hostname: string) {
  return request<{ data: DATASOPHON.HostRoleResponse[] }>(
    `/cluster/${clusterId}/host/roles`,
    { method: 'GET', params: { hostname } },
  );
}

/** 分配机架 */
export function assignRack(
  clusterId: number,
  params: AssignRackParams,
) {
  return request<{ data: void }>(
    `/cluster/${clusterId}/host/assign-rack`,
    { method: 'POST', data: params },
  );
}

/** 获取机架列表 */
export function getRackList(clusterId: number) {
  return request<{ data: string[] }>(
    `/cluster/${clusterId}/host/rack`,
    { method: 'GET' },
  );
}
