import { request } from '@umijs/max';

/** 分页查询集群命令历史列表 */
export function listDagCommands(
  clusterId: number,
  params: { page?: number; pageSize?: number },
) {
  return request<DATASOPHON.ApiResponse<any>>(
    `/ddh/api/v2/cluster/${clusterId}/command/list`,
    {
      method: 'GET',
      params,
    },
  );
}

/** 获取 DAG 图数据（节点 + 边 + 状态），用于 x6 图渲染 */
export function getDagGraph(clusterId: number, dagId: string) {
  return request<DATASOPHON.ApiResponse<DATASOPHON.DagGraph>>(
    `/ddh/api/v2/cluster/${clusterId}/dag/${dagId}/graph`,
    {
      method: 'GET',
    },
  );
}

/** 重新运行指定 DAG */
export function redeployDag(clusterId: number, dagId: string) {
  return request<DATASOPHON.ApiResponse<null>>(
    `/ddh/api/v2/cluster/${clusterId}/dag/${dagId}/redeploy`,
    {
      method: 'POST',
    },
  );
}
