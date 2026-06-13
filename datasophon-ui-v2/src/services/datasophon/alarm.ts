import { request } from '@umijs/max';

// ─── 告警组 ─────────────────────────────────────────────────

/** 告警组列表（分页） */
export function listAlertGroups(
  clusterId: number,
  params: { alertGroupName?: string; page: number; pageSize: number },
) {
  return request<{ data: { totalCount: number; totalList: DATASOPHON.AlertGroup[] } }>(
    `/cluster/${clusterId}/alert/group/list`,
    { method: 'GET', params },
  );
}

/** 新建告警组 */
export function saveAlertGroup(clusterId: number, body: Partial<DATASOPHON.AlertGroup>) {
  return request<{ data: void }>(`/cluster/${clusterId}/alert/group`, {
    method: 'POST',
    data: body,
  });
}

/** 批量删除告警组 */
export function deleteAlertGroups(clusterId: number, ids: number[]) {
  return request<{ data: void }>(`/cluster/${clusterId}/alert/group`, {
    method: 'DELETE',
    data: ids,
  });
}

/** 告警组类别下拉（即关联服务名列表） */
export function listAlertCategories(clusterId: number) {
  return request<{ data: { serviceName: string }[] }>(
    `/cluster/${clusterId}/alert/group/categories`,
    { method: 'GET' },
  );
}

// ─── 告警指标 ───────────────────────────────────────────────

/** 告警指标列表（分页） */
export function listAlertQuotas(
  clusterId: number,
  params: {
    alertGroupId?: number;
    quotaName?: string;
    page: number;
    pageSize: number;
  },
) {
  return request<{ data: { totalCount: number; totalList: DATASOPHON.AlertQuota[] } }>(
    `/cluster/${clusterId}/alert/quota/list`,
    { method: 'GET', params },
  );
}

/** 新建告警指标 */
export function saveAlertQuota(clusterId: number, body: Partial<DATASOPHON.AlertQuota>) {
  return request<{ data: void }>(`/cluster/${clusterId}/alert/quota`, {
    method: 'POST',
    data: body,
  });
}

/** 修改告警指标 */
export function updateAlertQuota(clusterId: number, body: Partial<DATASOPHON.AlertQuota>) {
  return request<{ data: void }>(`/cluster/${clusterId}/alert/quota`, {
    method: 'PUT',
    data: body,
  });
}

/** 批量删除告警指标 */
export function deleteAlertQuotas(clusterId: number, ids: number[]) {
  return request<{ data: void }>(`/cluster/${clusterId}/alert/quota`, {
    method: 'DELETE',
    data: ids,
  });
}

/** 启用告警指标（alertQuotaIds 逗号分隔） */
export function startQuotas(clusterId: number, alertQuotaIds: string) {
  return request<{ data: void }>(`/cluster/${clusterId}/alert/quota/start`, {
    method: 'POST',
    params: { alertQuotaIds },
  });
}

/** 停用告警指标（alertQuotaIds 逗号分隔） */
export function stopQuotas(clusterId: number, alertQuotaIds: string) {
  return request<{ data: void }>(`/cluster/${clusterId}/alert/quota/stop`, {
    method: 'POST',
    params: { alertQuotaIds },
  });
}

/** 按告警组查询可绑定的服务角色列表 */
export function listQuotaRoles(clusterId: number, alertGroupId: number) {
  return request<{ data: { serviceRoleName: string }[] }>(
    `/cluster/${clusterId}/alert/quota/roles`,
    { method: 'GET', params: { alertGroupId } },
  );
}
