import { request } from '@umijs/max';

export function listClusters() {
  return request<{ data: DATASOPHON.ClusterResponse[] }>('/cluster/list', {
    method: 'GET',
  });
}

export function createCluster(body: DATASOPHON.CreateClusterRequest) {
  return request<{ data: DATASOPHON.ClusterResponse }>('/cluster', {
    method: 'POST',
    data: body,
  });
}

export function updateCluster(id: number, body: DATASOPHON.UpdateClusterRequest) {
  return request<{ data: void }>(`/cluster/${id}`, {
    method: 'PUT',
    data: body,
  });
}

export function deleteCluster(id: number) {
  return request<{ data: void }>(`/cluster/${id}`, {
    method: 'DELETE',
  });
}

export function saveManagers(id: number, userIds: number[]) {
  return request<{ data: void }>(`/cluster/${id}/managers`, {
    method: 'PUT',
    data: { userIds },
  });
}

export function getK8sConfig(clusterId: number) {
  return request<{ data: DATASOPHON.K8sConfig }>(
    `/cluster/k8sConfig/getConfigByClusterId/${clusterId}`,
    { method: 'GET' },
  );
}

export function testK8sConnection(body: DATASOPHON.K8sConfig) {
  return request<{ data: DATASOPHON.K8sTestResult }>('/cluster/k8sConfig/testConnection', {
    method: 'POST',
    data: body,
  });
}

export function saveK8sConfig(body: DATASOPHON.K8sConfig) {
  return request<DATASOPHON.ApiResponse<DATASOPHON.K8sConfig>>(
    '/cluster/k8sConfig/saveOrUpdateConfig',
    {
      method: 'POST',
      data: body,
    },
  );
}

export function listFrames() {
  return request<{ data: DATASOPHON.FrameResponse[] }>('/frame/list', {
    method: 'GET',
  });
}

export function listAllUsers() {
  return request<{ data: DATASOPHON.UserResponse[] }>('/user/list', {
    method: 'GET',
  });
}
