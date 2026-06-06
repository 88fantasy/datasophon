import { request } from '@umijs/max';

export function listClusters() {
  return request<{ data: DATASOPHON.ClusterInfo[] }>('/cluster/list', {
    method: 'GET',
  });
}

export function createCluster(body: Partial<DATASOPHON.ClusterInfo>) {
  return request<{ data: DATASOPHON.ClusterInfo }>('/cluster', {
    method: 'POST',
    data: body,
  });
}

export function updateCluster(id: number, body: Partial<DATASOPHON.ClusterInfo>) {
  return request<{ data: void }>(`/cluster/${id}`, {
    method: 'PUT',
    data: { ...body, id },
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

export function listFrames() {
  return request<{ data: DATASOPHON.FrameInfo[] }>('/frame/list', {
    method: 'GET',
  });
}

export function listAllUsers() {
  return request<{ data: DATASOPHON.UserInfo[] }>('/user/list', {
    method: 'GET',
  });
}
