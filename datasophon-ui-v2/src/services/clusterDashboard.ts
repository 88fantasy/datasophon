import { request } from '@umijs/max';
import type { LegacyResult } from './physicalHostInstall';

/** 告警历史接口是遗留 v1 端点（无 /v2 前缀），需覆盖默认 baseURL，见 physicalHostInstall.ts 同款用法 */
const legacyRequestOptions = { baseURL: '/ddh/api' } as const;

/** 集群总览看板聚合数据（统计数字/告警趋势/服务健康度/集群概要） */
export function getClusterDashboardSummary(clusterId: number) {
  return request<{ data: DATASOPHON.ClusterDashboardResponse }>(
    `/cluster/${clusterId}/dashboard/summary`,
    { method: 'GET' },
  );
}

/** 最新告警（复用告警管理已有的分页历史接口 ClusterAlertHistoryController，非 v2） */
export function getRecentAlerts(clusterId: number, pageSize: number) {
  return request<LegacyResult<DATASOPHON.ClusterAlertHistoryRecord[]>>(
    '/cluster/alert/history/getAllAlertList',
    {
      ...legacyRequestOptions,
      method: 'GET',
      params: { clusterId, page: 1, pageSize },
    },
  );
}
