import { request } from '@umijs/max';

/** 获取 YARN 调度器类型（fair / capacity / …） */
export async function getYarnSchedulerInfo(clusterId: number) {
  return request<DATASOPHON.ApiResponse<string>>(
    `/cluster/${clusterId}/yarn/queue/scheduler`,
    { method: 'GET' },
  );
}

/** YARN 队列列表（分页） */
export async function listYarnQueues(
  clusterId: number,
  params: { page?: number; pageSize?: number },
) {
  return request<DATASOPHON.ApiResponse<DATASOPHON.YarnQueuePageResponse>>(
    `/cluster/${clusterId}/yarn/queue/list`,
    { method: 'GET', params },
  );
}

/** 新建 YARN 队列 */
export async function saveYarnQueue(
  clusterId: number,
  body: DATASOPHON.SaveYarnQueueRequest,
) {
  return request<DATASOPHON.ApiResponse<void>>(
    `/cluster/${clusterId}/yarn/queue/save`,
    { method: 'POST', data: body },
  );
}

/** 修改 YARN 队列 */
export async function updateYarnQueue(
  clusterId: number,
  body: DATASOPHON.UpdateYarnQueueRequest,
) {
  return request<DATASOPHON.ApiResponse<void>>(
    `/cluster/${clusterId}/yarn/queue/update`,
    { method: 'POST', data: body },
  );
}

/** 删除 YARN 队列（批量） */
export async function deleteYarnQueues(clusterId: number, ids: number[]) {
  return request<DATASOPHON.ApiResponse<void>>(
    `/cluster/${clusterId}/yarn/queue/delete`,
    { method: 'POST', data: ids },
  );
}

/** 刷新队列到 YARN */
export async function refreshYarnQueues(clusterId: number) {
  return request<DATASOPHON.ApiResponse<void>>(
    `/cluster/${clusterId}/yarn/queue/refresh`,
    { method: 'POST' },
  );
}
