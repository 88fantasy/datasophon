import { request } from '@umijs/max';
import type {
  DorisActiveTask,
  DorisActiveTaskQuery,
  DorisActiveTaskResponse,
} from './types';

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

export async function getDorisActiveTaskDetail(
  clusterId: number,
  instanceId: number,
  taskId: string,
) {
  return request<{ data: DorisActiveTask }>(
    `/cluster/${clusterId}/service/${instanceId}/doris/active-tasks/${encodeURIComponent(taskId)}`,
    { method: 'GET', skipErrorHandler: true },
  );
}
