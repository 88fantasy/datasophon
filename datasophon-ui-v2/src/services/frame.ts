import { request } from '@umijs/max';

/** 获取所有框架及其下物理机 / K8s 服务列表（嵌套结构） */
export function listFrameServices() {
  return request<{ data: DATASOPHON.FrameWithServicesResponse[] }>('/frame/services', {
    method: 'GET',
  });
}

/** 删除物理机框架服务 */
export function deleteFrameService(id: number) {
  return request<{ data: void }>(`/frame/service/${id}`, {
    method: 'DELETE',
  });
}

/** 删除 K8s 框架服务 */
export function deleteFrameK8sService(id: number) {
  return request<{ data: void }>(`/frame/k8s-service/${id}`, {
    method: 'DELETE',
  });
}

/** 读取指定物理机服务的 service_ddl.json 内容 */
export function getFrameServiceDdl(id: number) {
  return request<{ data: string }>(`/frame/service/${id}/ddl`, {
    method: 'GET',
  });
}

/** 更新指定物理机服务的 service_ddl.json 内容 */
export function updateFrameServiceDdl(id: number, content: string) {
  return request<{ data: void }>(`/frame/service/${id}/ddl`, {
    method: 'PUT',
    data: { content },
  });
}
