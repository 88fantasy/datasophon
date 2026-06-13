import { request } from '@umijs/max';

/** 节点标签列表 */
export function listNodeLabels(clusterId: number) {
  return request<{ data: DATASOPHON.NodeLabel[] }>(
    `/cluster/${clusterId}/host/label/list`,
    { method: 'GET' },
  );
}

/** 新建标签 */
export function saveNodeLabel(clusterId: number, nodeLabel: string) {
  return request<{ data: void }>(`/cluster/${clusterId}/host/label`, {
    method: 'POST',
    params: { nodeLabel },
  });
}

/** 删除标签 */
export function deleteNodeLabel(clusterId: number, nodeLabelId: number) {
  return request<{ data: void }>(
    `/cluster/${clusterId}/host/label/${nodeLabelId}`,
    { method: 'DELETE' },
  );
}

/** 分配标签给主机 */
export function assignNodeLabel(
  clusterId: number,
  nodeLabelId: number,
  hostIds: number[],
) {
  return request<{ data: void }>(
    `/cluster/${clusterId}/host/label/assign`,
    { method: 'POST', data: { nodeLabelId, hostIds } },
  );
}
