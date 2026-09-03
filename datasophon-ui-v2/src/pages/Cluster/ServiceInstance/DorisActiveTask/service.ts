import { request } from '@umijs/max';
import type { DorisActiveTaskQuery, DorisActiveTaskResponse } from './types';

export async function getDorisActiveTasks(
  clusterId: number,
  instanceId: number,
  query: DorisActiveTaskQuery = {},
) {
  return request<{ data: DorisActiveTaskResponse }>(
    `/cluster/${clusterId}/service/${instanceId}/doris/active-tasks`,
    { method: 'POST', data: query, skipErrorHandler: true },
  );
}
