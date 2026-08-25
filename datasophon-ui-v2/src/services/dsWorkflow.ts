import { request } from '@umijs/max';

export async function getDsProjects(clusterId: number) {
  return request<DATASOPHON.ApiResponse<DATASOPHON.DsPage<DATASOPHON.DsProject>>>(
    '/ds/projects',
    { method: 'GET', params: { clusterId }, skipErrorHandler: true },
  );
}
