import { request } from '@umijs/max';

export async function getDsProjects(clusterId: number) {
  return request<DATASOPHON.ApiResponse<DATASOPHON.DsPage<DATASOPHON.DsProject>>>(
    '/ds/projects',
    { method: 'GET', params: { clusterId }, skipErrorHandler: true },
  );
}

export async function getDsWorkflows(
  clusterId: number,
  projectCode: number,
  pageNo: number,
  pageSize: number,
  searchVal?: string,
) {
  return request<
    DATASOPHON.ApiResponse<DATASOPHON.DsPage<DATASOPHON.DsWorkflowDefinition>>
  >('/ds/workflows', {
    method: 'GET',
    params: { clusterId, projectCode, pageNo, pageSize, searchVal },
    skipErrorHandler: true,
  });
}

export async function getDsWorkflowInstances(
  clusterId: number,
  projectCode: number,
  workflowCode: number,
  limit = 10,
) {
  return request<
    DATASOPHON.ApiResponse<DATASOPHON.DsPage<DATASOPHON.DsWorkflowInstance>>
  >(`/ds/workflows/${workflowCode}/instances`, {
    method: 'GET',
    params: { clusterId, projectCode, limit },
    skipErrorHandler: true,
  });
}

export async function getDsDag(
  clusterId: number,
  projectCode: number,
  instanceId: number,
) {
  return request<DATASOPHON.ApiResponse<DATASOPHON.DsDag>>(
    `/ds/instances/${instanceId}/dag`,
    {
      method: 'GET',
      params: { clusterId, projectCode },
      skipErrorHandler: true,
    },
  );
}
